package ru.astrosmap.app.ui.tools

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Persistent cache for expensive offline ephemeris calculations. */
object PersonalCalendarCache {
    private const val PREFS = "personal_calendar_cache_v2"
    private const val MAX_MONTHS = 12
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CachedEvent(
        val date: String,
        val type: String,
        val subject: String,
        val target: String? = null,
        val aspect: String? = null,
    )

    @Serializable
    private data class CachedDay(
        val day: Int,
        val phaseKey: String,
        val sign: String,
        val lunarDay: Int,
        val events: List<CachedEvent>,
    )

    @Serializable
    private data class CachedMonth(val savedAt: Long, val days: List<CachedDay>)

    fun read(context: Context, key: String): List<LunarDay>? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
            ?: return null
        json.decodeFromString<CachedMonth>(raw).days.map { day ->
            LunarDay(
                day = day.day,
                phaseKey = day.phaseKey,
                sign = day.sign,
                lunarDay = day.lunarDay,
                events = day.events.map { event ->
                    PersonalCalendarEvent(
                        date = LocalDate.parse(event.date),
                        type = CalendarEventType.valueOf(event.type),
                        subject = event.subject,
                        target = event.target,
                        aspect = event.aspect,
                    )
                },
            )
        }
    }.getOrNull()

    fun write(context: Context, key: String, days: List<LunarDay>) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cached = CachedMonth(
                savedAt = System.currentTimeMillis(),
                days = days.map { day ->
                    CachedDay(
                        day = day.day,
                        phaseKey = day.phaseKey,
                        sign = day.sign,
                        lunarDay = day.lunarDay,
                        events = day.events.map { event ->
                            CachedEvent(
                                date = event.date.toString(),
                                type = event.type.name,
                                subject = event.subject,
                                target = event.target,
                                aspect = event.aspect,
                            )
                        },
                    )
                },
            )
            val editor = prefs.edit().putString(key, json.encodeToString(cached))
            prefs.all.mapNotNull { (storedKey, raw) ->
                runCatching { storedKey to json.decodeFromString<CachedMonth>(raw as String).savedAt }.getOrNull()
            }.sortedByDescending { it.second }.drop(MAX_MONTHS).forEach { editor.remove(it.first) }
            editor.apply()
        }
    }
}
