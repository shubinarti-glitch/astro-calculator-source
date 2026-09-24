package ru.astrosmap.app.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import ru.astrosmap.app.R
import ru.astrosmap.app.data.SyncManager
import ru.astrosmap.app.data.TokenStore
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.DeleteAccountRequest
import ru.astrosmap.app.data.api.LoginRequest
import ru.astrosmap.app.data.api.MeResponse
import ru.astrosmap.app.data.api.RegisterRequest
import ru.astrosmap.app.ui.AstroLabels
import java.io.IOException
import javax.inject.Inject

sealed interface AccountState {
    data object Loading : AccountState
    data object LoggedOut : AccountState
    data object Offline : AccountState // вход выполнен, но сервер недоступен
    data class LoggedIn(val me: MeResponse) : AccountState
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val api: AstroApi,
    private val tokenStore: TokenStore,
    private val syncManager: SyncManager,
    dao: ru.astrosmap.app.data.ChartDao,
) : ViewModel() {

    val chartsCount = dao.search("").map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    var state by mutableStateOf<AccountState>(AccountState.Loading)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Текст ошибки с сервера (detail) или null; ресурсные ошибки — errorRes. */
    var errorText by mutableStateOf<String?>(null)
    var errorRes by mutableStateOf<Int?>(null)

    init {
        viewModelScope.launch {
            if (tokenStore.get() == null) {
                state = AccountState.LoggedOut
            } else {
                refreshMe()
            }
        }
    }

    private suspend fun refreshMe() {
        state = try {
            api.me().also { ru.astrosmap.app.data.DailyNotify.setPremium(context, it.premium) }
                .let { AccountState.LoggedIn(it) }
        } catch (e: HttpException) {
            if (e.code() == 401) tokenStore.clear() // токен отозван на сервере
            AccountState.LoggedOut
        } catch (e: IOException) {
            AccountState.Offline
        } catch (e: Exception) {
            // Неожиданный формат ответа и т.п. — никогда не роняем приложение.
            AccountState.Offline
        }
    }

    fun retry() {
        state = AccountState.Loading
        viewModelScope.launch { refreshMe() }
    }

    fun login(username: String, password: String) = submit {
        val resp = api.login(LoginRequest(username.trim(), password, lang()))
        tokenStore.save(resp.token)
        refreshMe()
        syncManager.sync()
    }

    fun register(username: String, email: String, password: String,
                 privacyAccepted: Boolean, termsAccepted: Boolean) = submit {
        if (!privacyAccepted || !termsAccepted) {
            errorText = if (lang() == "ru") "Примите политику и пользовательское соглашение." else "Accept the privacy policy and terms."
            return@submit
        }
        val resp = api.register(RegisterRequest(username.trim(), password, email.trim(), lang(),
            privacyAccepted, termsAccepted, "2026-09-01", "2026-09-01", "android"))
        tokenStore.save(resp.token)
        refreshMe()
        syncManager.sync()
    }

    var recoverySent by mutableStateOf(false)
        private set

    fun resetRecovery() {
        recoverySent = false
        errorText = null
        errorRes = null
    }

    fun recoverPassword(email: String) = submit {
        api.forgotPassword(ru.astrosmap.app.data.api.ForgotPasswordRequest(email.trim(), lang()))
        recoverySent = true
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() } // сеть могла пропасть — локальный выход всё равно делаем
            tokenStore.clear()
            ru.astrosmap.app.data.DailyNotify.setPremium(context, false)
            state = AccountState.LoggedOut
        }
    }

    fun deleteAccount(password: String) = submit {
        api.deleteAccount(DeleteAccountRequest(password))
        tokenStore.clear()
        ru.astrosmap.app.data.DailyNotify.setPremium(context, false)
        state = AccountState.LoggedOut
    }

    private fun submit(block: suspend () -> Unit) {
        if (busy) return
        errorText = null
        errorRes = null
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: HttpException) {
                errorText = parseDetail(e)
            } catch (e: IOException) {
                errorRes = R.string.net_error
            } catch (e: Exception) {
                errorRes = R.string.net_error // формат ответа и прочее — не краш, а сообщение
            } finally {
                busy = false
            }
        }
    }

    private fun parseDetail(e: HttpException): String {
        val body = e.response()?.errorBody()?.string().orEmpty()
        // FastAPI отдаёт detail двумя формами: строкой (HTTPException) и списком
        // объектов с полем msg (422 от pydantic). Раньше читали только строку —
        // на 422 декод падал и пользователь видел голое «HTTP 422».
        return runCatching {
            when (val detail = Json.parseToJsonElement(body).jsonObject["detail"]) {
                is JsonPrimitive -> detail.content
                is JsonArray -> detail.joinToString("; ") {
                    val field = (it.jsonObject["loc"] as? JsonArray)?.lastOrNull()?.jsonPrimitive?.content
                    when (field) {
                        "email" -> if (lang() == "ru") "Проверьте адрес электронной почты." else "Check your email address."
                        "username" -> if (lang() == "ru") "Имя пользователя: от 2 до 50 символов." else "Username: 2 to 50 characters."
                        "password" -> if (lang() == "ru") "Пароль: от 8 до 200 символов." else "Password: 8 to 200 characters."
                        "privacy_accepted", "terms_accepted", "privacy_version", "terms_version", "consent_source" ->
                            if (lang() == "ru") "Не удалось передать согласия. Обновите приложение и повторите регистрацию." else "Could not submit consent. Update the app and try again."
                        else -> if (lang() == "ru") "Проверьте заполненные поля." else "Check the entered details."
                    }
                }.split("; ").distinct().joinToString("; ")
                else -> null
            }
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "HTTP ${e.code()}"
    }

    private fun lang() = if (AstroLabels.isRu()) "ru" else "en"
}
