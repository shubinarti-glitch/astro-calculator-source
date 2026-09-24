package ru.astrosmap.app.ui.tools

import ru.astrosmap.app.ui.AstroLabels

/** Тексты лунного календаря — порт LUNAR_PHASE_ADVICE / MOON_IN_SIGN_MOOD сайта (backend/interpretations.py). */
object LunarTexts {

    private fun g(pair: Pair<String, String>) = if (AstroLabels.isRu()) pair.first else pair.second

    val phaseEmoji = mapOf(
        "New Moon" to "🌑", "Waxing Crescent" to "🌒", "First Quarter" to "🌓",
        "Waxing Gibbous" to "🌔", "Full Moon" to "🌕", "Waning Gibbous" to "🌖",
        "Last Quarter" to "🌗", "Waning Crescent" to "🌘",
    )

    private val phaseNames = mapOf(
        "New Moon" to ("Новолуние" to "New Moon"),
        "Waxing Crescent" to ("Растущий серп" to "Waxing Crescent"),
        "First Quarter" to ("Первая четверть" to "First Quarter"),
        "Waxing Gibbous" to ("Растущая Луна" to "Waxing Gibbous"),
        "Full Moon" to ("Полнолуние" to "Full Moon"),
        "Waning Gibbous" to ("Убывающая Луна" to "Waning Gibbous"),
        "Last Quarter" to ("Последняя четверть" to "Last Quarter"),
        "Waning Crescent" to ("Убывающий серп" to "Waning Crescent"),
    )

    fun phaseName(key: String): String = phaseNames[key]?.let { g(it) } ?: key

    fun phaseAdvice(key: String): String = ru.astrosmap.app.editorial.RemoteEditorial.lunar(key, false, AstroLabels.isRu())
    fun moonMood(sign: String): String = ru.astrosmap.app.editorial.RemoteEditorial.lunar(sign, true, AstroLabels.isRu())
}
