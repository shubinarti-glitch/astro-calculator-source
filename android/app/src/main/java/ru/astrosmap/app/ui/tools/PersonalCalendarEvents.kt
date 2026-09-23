package ru.astrosmap.app.ui.tools

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.math.floor

enum class CalendarEventType {
    LUNAR_PHASE,
    MOON_SIGN,
    RETROGRADE_START,
    RETROGRADE_END,
    SOLAR_RETURN,
    LUNAR_RETURN,
    PERSONAL_TRANSIT,
}

data class CalendarDaySnapshot(
    val date: LocalDate,
    val lunarPhaseKey: String,
    val lunarAgeDegrees: Double,
    val moonSign: String,
    val sunLongitude: Double = 0.0,
    val moonLongitude: Double = 0.0,
    val retrograde: Map<String, Boolean>,
)

data class PersonalCalendarEvent(
    val date: LocalDate,
    val type: CalendarEventType,
    val subject: String,
    val target: String? = null,
    val aspect: String? = null,
)

data class PersonalCalendarDay(
    val date: LocalDate,
    val lunarPhaseKey: String,
    val lunarDay: Int,
    val moonSign: String,
    val events: List<PersonalCalendarEvent>,
)

/** Builds stable calendar facts from daily ephemeris snapshots. */
object PersonalCalendarEventCalculator {
    private const val SYNODIC_MONTH_DAYS = 29.530588

    fun buildMonth(
        month: YearMonth,
        snapshots: List<CalendarDaySnapshot>,
        birthMonth: Int? = null,
        birthDay: Int? = null,
        natalSunLongitude: Double? = null,
        natalMoonLongitude: Double? = null,
    ): List<PersonalCalendarDay> {
        val ordered = snapshots.distinctBy { it.date }.sortedBy { it.date }
        val byDate = ordered.associateBy { it.date }

        return (1..month.lengthOfMonth()).mapNotNull { dayNumber ->
            val date = month.atDay(dayNumber)
            val current = byDate[date] ?: return@mapNotNull null
            val previous = byDate[date.minusDays(1)]
            val next = byDate[date.plusDays(1)]
            val events = buildList {
                if (previous != null && previous.lunarPhaseKey != current.lunarPhaseKey) {
                    add(PersonalCalendarEvent(date, CalendarEventType.LUNAR_PHASE, current.lunarPhaseKey))
                }
                if (previous != null && previous.moonSign != current.moonSign) {
                    add(PersonalCalendarEvent(date, CalendarEventType.MOON_SIGN, current.moonSign))
                }

                val planets = (previous?.retrograde.orEmpty().keys + current.retrograde.keys).sorted()
                planets.forEach { planet ->
                    val wasRetrograde = previous?.retrograde?.get(planet) ?: current.retrograde.getValue(planet)
                    val isRetrograde = current.retrograde[planet] ?: false
                    if (wasRetrograde != isRetrograde) {
                        add(
                            PersonalCalendarEvent(
                                date = date,
                                type = if (isRetrograde) {
                                    CalendarEventType.RETROGRADE_START
                                } else {
                                    CalendarEventType.RETROGRADE_END
                                },
                                subject = planet,
                            ),
                        )
                    }
                }

                if (natalSunLongitude != null && isClosestReturn(
                        current.sunLongitude, previous?.sunLongitude, next?.sunLongitude,
                        natalSunLongitude, 1.5,
                    )
                ) {
                    add(PersonalCalendarEvent(date, CalendarEventType.SOLAR_RETURN, "Sun"))
                } else if (natalSunLongitude == null && birthMonth == date.monthValue && birthDay == date.dayOfMonth) {
                    add(PersonalCalendarEvent(date, CalendarEventType.SOLAR_RETURN, "Sun"))
                }
                if (natalMoonLongitude != null && isClosestReturn(
                        current.moonLongitude, previous?.moonLongitude, next?.moonLongitude,
                        natalMoonLongitude, 7.5,
                    )
                ) {
                    add(PersonalCalendarEvent(date, CalendarEventType.LUNAR_RETURN, "Moon"))
                }
            }

            PersonalCalendarDay(
                date = date,
                lunarPhaseKey = current.lunarPhaseKey,
                lunarDay = lunarDay(current.lunarAgeDegrees),
                moonSign = current.moonSign,
                events = events,
            )
        }
    }

    internal fun lunarDay(ageDegrees: Double): Int {
        val normalized = ((ageDegrees % 360.0) + 360.0) % 360.0
        return (floor(normalized / 360.0 * SYNODIC_MONTH_DAYS).toInt() + 1).coerceIn(1, 30)
    }

    fun canShowPersonalTransit(date: LocalDate, today: LocalDate, fullCalendar: Boolean): Boolean {
        if (fullCalendar) return true
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)
        return !date.isBefore(weekStart) && !date.isAfter(weekEnd)
    }

    private fun isClosestReturn(
        current: Double,
        previous: Double?,
        next: Double?,
        natal: Double,
        threshold: Double,
    ): Boolean {
        val distance = angularDistance(current, natal)
        return distance <= threshold &&
            (previous == null || distance <= angularDistance(previous, natal)) &&
            (next == null || distance < angularDistance(next, natal))
    }

    private fun angularDistance(first: Double, second: Double): Double {
        val raw = kotlin.math.abs((first - second) % 360.0)
        return minOf(raw, 360.0 - raw)
    }
}
