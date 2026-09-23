package ru.astrosmap.app.astro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Golden-тесты: локальный движок обязан совпадать с расчётами сайта
 * (фикстуры сгенерированы из backend/astrology.py — см. scratchpad/gen_golden.py).
 * Допуск по долготе 0.01° (36 угловых секунд).
 */
class GoldenTest {

    private val tolerance = 0.01

    private val engine by lazy {
        val ephe = System.getProperty("ephe.dir")?.let(::File)?.takeIf { it.exists() }
            ?: File("../app/src/main/assets/ephe")
        AstroEngine(ephe.absolutePath)
    }

    private fun fixture(name: String) = Json.parseToJsonElement(
        javaClass.getResourceAsStream("/golden/$name.json")!!.readBytes().decodeToString()
    ).jsonObject

    private fun birthOf(obj: Map<String, kotlinx.serialization.json.JsonElement>) = BirthInput(
        year = obj.getValue("year").jsonPrimitive.int,
        month = obj.getValue("month").jsonPrimitive.int,
        day = obj.getValue("day").jsonPrimitive.int,
        hour = obj.getValue("hour").jsonPrimitive.int,
        minute = obj.getValue("minute").jsonPrimitive.int,
        lat = obj.getValue("lat").jsonPrimitive.double,
        lng = obj.getValue("lng").jsonPrimitive.double,
        tzId = obj.getValue("tz_str").jsonPrimitive.content,
    )

    private fun checkPoints(label: String, expected: List<kotlinx.serialization.json.JsonElement>, actual: List<ChartPoint>) {
        val actualByName = actual.associateBy { it.name }
        for (e in expected) {
            val o = e.jsonObject
            val name = o.getValue("name").jsonPrimitive.content
            val p = actualByName[name] ?: error("$label: точка $name не рассчитана")
            val expPos = o.getValue("abs_pos").jsonPrimitive.double
            assertTrue(
                "$label/$name: abs_pos ${p.absPos} != $expPos",
                abs(difDeg2n(p.absPos, expPos)) <= tolerance,
            )
            assertEquals("$label/$name: sign", o.getValue("sign").jsonPrimitive.content, p.sign)
            assertEquals("$label/$name: house", o.getValue("house_num").jsonPrimitive.int, p.houseNum)
            assertEquals("$label/$name: retrograde", o.getValue("retrograde").jsonPrimitive.boolean, p.retrograde)
        }
        assertEquals("$label: число точек", expected.size, actual.size)
    }

    private fun checkAspects(label: String, expected: List<kotlinx.serialization.json.JsonElement>, actual: List<AspectHit>) {
        val actualByPair = actual.associateBy { Triple(it.p1, it.p2, it.aspect) }
        for (e in expected) {
            val o = e.jsonObject
            val key = Triple(
                o.getValue("p1").jsonPrimitive.content,
                o.getValue("p2").jsonPrimitive.content,
                o.getValue("aspect").jsonPrimitive.content,
            )
            val a = actualByPair[key] ?: error("$label: аспект $key не найден локально")
            val expOrbit = o.getValue("orbit").jsonPrimitive.double
            assertTrue(
                "$label/$key: orbit ${a.orbit} != $expOrbit",
                abs(a.orbit - expOrbit) <= tolerance,
            )
            assertEquals("$label/$key: movement", o.getValue("movement").jsonPrimitive.content, a.movement)
        }
        assertEquals("$label: число аспектов", expected.size, actual.size)
    }

    private fun runNatal(name: String) {
        val fx = fixture("natal_$name")
        val chart = engine.natal(birthOf(fx.getValue("birth").jsonObject))

        checkPoints("$name/planets", fx.getValue("points").jsonArray.toList(), chart.points)
        checkPoints("$name/angles", fx.getValue("angles").jsonArray.toList(), chart.angles)

        val expHouses = fx.getValue("houses").jsonArray
        for ((i, e) in expHouses.withIndex()) {
            val exp = e.jsonObject.getValue("abs_pos").jsonPrimitive.double
            val act = chart.houses[i].absPos
            assertTrue("$name: дом ${i + 1} $act != $exp", abs(difDeg2n(act, exp)) <= tolerance)
        }

        checkAspects("$name/aspects", fx.getValue("aspects").jsonArray.toList(), chart.aspects)

        val lp = fx.getValue("lunar_phase").jsonObject
        assertEquals("$name: фаза Луны", lp.getValue("phase").jsonPrimitive.int, chart.lunarPhase.phase)
        assertEquals("$name: имя фазы", lp.getValue("name").jsonPrimitive.content, chart.lunarPhase.name)
    }

    @Test fun moscow1990() = runNatal("moscow1990")
    @Test fun newyork1985() = runNatal("newyork1985")
    @Test fun sydney1975() = runNatal("sydney1975")
    @Test fun murmansk2000() = runNatal("murmansk2000")
    @Test fun buenosaires2015() = runNatal("buenosaires2015")

    @Test
    fun transitMoscow() {
        val fx = fixture("transit_moscow")
        val chart = engine.transit(
            natal = birthOf(fx.getValue("natal_birth").jsonObject),
            transit = birthOf(fx.getValue("transit_birth").jsonObject),
        )
        checkPoints("transit/points", fx.getValue("transit_points").jsonArray.toList(), chart.transitPoints)
        checkAspects("transit/aspects", fx.getValue("aspects").jsonArray.toList(), chart.aspects)
        val lp = fx.getValue("transit_lunar_phase").jsonObject
        assertEquals("transit: фаза Луны", lp.getValue("phase").jsonPrimitive.int, chart.lunarPhase.phase)
    }
}
