package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import retrofit2.HttpException
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.openSite

/** Состояние любого серверного отчёта (прогрессии, соляр, синастрия …). */
sealed interface ReportState {
    data object Loading : ReportState
    data object NeedPremium : ReportState
    data object NeedAuth : ReportState
    data object Offline : ReportState
    data class Ready(val data: JsonObject) : ReportState
}

/** Единая обработка ошибок серверных отчётов: 402 — подписка, 401 — вход, прочее — офлайн. */
suspend fun loadReport(block: suspend () -> JsonObject): ReportState = try {
    ReportState.Ready(block())
} catch (e: HttpException) {
    when (e.code()) {
        402 -> ReportState.NeedPremium
        401 -> ReportState.NeedAuth
        else -> ReportState.Offline
    }
} catch (e: Exception) {
    ReportState.Offline
}

/** Каркас экрана отчёта: гейты подписки/входа/сети, контент — только для Ready. */
@Composable
fun ReportScaffold(
    state: ReportState,
    onRetry: () -> Unit,
    content: @Composable (JsonObject) -> Unit,
) {
    when (state) {
        ReportState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ReportState.NeedPremium -> CenteredNote(stringResource(R.string.premium_required)) {
            // В googleplay-сборке без кнопки покупки — только пояснение (текст нейтральный, см. флейвор).
            if (ru.astrosmap.app.BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) {
                val context = LocalContext.current
                Button(onClick = { openSite(context) }) { Text(stringResource(R.string.premium_buy)) }
            }
        }
        ReportState.NeedAuth -> CenteredNote(stringResource(R.string.solar_need_auth)) {}
        ReportState.Offline -> CenteredNote(stringResource(R.string.net_error)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        is ReportState.Ready -> content(state.data)
    }
}

@Composable
private fun CenteredNote(text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        action()
    }
}

// ------------------------------------------------------------------ #
//  Мини-хелперы чтения JsonObject (ответы бэкенда, поля *_ru уже локализованы)
// ------------------------------------------------------------------ #

fun JsonObject.s(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.i(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/** Булево поле: сервер шлёт true/false как JSON-boolean (не строку). */
fun JsonObject.b(key: String): Boolean = (this[key] as? JsonPrimitive)?.content == "true"

fun JsonObject.d(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

fun JsonObject.o(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.a(key: String): List<JsonObject> =
    (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
