package ru.astrosmap.app.ui.account

import android.os.Build
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.MobileReportRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(private val api: AstroApi) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var receipt by mutableStateOf<String?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var pending: MobileReportRequest? = null

    fun reset() { if (!busy) { receipt = null; failed = false; pending = null } }

    fun send(description: String, consent: Boolean) {
        if (busy || !consent || description.trim().length !in 5..2000) return
        val request = pending?.takeIf { it.description == description.trim() }
            ?: MobileReportRequest(UUID.randomUUID().toString(), description.trim(),
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.STORE_ID,
                Build.VERSION.SDK_INT, true)
        pending = request
        busy = true
        failed = false
        viewModelScope.launch {
            try { receipt = api.sendReport(request).id }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true }
            finally { busy = false }
        }
    }
}
