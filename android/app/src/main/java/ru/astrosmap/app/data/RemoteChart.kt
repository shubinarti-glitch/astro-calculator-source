package ru.astrosmap.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ru.astrosmap.app.astro.AspectHit
import ru.astrosmap.app.astro.ChartPoint
import ru.astrosmap.app.astro.HouseCusp
import ru.astrosmap.app.astro.LunarPhase
import ru.astrosmap.app.astro.NatalChart

/**
 * Карта из ответа сервера (соляр, лунар …) в модели движка — чтобы рисовать
 * тем же ChartWheel. Формат — сериализация backend/astrology.py.
 */
object RemoteChart {

    fun parse(root: JsonObject): NatalChart? {
        val points = root["planets"]?.jsonArray?.mapNotNull { point(it.jsonObject) } ?: return null
        val angles = root["angles"]?.jsonArray?.mapNotNull { point(it.jsonObject) }.orEmpty()
        val houses = root["houses"]?.jsonArray?.mapNotNull { h ->
            val o = h.jsonObject
            HouseCusp(
                num = o.int("house_num") ?: return@mapNotNull null,
                absPos = o.dbl("abs_pos") ?: return@mapNotNull null,
            )
        }.orEmpty()
        if (houses.size != 12 || angles.isEmpty()) return null

        val aspects = root["aspects"]?.jsonArray?.mapNotNull { a ->
            val o = a.jsonObject
            AspectHit(
                p1 = o.str("p1") ?: return@mapNotNull null,
                p2 = o.str("p2") ?: return@mapNotNull null,
                aspect = o.str("aspect") ?: return@mapNotNull null,
                orbit = o.dbl("orbit") ?: 0.0,
                aspectDegrees = o.int("degrees") ?: 0,
                movement = "Static",
            )
        }.orEmpty()

        val lunar = root["lunar_phase"]?.jsonObject
        return NatalChart(
            utcJulianDay = 0.0, // для отрисовки не нужен
            points = points,
            angles = angles,
            houses = houses,
            aspects = aspects,
            lunarPhase = LunarPhase(
                phase = lunar?.int("phase") ?: 1,
                name = lunar?.str("name").orEmpty(),
                degreesBetween = 0.0,
            ),
        )
    }

    private fun point(o: JsonObject): ChartPoint? {
        return ChartPoint(
            name = o.str("name") ?: return null,
            absPos = o.dbl("abs_pos") ?: return null,
            speed = o.dbl("speed") ?: 0.0,
            retrograde = (o["retrograde"] as? JsonPrimitive)?.content == "true",
            houseNum = o.int("house_num") ?: 1,
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
}
