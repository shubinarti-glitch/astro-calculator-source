package ru.astrosmap.app.ui.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecastHistoryTest {
    @Test fun periodRangesAreStable() {
        val now = LocalDate.of(2026, 8, 26)
        assertEquals(LocalDate.of(2026, 8, 24), ForecastPeriod.THIS_WEEK.range(now).first)
        assertEquals(LocalDate.of(2026, 9, 1), ForecastPeriod.NEXT_MONTH.range(now).first)
    }

    @Test fun comparisonFindsChangedEventsAndTone() {
        val old = Json.parseToJsonElement("""{"events":[{"p1":"Sun","aspect":"Square","p2":"Moon","date":"2026-08-26"}],"sphere_forecast":[{"tone":"neutral"}],"summary":"old"}""") as JsonObject
        val current = Json.parseToJsonElement("""{"events":[{"p1":"Venus","aspect":"Trine","p2":"Mars","date":"2026-08-27"}],"sphere_forecast":[{"tone":"favorable"}],"summary":"new"}""") as JsonObject
        val result = ForecastHistory.compare(old, current)
        assertEquals(1, result.added)
        assertEquals(1, result.ended)
        assertEquals(1, result.favorableDelta)
        assertTrue(result.summaryChanged)
    }
}
