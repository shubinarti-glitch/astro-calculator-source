package ru.astrosmap.app.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.astrosmap.app.data.JournalEntry

class JournalAnalyticsTest {
    private fun entry(day: Long, mood: Int, energy: Int, relationships: Int, work: Int, wellbeing: Int) =
        JournalEntry("owner", day, mood, energy, relationships, work, wellbeing, "", "", "", "")

    @Test
    fun `requires five observations`() {
        assertNull(JournalAnalyticsCalculator.calculate(List(4) { entry(it.toLong(), 3, 3, 3, 3, 3) }))
    }

    @Test
    fun `finds strongest sensitive and improving metric`() {
        val result = JournalAnalyticsCalculator.calculate(
            listOf(
                entry(1, 2, 2, 4, 3, 3), entry(2, 2, 2, 5, 3, 3),
                entry(3, 3, 4, 5, 3, 3), entry(4, 3, 5, 5, 3, 3), entry(5, 3, 5, 4, 3, 3),
            ),
        )!!
        assertEquals(JournalMetric.RELATIONSHIPS, result.strongest.metric)
        assertEquals(JournalMetric.MOOD, result.sensitive.metric)
        assertEquals(JournalMetric.ENERGY, result.improving)
    }

    @Test
    fun `transit match ignores neutral observations`() {
        val rows = listOf(entry(1, 5, 1, 3, 3, 3))
        val match = JournalAnalyticsCalculator.transitMatch(
            rows,
            mapOf(1L to mapOf(JournalMetric.MOOD to 80, JournalMetric.ENERGY to 75, JournalMetric.WORK to 50)),
        )
        assertEquals(2, match.compared)
        assertEquals(1, match.matched)
        assertEquals(50, match.percent)
    }
}
