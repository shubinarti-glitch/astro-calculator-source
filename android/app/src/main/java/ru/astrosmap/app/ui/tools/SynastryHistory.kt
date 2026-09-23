package ru.astrosmap.app.ui.tools

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

enum class RelationshipType(val showsAttraction: Boolean, val showsRawInterpretations: Boolean) {
    PARTNERS(true, true), FAMILY(false, false), FRIENDS(false, false), PARENT_CHILD(false, false)
}

@Serializable
data class SynastrySnapshot(
    val idA: Long,
    val idB: Long,
    val relationship: String,
    val title: String,
    val createdAt: Long,
    val payload: String,
)

object SynastryHistory {
    private const val PREFS = "synastry_history_v1"
    private const val KEY = "items"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, record: SynastrySnapshot) {
        val items = (list(context).filterNot {
            it.idA == record.idA && it.idB == record.idB && it.relationship == record.relationship
        } + record).sortedByDescending { it.createdAt }.take(20)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(items)).apply()
    }

    fun list(context: Context): List<SynastrySnapshot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SynastrySnapshot>>(raw) }.getOrDefault(emptyList())
    }

    fun payload(record: SynastrySnapshot): JsonObject? =
        runCatching { json.parseToJsonElement(record.payload) as JsonObject }.getOrNull()
}
