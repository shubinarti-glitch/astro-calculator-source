package ru.astrosmap.app.ui.tools

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.LocalDate

enum class ForecastPeriod {
    TODAY, TOMORROW, THIS_WEEK, NEXT_WEEK, THIS_MONTH, NEXT_MONTH;

    fun range(now: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (this) {
        TODAY -> now to now
        TOMORROW -> now.plusDays(1) to now.plusDays(1)
        THIS_WEEK -> now.with(DayOfWeek.MONDAY) to now.with(DayOfWeek.SUNDAY)
        NEXT_WEEK -> now.with(DayOfWeek.MONDAY).plusWeeks(1) to now.with(DayOfWeek.SUNDAY).plusWeeks(1)
        THIS_MONTH -> now.withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
        NEXT_MONTH -> now.plusMonths(1).withDayOfMonth(1) to
            now.plusMonths(1).withDayOfMonth(now.plusMonths(1).lengthOfMonth())
    }
}

@Serializable
data class ForecastSnapshot(
    val chartId: Long,
    val period: String,
    val start: String,
    val end: String,
    val createdAt: Long,
    val payload: String,
)

data class ForecastComparison(
    val added: Int,
    val ended: Int,
    val continued: Int,
    val favorableDelta: Int,
    val summaryChanged: Boolean,
)

object ForecastHistory {
    private const val PREFS = "forecast_history_v1"
    private const val KEY = "items"
    private const val MAX_ITEMS = 30
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, chartId: Long, period: ForecastPeriod, range: Pair<LocalDate, LocalDate>, data: JsonObject) {
        val snapshot = ForecastSnapshot(chartId, period.name, range.first.toString(), range.second.toString(), System.currentTimeMillis(), data.toString())
        val updated = (list(context).filterNot {
            it.chartId == chartId && it.period == period.name && it.start == snapshot.start && it.end == snapshot.end
        } + snapshot).sortedByDescending { it.createdAt }.take(MAX_ITEMS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.encodeToString(updated)).apply()
    }

    fun list(context: Context, chartId: Long? = null): List<ForecastSnapshot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val all = runCatching { json.decodeFromString<List<ForecastSnapshot>>(raw) }.getOrDefault(emptyList())
        return if (chartId == null) all else all.filter { it.chartId == chartId }
    }

    fun payload(snapshot: ForecastSnapshot): JsonObject? =
        runCatching { json.parseToJsonElement(snapshot.payload) as JsonObject }.getOrNull()

    fun compare(previous: JsonObject, current: JsonObject): ForecastComparison {
        fun eventKeys(data: JsonObject) = data.a("events").map {
            listOf(it.s("p1"), it.s("aspect"), it.s("p2"), it.s("date")).joinToString("|")
        }.toSet()
        fun favorable(data: JsonObject) = data.a("sphere_forecast").count { it.s("tone") == "favorable" }
        val oldKeys = eventKeys(previous)
        val newKeys = eventKeys(current)
        return ForecastComparison(
            added = (newKeys - oldKeys).size,
            ended = (oldKeys - newKeys).size,
            continued = (newKeys intersect oldKeys).size,
            favorableDelta = favorable(current) - favorable(previous),
            summaryChanged = previous.s("summary") != current.s("summary"),
        )
    }
}
