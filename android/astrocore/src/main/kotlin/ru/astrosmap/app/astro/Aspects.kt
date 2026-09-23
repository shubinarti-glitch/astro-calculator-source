package ru.astrosmap.app.astro

import kotlin.math.abs

/**
 * Порт логики аспектов Kerykeion (aspects_factory.py + aspects_utils.py)
 * с дефолтными настройками — ровно теми, что использует сайт.
 */

private data class AspectDef(val name: String, val degree: Int, val orb: Double)

// DEFAULT_ACTIVE_ASPECTS Kerykeion (порядок — как в его настройках; орбисы не перекрываются).
private val ACTIVE_ASPECTS = listOf(
    AspectDef("conjunction", 0, 10.0),
    AspectDef("sextile", 60, 6.0),
    AspectDef("quintile", 72, 1.0),
    AspectDef("square", 90, 5.0),
    AspectDef("trine", 120, 8.0),
    AspectDef("opposition", 180, 10.0),
)

// DEFAULT_ACTIVE_POINTS Kerykeion, отфильтрованные в порядке
// DEFAULT_CELESTIAL_POINTS_SETTINGS — он определяет, кто в паре p1, а кто p2.
private val ASPECT_POINT_ORDER = listOf(
    "Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn",
    "Uranus", "Neptune", "Pluto",
    "True_North_Lunar_Node", "Chiron",
    "Ascendant", "Medium_Coeli", "Descendant", "Imum_Coeli",
    "Mean_Lilith", "True_South_Lunar_Node",
)

private val AXES = setOf("Ascendant", "Medium_Coeli", "Descendant", "Imum_Coeli")

// Пары-противоположности, между которыми аспекты не имеют смысла (внутри одной карты).
private val OPPOSITE_PAIRS = setOf(
    "Ascendant" to "Descendant", "Descendant" to "Ascendant",
    "Medium_Coeli" to "Imum_Coeli", "Imum_Coeli" to "Medium_Coeli",
    "True_North_Lunar_Node" to "True_South_Lunar_Node",
    "True_South_Lunar_Node" to "True_North_Lunar_Node",
    "Mean_North_Lunar_Node" to "Mean_South_Lunar_Node",
    "Mean_South_Lunar_Node" to "Mean_North_Lunar_Node",
)

private fun match(p1: Double, p2: Double): Triple<String, Double, Int>? {
    val distance = abs(difDeg2n(p1, p2))
    for (a in ACTIVE_ASPECTS) {
        if (distance >= a.degree - a.orb && distance <= a.degree + a.orb) {
            return Triple(a.name, abs(distance - a.degree), a.degree)
        }
    }
    return null
}

/** Порт calculate_aspect_movement: сходящийся/расходящийся/статичный. */
private fun movement(p1: Double, s1: Double, p2: Double, s2: Double, aspectDeg: Int): String {
    if (abs(s1 - s2) < 1e-9) return "Static"
    fun orb(a: Double, b: Double) = abs(abs(difDeg2n(a, b)) - aspectDeg)
    val dt = 0.001
    val change = orb(norm360(p1 + s1 * dt), norm360(p2 + s2 * dt)) - orb(p1, p2)
    return when {
        abs(change) < 1e-6 -> "Static"
        change < 0 -> "Applying"
        else -> "Separating"
    }
}

private fun orderedActive(points: Collection<ChartPoint>): List<ChartPoint> {
    val byName = points.associateBy { it.name }
    return ASPECT_POINT_ORDER.mapNotNull { byName[it] }
}

/** Аспекты внутри одной карты (натал, соляр …). */
fun singleChartAspects(points: Collection<ChartPoint>): List<AspectHit> {
    val active = orderedActive(points)
    val result = mutableListOf<AspectHit>()
    for (i in active.indices) {
        for (j in i + 1 until active.size) {
            val a = active[i]
            val b = active[j]
            if ((a.name to b.name) in OPPOSITE_PAIRS) continue
            val hit = match(a.absPos, b.absPos) ?: continue
            val (name, orbit, degree) = hit
            val move = if (a.name in AXES && b.name in AXES) "Static"
            else movement(a.absPos, a.speed, b.absPos, b.speed, degree)
            result += AspectHit(a.name, b.name, name, orbit, degree, move)
        }
    }
    return result
}

/**
 * Аспекты между двумя картами (транзиты: first — натал, second — транзит).
 * Обе карты «зафиксированы», как на сайте (first/second_subject_is_fixed=True) —
 * скорости обнуляются, движение всегда Static.
 */
fun dualChartAspects(first: Collection<ChartPoint>, second: Collection<ChartPoint>): List<AspectHit> {
    val firstActive = orderedActive(first)
    val secondActive = orderedActive(second)
    val result = mutableListOf<AspectHit>()
    for (a in firstActive) {
        for (b in secondActive) {
            val hit = match(a.absPos, b.absPos) ?: continue
            val (name, orbit, degree) = hit
            result += AspectHit(a.name, b.name, name, orbit, degree, "Static")
        }
    }
    return result
}
