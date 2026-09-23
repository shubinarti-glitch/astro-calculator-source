package ru.astrosmap.app.ui.today

import ru.astrosmap.app.astro.AspectHit
import java.time.LocalDate
import kotlin.math.abs

data class UpcomingDayEvent(val date: LocalDate, val hit: AspectHit)

/** Pure selection rules used by the Today screen and covered by unit tests. */
object DayForecastCalculator {
    private val importantTransitPlanets = setOf(
        "Sun", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto",
    )
    private val importantNatalPoints = setOf(
        "Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn",
        "Ascendant", "Medium_Coeli",
    )
    private val importantAspects = setOf("conjunction", "square", "trine", "opposition")

    fun nearestImportant(days: List<Pair<LocalDate, List<AspectHit>>>): UpcomingDayEvent? = days
        .sortedBy { it.first }
        .firstNotNullOfOrNull { (date, aspects) ->
            aspects.asSequence()
                .filter { it.p2 in importantTransitPlanets }
                .filter { it.p1 in importantNatalPoints }
                .filter { it.aspect in importantAspects }
                .filter { abs(it.orbit) <= 1.5 }
                .minByOrNull { abs(it.orbit) }
                ?.let { UpcomingDayEvent(date, it) }
        }
}
