package ru.astrosmap.app.ui.journal

import ru.astrosmap.app.data.JournalEntry
enum class JournalMetric { MOOD, ENERGY, RELATIONSHIPS, WORK, WELLBEING }

data class JournalMetricAverage(val metric: JournalMetric, val value: Double)

data class JournalAnalytics(
    val averages: List<JournalMetricAverage>,
    val strongest: JournalMetricAverage,
    val sensitive: JournalMetricAverage,
    val improving: JournalMetric?,
    val sampleSize: Int,
)

data class TransitJournalMatch(
    val matched: Int,
    val compared: Int,
) {
    val percent: Int get() = if (compared == 0) 0 else (matched * 100.0 / compared).toInt()
}

/** Pure calculations kept separate from Compose and the local database. */
object JournalAnalyticsCalculator {
    const val MIN_SAMPLE = 5

    fun calculate(entries: List<JournalEntry>): JournalAnalytics? {
        val ordered = entries.sortedBy { it.epochDay }
        if (ordered.size < MIN_SAMPLE) return null
        val averages = JournalMetric.entries.map { metric ->
            JournalMetricAverage(metric, ordered.map { it.value(metric) }.average())
        }
        val split = ordered.size / 2
        val earlier = ordered.take(split)
        val later = ordered.drop(split)
        val improving = JournalMetric.entries
            .map { metric -> metric to later.map { it.value(metric) }.average() - earlier.map { it.value(metric) }.average() }
            .filter { it.second >= 0.35 }
            .maxByOrNull { it.second }
            ?.first
        return JournalAnalytics(
            averages = averages,
            strongest = averages.maxBy { it.value },
            sensitive = averages.minBy { it.value },
            improving = improving,
            sampleSize = ordered.size,
        )
    }

    /**
     * Counts directional agreement between recorded values and local transit indicators.
     * Neutral values (3 / forecast 40..60) do not affect the result.
     */
    fun transitMatch(
        entries: List<JournalEntry>,
        predicted: Map<Long, Map<JournalMetric, Int>>,
    ): TransitJournalMatch {
        var matched = 0
        var compared = 0
        entries.forEach { entry ->
            predicted[entry.epochDay]?.forEach { (metric, forecast) ->
                val actualDirection = entry.value(metric).compareTo(3)
                val forecastDirection = when {
                    forecast > 60 -> 1
                    forecast < 40 -> -1
                    else -> 0
                }
                if (actualDirection != 0 && forecastDirection != 0) {
                    compared++
                    if (actualDirection == forecastDirection) matched++
                }
            }
        }
        return TransitJournalMatch(matched, compared)
    }
}

fun JournalEntry.value(metric: JournalMetric): Int = when (metric) {
    JournalMetric.MOOD -> mood
    JournalMetric.ENERGY -> energy
    JournalMetric.RELATIONSHIPS -> relationships
    JournalMetric.WORK -> work
    JournalMetric.WELLBEING -> wellbeing
}
