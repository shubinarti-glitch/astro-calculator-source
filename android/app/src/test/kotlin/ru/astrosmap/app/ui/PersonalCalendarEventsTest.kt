package ru.astrosmap.app.ui.tools

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalCalendarEventsTest {
    private fun snapshot(
        date: LocalDate,
        phase: String = "WAXING_CRESCENT",
        age: Double = 20.0,
        sign: String = "Aries",
        mercuryRetrograde: Boolean = false,
    ) = CalendarDaySnapshot(
        date = date,
        lunarPhaseKey = phase,
        lunarAgeDegrees = age,
        moonSign = sign,
        retrograde = mapOf("Mercury" to mercuryRetrograde),
    )

    @Test
    fun `lunar day is derived from normalized lunar age`() {
        assertEquals(1, PersonalCalendarEventCalculator.lunarDay(0.0))
        assertEquals(15, PersonalCalendarEventCalculator.lunarDay(180.0))
        assertEquals(30, PersonalCalendarEventCalculator.lunarDay(359.9))
        assertEquals(30, PersonalCalendarEventCalculator.lunarDay(-0.1))
    }

    @Test
    fun `month detects phase sign and retrograde transitions`() {
        val month = YearMonth.of(2026, 8)
        val days = PersonalCalendarEventCalculator.buildMonth(
            month = month,
            snapshots = listOf(
                snapshot(LocalDate.of(2026, 7, 31)),
                snapshot(LocalDate.of(2026, 8, 1)),
                snapshot(
                    date = LocalDate.of(2026, 8, 2),
                    phase = "FIRST_QUARTER",
                    sign = "Taurus",
                    mercuryRetrograde = true,
                ),
            ),
        )

        val types = days.single { it.date.dayOfMonth == 2 }.events.map { it.type }.toSet()
        assertEquals(
            setOf(
                CalendarEventType.LUNAR_PHASE,
                CalendarEventType.MOON_SIGN,
                CalendarEventType.RETROGRADE_START,
            ),
            types,
        )
    }

    @Test
    fun `birthday adds solar return marker`() {
        val date = LocalDate.of(2026, 8, 20)
        val days = PersonalCalendarEventCalculator.buildMonth(
            month = YearMonth.from(date),
            snapshots = listOf(snapshot(date)),
            birthMonth = 8,
            birthDay = 20,
        )

        assertTrue(days.single().events.any { it.type == CalendarEventType.SOLAR_RETURN })
    }

    @Test
    fun `month detects closest solar and lunar returns`() {
        val month = YearMonth.of(2026, 8)
        fun positioned(date: LocalDate, sun: Double, moon: Double) = CalendarDaySnapshot(
            date = date,
            lunarPhaseKey = "WAXING_CRESCENT",
            lunarAgeDegrees = 20.0,
            moonSign = "Aries",
            sunLongitude = sun,
            moonLongitude = moon,
            retrograde = emptyMap(),
        )
        val days = PersonalCalendarEventCalculator.buildMonth(
            month = month,
            snapshots = listOf(
                positioned(LocalDate.of(2026, 7, 31), 98.0, 80.0),
                positioned(LocalDate.of(2026, 8, 1), 99.2, 92.0),
                positioned(LocalDate.of(2026, 8, 2), 100.1, 100.5),
                positioned(LocalDate.of(2026, 8, 3), 101.2, 112.0),
            ),
            natalSunLongitude = 100.0,
            natalMoonLongitude = 100.0,
        )

        val types = days.single { it.date.dayOfMonth == 2 }.events.map { it.type }.toSet()
        assertTrue(CalendarEventType.SOLAR_RETURN in types)
        assertTrue(CalendarEventType.LUNAR_RETURN in types)
    }

    @Test
    fun `free calendar shows personal transits only in current week`() {
        val today = LocalDate.of(2026, 8, 20)
        assertTrue(
            PersonalCalendarEventCalculator.canShowPersonalTransit(
                LocalDate.of(2026, 8, 17), today, fullCalendar = false,
            ),
        )
        assertEquals(
            false,
            PersonalCalendarEventCalculator.canShowPersonalTransit(
                LocalDate.of(2026, 8, 24), today, fullCalendar = false,
            ),
        )
        assertTrue(
            PersonalCalendarEventCalculator.canShowPersonalTransit(
                LocalDate.of(2027, 1, 1), today, fullCalendar = true,
            ),
        )
    }
}
