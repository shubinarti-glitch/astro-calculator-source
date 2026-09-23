package ru.astrosmap.app.ui

import java.util.Locale

/** Символы и локализованные названия точек/знаков/аспектов (как на сайте). */
object AstroLabels {

    fun isRu(): Boolean = Locale.getDefault().language == "ru"

    val pointGlyphs = mapOf(
        "Sun" to "☉", "Moon" to "☽", "Mercury" to "☿", "Venus" to "♀", "Mars" to "♂",
        "Jupiter" to "♃", "Saturn" to "♄", "Uranus" to "♅", "Neptune" to "♆", "Pluto" to "♇",
        "True_North_Lunar_Node" to "☊", "True_South_Lunar_Node" to "☋",
        "Chiron" to "⚷", "Mean_Lilith" to "⚸",
        "Ascendant" to "As", "Medium_Coeli" to "Mc", "Descendant" to "Ds", "Imum_Coeli" to "Ic",
    )

    private val pointsRu = mapOf(
        "Sun" to "Солнце", "Moon" to "Луна", "Mercury" to "Меркурий", "Venus" to "Венера",
        "Mars" to "Марс", "Jupiter" to "Юпитер", "Saturn" to "Сатурн", "Uranus" to "Уран",
        "Neptune" to "Нептун", "Pluto" to "Плутон",
        "True_North_Lunar_Node" to "Северный узел", "True_South_Lunar_Node" to "Южный узел",
        "Chiron" to "Хирон", "Mean_Lilith" to "Лилит",
        "Ascendant" to "Асцендент", "Medium_Coeli" to "Середина неба",
        "Descendant" to "Десцендент", "Imum_Coeli" to "Глубина неба",
    )

    private val pointsEn = mapOf(
        "True_North_Lunar_Node" to "North Node", "True_South_Lunar_Node" to "South Node",
        "Medium_Coeli" to "Midheaven", "Imum_Coeli" to "Imum Coeli", "Mean_Lilith" to "Lilith",
    )

    fun point(name: String): String =
        if (isRu()) pointsRu[name] ?: name
        else pointsEn[name] ?: name.replace('_', ' ')

    // U+FE0E — текстовое представление вместо эмодзи.
    val signGlyphs: Map<String, String> = listOf(
        "Ari" to "♈", "Tau" to "♉", "Gem" to "♊", "Can" to "♋", "Leo" to "♌", "Vir" to "♍",
        "Lib" to "♎", "Sco" to "♏", "Sag" to "♐", "Cap" to "♑", "Aqu" to "♒", "Pis" to "♓",
    ).associate { (k, v) -> k to (v + "︎") }

    private val signsRu = mapOf(
        "Ari" to "Овен", "Tau" to "Телец", "Gem" to "Близнецы", "Can" to "Рак",
        "Leo" to "Лев", "Vir" to "Дева", "Lib" to "Весы", "Sco" to "Скорпион",
        "Sag" to "Стрелец", "Cap" to "Козерог", "Aqu" to "Водолей", "Pis" to "Рыбы",
    )

    private val signsEn = mapOf(
        "Ari" to "Aries", "Tau" to "Taurus", "Gem" to "Gemini", "Can" to "Cancer",
        "Leo" to "Leo", "Vir" to "Virgo", "Lib" to "Libra", "Sco" to "Scorpio",
        "Sag" to "Sagittarius", "Cap" to "Capricorn", "Aqu" to "Aquarius", "Pis" to "Pisces",
    )

    fun sign(code: String): String = (if (isRu()) signsRu else signsEn)[code] ?: code

    private val aspectsRu = mapOf(
        "conjunction" to "Соединение", "opposition" to "Оппозиция", "trine" to "Трин",
        "square" to "Квадрат", "sextile" to "Секстиль", "quintile" to "Квинтиль",
    )

    val aspectGlyphs = mapOf(
        "conjunction" to "☌", "opposition" to "☍", "trine" to "△",
        "square" to "□", "sextile" to "✱", "quintile" to "Q",
    )

    fun aspect(kind: String): String =
        if (isRu()) aspectsRu[kind] ?: kind
        else kind.replaceFirstChar { it.uppercase() }

    /** «17°42′» внутри знака. */
    fun degMin(position: Double): String {
        var d = position.toInt()
        var m = Math.round((position - d) * 60).toInt()
        if (m == 60) { d += 1; m = 0 }
        return "$d°${m.toString().padStart(2, '0')}′"
    }
}
