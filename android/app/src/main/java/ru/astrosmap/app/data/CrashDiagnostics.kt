package ru.astrosmap.app.data

import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.*
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.MobileReportRequest
import java.util.UUID
import kotlin.system.exitProcess

/** One bounded pending report, excluded from backup. No network in crash handler. */
object CrashDiagnostics {
    private lateinit var store: PendingCrashStore
    private val lock = Any()
    @Volatile private var enabled = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isEnabled() = enabled

    fun setEnabled(value: Boolean): Boolean = synchronized(lock) {
        try {
            if (!value) enabled = false
            store.setEnabled(value)
            enabled = value
            true
        } catch (_: Exception) { false }
    }

    fun install(context: Context, api: AstroApi) {
        store = PendingCrashStore(context.noBackupFilesDir)
        enabled = store.enabled()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                synchronized(lock) {
                    if (enabled) {
                        val report = MobileReportRequest(UUID.randomUUID().toString(), CrashSummary.format(error),
                            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.STORE_ID,
                            Build.VERSION.SDK_INT, true)
                        store.save(report)
                    }
                }
            } catch (_: Throwable) {
                // Diagnostics must never replace the original termination path, including OOM.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else { Process.killProcess(Process.myPid()); exitProcess(10) }
            }
        }
        scope.launch {
            try {
                val report = synchronized(lock) {
                    store.read() ?: return@launch
                }
                if (!enabled) return@launch
                val receipt = api.sendReport(report)
                synchronized(lock) {
                    if (receipt.id == report.id) store.acknowledge(report.id)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Offline/server unavailable: keep one report until next launch. */ }
        }
    }
}
