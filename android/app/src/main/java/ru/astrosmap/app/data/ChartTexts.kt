package ru.astrosmap.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Titled(val title: String, val text: String)

/** Трактовки из ответа /api/natal?svg=0 — только текстовые блоки, всё опционально. */
data class ChartTexts(
    val storySections: List<Titled>,
    val bigThree: List<Titled>,
    val temperament: List<Titled>,
    val spheres: List<Titled>,
    val planetTexts: Map<String, List<Titled>>, // имя точки -> абзацы interp_full
    val aspectTexts: Map<String, String>,       // "p1|aspect|p2" -> interp
) {
    companion object {
        fun aspectKey(p1: String, aspect: String, p2: String) = "$p1|$aspect|$p2"

        /** Разбор ответа сервера; незнакомые/отсутствующие поля просто пропускаются. */
        fun parse(raw: String): ChartTexts {
            val root = Json.parseToJsonElement(raw).jsonObject

            fun str(obj: JsonObject?, key: String): String? =
                obj?.get(key)?.jsonPrimitive?.takeIf { it.isString }?.content

            val story = root.obj("story")?.get("sections")?.jsonArray.orEmpty().mapNotNull {
                val o = it.jsonObject
                val title = str(o, "title") ?: return@mapNotNull null
                val text = str(o, "text") ?: return@mapNotNull null
                Titled(title, text)
            }

            val big = root.obj("big_three")?.let { bt ->
                listOf("sun" to "☉", "moon" to "☽", "asc" to "As").mapNotNull { (key, glyph) ->
                    val o = bt.obj(key) ?: return@mapNotNull null
                    val text = str(o, "text") ?: return@mapNotNull null
                    Titled("$glyph ${str(o, "sign_ru").orEmpty()}", text)
                }
            }.orEmpty()

            val temperament = buildList {
                str(root.obj("profile"), "core_text")?.let { add(Titled("", it)) }
                root.obj("psych")?.obj("temperament")?.let { t ->
                    str(t, "text")?.let { add(Titled(str(t, "name").orEmpty(), it)) }
                }
            }

            val spheres = root.obj("spheres")?.let { s ->
                listOf("love", "career", "health").mapNotNull { key ->
                    str(s, key)?.let { Titled(key, it) }
                }
            }.orEmpty()

            val planetTexts = root["planets"]?.jsonArray.orEmpty().mapNotNull { p ->
                val o = p.jsonObject
                val name = str(o, "name") ?: return@mapNotNull null
                val paragraphs = o["interp_full"]?.jsonArray.orEmpty().mapNotNull { block ->
                    val b = block.jsonObject
                    val text = str(b, "text") ?: return@mapNotNull null
                    Titled(str(b, "label").orEmpty(), text)
                }
                if (paragraphs.isEmpty()) null else name to paragraphs
            }.toMap()

            val aspectTexts = root["aspects"]?.jsonArray.orEmpty().mapNotNull { a ->
                val o = a.jsonObject
                val interp = str(o, "interp") ?: return@mapNotNull null
                val key = aspectKey(
                    str(o, "p1") ?: return@mapNotNull null,
                    str(o, "aspect") ?: return@mapNotNull null,
                    str(o, "p2") ?: return@mapNotNull null,
                )
                key to interp
            }.toMap()

            return ChartTexts(story, big, temperament, spheres, planetTexts, aspectTexts)
        }

        private fun JsonObject.obj(key: String): JsonObject? =
            (this[key] as? JsonObject)
    }
}
