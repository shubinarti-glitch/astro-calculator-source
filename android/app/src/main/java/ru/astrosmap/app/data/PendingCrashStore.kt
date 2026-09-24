package ru.astrosmap.app.data

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.astrosmap.app.data.api.MobileReportRequest

/** Caller serializes access. Directory must be app-private and excluded from backup. */
class PendingCrashStore(directory: File, private val now: () -> Long = System::currentTimeMillis) {
    private val consent = File(directory, "crash-consent-v1")
    private val pending = File(directory, "crash-pending-v1.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun enabled(): Boolean = try {
        consent.length() == 3L && consent.readText() == "yes"
    } catch (_: Exception) { false }

    fun setEnabled(value: Boolean) {
        // Write a denial before deleting pending data, so a failed deletion cannot re-enable upload.
        consent.writeText(if (value) "yes" else "no")
        if (!value) clear()
    }

    fun clear() {
        if (pending.exists() && !pending.delete()) throw java.io.IOException("Cannot clear pending diagnostics")
    }

    fun save(report: MobileReportRequest) {
        if (!enabled()) return
        val encoded = json.encodeToString(report)
        require(encoded.toByteArray(Charsets.UTF_8).size <= 16384)
        pending.writeText(encoded)
    }

    fun read(): MobileReportRequest? {
        if (!enabled()) { clear(); return null }
        if (!pending.exists()) return null
        if (pending.length() > 16384 || now() - pending.lastModified() > 7 * 86400000L) {
            clear()
            return null
        }
        return try { json.decodeFromString<MobileReportRequest>(pending.readText()) }
        catch (_: kotlinx.serialization.SerializationException) { clear(); null }
        catch (_: IllegalArgumentException) { clear(); null }
    }

    fun acknowledge(id: String) {
        if (read()?.id == id) clear()
    }
}
