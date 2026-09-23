package ru.astrosmap.app.ui.today

import ru.astrosmap.app.astro.AspectHit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

enum class DayDomain {
    ENERGY,
    EMOTIONS,
    RELATIONSHIPS,
    COMMUNICATION,
    PRODUCTIVITY,
}

data class DayIndicator(
    val domain: DayDomain,
    val score: Int,
    val contributors: List<AspectHit>,
)

data class DayInsights(
    val indicators: List<DayIndicator>,
    val strongest: DayIndicator,
    val mostSensitive: DayIndicator,
)

/** Прозрачные индексы дня: каждую оценку можно раскрыть до конкретных аспектов. */
object DayInsightCalculator {
    private val planetWeight = mapOf(
        "Sun" to 1.0, "Moon" to 0.6, "Mercury" to 0.8, "Venus" to 0.8,
        "Mars" to 0.9, "Jupiter" to 1.0, "Saturn" to 1.0,
        "Uranus" to 0.7, "Neptune" to 0.7, "Pluto" to 0.7,
    )

    private val domainPlanets = mapOf(
        DayDomain.ENERGY to setOf("Sun", "Mars"),
        DayDomain.EMOTIONS to setOf("Moon", "Neptune"),
        DayDomain.RELATIONSHIPS to setOf("Venus", "Moon"),
        DayDomain.COMMUNICATION to setOf("Mercury"),
        DayDomain.PRODUCTIVITY to setOf("Saturn", "Mercury", "Mars"),
    )

    fun calculate(aspects: List<AspectHit>): DayInsights {
        val indicators = DayDomain.entries.map { domain ->
            val relevant = aspects.filter { hit ->
                val sources = domainPlanets.getValue(domain)
                hit.p2 in sources || hit.p1 in sources
            }
            val sum = relevant.sumOf(::contribution)
            val score = (50.0 + 50.0 * tanh(sum / 1.5)).roundToInt().coerceIn(0, 100)
            DayIndicator(
                domain = domain,
                score = score,
                contributors = relevant.sortedByDescending { abs(contribution(it)) }.take(3),
            )
        }
        return DayInsights(
            indicators = indicators,
            strongest = indicators.maxBy { it.score },
            mostSensitive = indicators.minBy { it.score },
        )
    }

    internal fun contribution(hit: AspectHit): Double {
        val planet = planetWeight[hit.p2] ?: planetWeight[hit.p1] ?: 0.5
        val polarity = when (hit.aspect) {
            "trine" -> 1.0
            "sextile" -> 0.7
            "square" -> -0.9
            "opposition" -> -1.0
            "conjunction" -> conjunctionPolarity(hit.p2)
            else -> 0.0
        }
        val tightness = (1.0 - abs(hit.orbit) / 10.0).coerceIn(0.0, 1.0)
        // Локальный dual-chart движок пока возвращает Static. Не выдаём это за
        // расходящийся аспект: коэффициент движения применяем только когда он известен.
        val movement = when (hit.movement) {
            "Applying" -> 1.15
            "Separating" -> 0.9
            else -> 1.0
        }
        return planet * polarity * tightness * movement
    }

    private fun conjunctionPolarity(transitPlanet: String): Double = when (transitPlanet) {
        "Saturn", "Mars", "Uranus", "Neptune", "Pluto" -> -1.0
        else -> 1.0
    }
}
