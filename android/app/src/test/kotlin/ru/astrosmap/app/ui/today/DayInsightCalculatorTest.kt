package ru.astrosmap.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.astrosmap.app.astro.AspectHit
import java.time.LocalDate

class DayInsightCalculatorTest {
    @Test
    fun `harmonious Mars transit raises energy and productivity`() {
        val insights = DayInsightCalculator.calculate(
            listOf(AspectHit("Sun", "Mars", "trine", 0.2, 120, "Applying")),
        )

        assertTrue(insights.indicators.first { it.domain == DayDomain.ENERGY }.score > 50)
        assertTrue(insights.indicators.first { it.domain == DayDomain.PRODUCTIVITY }.score > 50)
    }

    @Test
    fun `tense Moon transit lowers emotions and relationships`() {
        val insights = DayInsightCalculator.calculate(
            listOf(AspectHit("Venus", "Moon", "opposition", 0.1, 180, "Applying")),
        )

        assertTrue(insights.indicators.first { it.domain == DayDomain.EMOTIONS }.score < 50)
        assertTrue(insights.indicators.first { it.domain == DayDomain.RELATIONSHIPS }.score < 50)
    }

    @Test
    fun `empty day stays neutral`() {
        val insights = DayInsightCalculator.calculate(emptyList())
        assertTrue(insights.indicators.all { it.score == 50 })
        assertEquals(5, insights.indicators.size)
    }

    @Test
    fun `nearest important event prefers the earlier qualifying day`() {
        val start = LocalDate.of(2026, 8, 20)
        val laterExact = AspectHit("Sun", "Saturn", "opposition", 0.1, 180, "Static")
        val earlier = AspectHit("Moon", "Jupiter", "trine", 1.0, 120, "Static")

        val result = DayForecastCalculator.nearestImportant(
            listOf(start.plusDays(5) to listOf(laterExact), start.plusDays(2) to listOf(earlier)),
        )

        assertEquals(start.plusDays(2), result?.date)
        assertEquals(earlier, result?.hit)
    }

    @Test
    fun `nearest important event ignores Moon quintiles and wide aspects`() {
        val date = LocalDate.of(2026, 8, 21)
        val moon = AspectHit("Sun", "Moon", "opposition", 0.1, 180, "Static")
        val quintile = AspectHit("Sun", "Saturn", "quintile", 0.1, 72, "Static")
        val wide = AspectHit("Sun", "Saturn", "square", 2.0, 90, "Static")
        val lilith = AspectHit("Mean_Lilith", "Mars", "trine", 0.1, 120, "Static")

        assertEquals(
            null,
            DayForecastCalculator.nearestImportant(listOf(date to listOf(moon, quintile, wide, lilith))),
        )
    }
}
