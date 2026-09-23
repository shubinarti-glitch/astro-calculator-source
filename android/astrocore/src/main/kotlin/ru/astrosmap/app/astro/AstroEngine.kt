package ru.astrosmap.app.astro

import swisseph.SweConst
import swisseph.SwissEph
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Офлайн-движок расчёта карт. Точный порт логики сайта (Kerykeion + Swiss Ephemeris):
 * планеты — Moshier-режим Swiss Ephemeris, Хирон — из файла seas_18.se1 (ephePath),
 * дома — Плацидус через swe_houses, аспекты — портированная логика Kerykeion.
 *
 * Паритет с сервером закреплён golden-тестами (см. app/src/test).
 */
class AstroEngine(ephePath: String?) {

    private val swe = SwissEph(ephePath)

    private companion object {
        const val FLAGS = SweConst.SEFLG_SWIEPH or SweConst.SEFLG_SPEED

        // Рассчитываемые точки (DEFAULT_ACTIVE_POINTS Kerykeion): узлы — истинные,
        // Лилит — средняя. Южные узлы выводятся из северных.
        val CALC_POINTS = listOf(
            "Sun" to 0, "Moon" to 1, "Mercury" to 2, "Venus" to 3, "Mars" to 4,
            "Jupiter" to 5, "Saturn" to 6, "Uranus" to 7, "Neptune" to 8, "Pluto" to 9,
            "True_North_Lunar_Node" to 11, "Mean_Lilith" to 12, "Chiron" to 15,
        )

        // Порядок вывода точек, как в PLANET_ORDER сайта (сайт подставляет
        // истинные узлы вместо средних — см. _NODE_FALLBACK в astrology.py).
        val OUTPUT_ORDER = listOf(
            "Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn",
            "Uranus", "Neptune", "Pluto",
            "True_North_Lunar_Node", "True_South_Lunar_Node", "Chiron", "Mean_Lilith",
        )
    }

    fun natal(b: BirthInput): NatalChart {
        val jd = julianDayUt(b)
        val (points, houses, angles) = calcChart(jd, b)
        val all = points.values + angles
        val aspects = singleChartAspects(all)
        val sun = points.getValue("Sun")
        val moon = points.getValue("Moon")
        return NatalChart(
            utcJulianDay = jd,
            points = OUTPUT_ORDER.mapNotNull { points[it] },
            angles = angles,
            houses = houses,
            aspects = aspects,
            lunarPhase = lunarPhase(moon.absPos, sun.absPos),
        )
    }

    fun transit(natal: BirthInput, transit: BirthInput): TransitChart {
        val natalChart = calcChart(julianDayUt(natal), natal)
        val jdT = julianDayUt(transit)
        val (tPoints, _, tAngles) = calcChart(jdT, transit)
        val sun = tPoints.getValue("Sun")
        val moon = tPoints.getValue("Moon")
        val aspects = dualChartAspects(
            first = natalChart.first.values + natalChart.third,
            second = tPoints.values + tAngles,
        )
        return TransitChart(
            transitPoints = OUTPUT_ORDER.mapNotNull { tPoints[it] },
            aspects = aspects,
            lunarPhase = lunarPhase(moon.absPos, sun.absPos),
        )
    }

    // ------------------------------------------------------------------ #
    //  Расчёт позиций
    // ------------------------------------------------------------------ #

    private fun calcChart(jd: Double, b: BirthInput): Triple<Map<String, ChartPoint>, List<HouseCusp>, List<ChartPoint>> {
        // Kerykeion зажимает широту ±66° — иначе Плацидус нестабилен у полюсов.
        val lat = b.lat.coerceIn(-66.0, 66.0)

        val cusps = DoubleArray(13)
        val ascmc = DoubleArray(10)
        swe.swe_houses(jd, 0, lat, b.lng, b.housesSystem.code, cusps, ascmc)
        val cuspDegrees = DoubleArray(12) { cusps[it + 1] }
        val houses = (1..12).map { HouseCusp(it, cusps[it]) }

        val points = LinkedHashMap<String, ChartPoint>()
        for ((name, id) in CALC_POINTS) {
            val xx = DoubleArray(6)
            val serr = StringBuffer()
            val ret = swe.swe_calc_ut(jd, id, FLAGS, xx, serr)
            if (ret < 0) continue // как Kerykeion: точка без эфемерид просто выпадает
            val pos = norm360(xx[0])
            points[name] = ChartPoint(
                name = name,
                absPos = pos,
                speed = xx[3],
                retrograde = xx[3] < 0,
                houseNum = houseOf(pos, cuspDegrees),
            )
            // Южный узел — противоположность северному (порт логики Kerykeion).
            val southName = if (name == "True_North_Lunar_Node") "True_South_Lunar_Node" else null
            if (southName != null) {
                val southPos = norm360(pos + 180.0)
                points[southName] = ChartPoint(
                    name = southName,
                    absPos = southPos,
                    speed = -xx[3],
                    retrograde = xx[3] < 0,
                    houseNum = houseOf(southPos, cuspDegrees),
                )
            }
        }

        // Углы: ASC/MC из ascmc, DSC/IC — противоположные точки.
        // Скорость угла — численно (Kerykeion берёт её из houses_ex2): нужна для applying/separating.
        val dt = 1e-5
        val cusps2 = DoubleArray(13)
        val ascmc2 = DoubleArray(10)
        swe.swe_houses(jd + dt, 0, lat, b.lng, b.housesSystem.code, cusps2, ascmc2)
        val ascSpeed = difDeg2n(ascmc2[0], ascmc[0]) / dt
        val mcSpeed = difDeg2n(ascmc2[1], ascmc[1]) / dt

        fun angle(name: String, pos: Double, speed: Double) = ChartPoint(
            name = name,
            absPos = norm360(pos),
            speed = speed,
            retrograde = false,
            houseNum = houseOf(norm360(pos), cuspDegrees),
        )

        val angles = listOf(
            angle("Ascendant", ascmc[0], ascSpeed),
            angle("Medium_Coeli", ascmc[1], mcSpeed),
            angle("Descendant", ascmc[0] + 180.0, ascSpeed),
            angle("Imum_Coeli", ascmc[1] + 180.0, mcSpeed),
        )

        return Triple(points, houses, angles)
    }

    /** Юлианский день UT — та же формула, что kerykeion.utilities.datetime_to_julian. */
    fun julianDayUt(b: BirthInput): Double {
        val utc = ZonedDateTime.of(b.year, b.month, b.day, b.hour, b.minute, 0, 0, ZoneId.of(b.tzId))
            .withZoneSameInstant(ZoneOffset.UTC)
        var year = utc.year
        var month = utc.monthValue
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = year / 100
        val bCorr = 2 - a + a / 4
        var jd = (365.25 * (year + 4716)).toInt() + (30.6001 * (month + 1)).toInt() + utc.dayOfMonth + bCorr - 1524.5
        jd += (utc.hour + utc.minute / 60.0 + utc.second / 3600.0) / 24.0
        return jd
    }

    /** Дом по позиции: порт kerykeion.utilities.get_planet_house / is_point_between. */
    private fun houseOf(pos: Double, cusps: DoubleArray): Int {
        for (i in 0 until 12) {
            val start = cusps[i]
            val end = cusps[(i + 1) % 12]
            val span = norm360(end - start)
            val dist = norm360(pos - start)
            if (span > 0 && dist < span) return i + 1
        }
        return 12 // на границе куспида из-за округления — крайний случай
    }

    /** Фаза Луны: порт kerykeion.utilities.calculate_moon_phase (28 шагов, 8 имён). */
    fun lunarPhase(moonAbs: Double, sunAbs: Double): LunarPhase {
        val degrees = norm360(moonAbs - sunAbs)
        val phase = (degrees / (360.0 / 28.0)).toInt() + 1
        val name = when {
            phase == 1 -> "New Moon"
            phase < 7 -> "Waxing Crescent"
            phase <= 9 -> "First Quarter"
            phase < 14 -> "Waxing Gibbous"
            phase == 14 -> "Full Moon"
            phase < 20 -> "Waning Gibbous"
            phase <= 22 -> "Last Quarter"
            else -> "Waning Crescent"
        }
        return LunarPhase(phase, name, degrees)
    }
}

internal fun norm360(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

/** Кратчайшая угловая разница в диапазоне [-180, 180) — как swe_difdeg2n. */
internal fun difDeg2n(p1: Double, p2: Double): Double {
    val dif = norm360(p1 - p2)
    return if (dif >= 180.0) dif - 360.0 else dif
}
