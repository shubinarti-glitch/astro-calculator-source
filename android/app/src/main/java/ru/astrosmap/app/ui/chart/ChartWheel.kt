package ru.astrosmap.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import ru.astrosmap.app.astro.AspectHit
import ru.astrosmap.app.astro.ChartPoint
import ru.astrosmap.app.astro.HouseCusp
import ru.astrosmap.app.astro.NatalChart
import ru.astrosmap.app.ui.AstroLabels
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Палитра колеса — тёмная тема сайта (frontend/css/style.css).
private val DarkFrameColor = Color(0xFF0C0C1E)  // --chart-frame
private val DarkLineDim = Color(0x338B9BD8)     // тонкие линии
private val DarkLineBright = Color(0x668B9BD8)
private val LightFrameColor = Color(0xFFF8F4EC)
private val LightLineDim = Color(0x334A435F)
private val LightLineBright = Color(0x80534B68)
private val DarkGoldColor = Color(0xFFC9A86A)   // --accent
private val LightGoldColor = Color(0xFF8A6320)
private val DarkTextColor = Color(0xFFECE9F5)   // --text
private val LightTextColor = Color(0xFF292438)
private val DarkHarmoniousColor = Color(0xFF5FC98A) // --good
private val LightHarmoniousColor = Color(0xFF187A49)
private val DarkTenseColor = Color(0xFFE0716F)      // --bad
private val LightTenseColor = Color(0xFFB83232)
private val DarkCreativeColor = Color(0xFF8B7BD8)   // --accent-2
private val LightCreativeColor = Color(0xFF5A49B0)

// Цвета стихий: огонь/земля/воздух/вода (по индексу знака % 4).
private val DarkElementColors = listOf(
    Color(0xFFE0716F), Color(0xFF5FC98A), Color(0xFFC9A86A), Color(0xFF8B9BD8),
)
private val LightElementColors = listOf(
    Color(0xFFB83232), Color(0xFF187A49), Color(0xFF8A6320), Color(0xFF465AA0),
)

private val SignGlyphs = ru.astrosmap.app.astro.SIGNS.map { AstroLabels.signGlyphs.getValue(it) }

private val Angles = setOf("Ascendant", "Medium_Coeli", "Descendant", "Imum_Coeli")
private val PointGlyphs = AstroLabels.pointGlyphs.filterKeys { it !in Angles }

private fun aspectColor(aspect: String, darkTheme: Boolean): Color = when (aspect) {
    "trine", "sextile" -> if (darkTheme) DarkHarmoniousColor else LightHarmoniousColor
    "square", "opposition" -> if (darkTheme) DarkTenseColor else LightTenseColor
    "quintile" -> if (darkTheme) DarkCreativeColor else LightCreativeColor
    else -> if (darkTheme) DarkGoldColor else LightGoldColor // conjunction
}

/** Колесо натальной карты: зодиак, дома, планеты, линии аспектов. */
@Composable
fun ChartWheel(chart: NatalChart, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val darkTheme = isSystemInDarkTheme()
    Canvas(modifier = modifier) {
        drawWheel(chart, measurer, darkTheme)
    }
}

internal fun DrawScope.drawWheel(
    chart: NatalChart,
    measurer: TextMeasurer,
    darkTheme: Boolean = true,
) {
    val frameColor = if (darkTheme) DarkFrameColor else LightFrameColor
    val lineDim = if (darkTheme) DarkLineDim else LightLineDim
    val lineBright = if (darkTheme) DarkLineBright else LightLineBright
    val textColor = if (darkTheme) DarkTextColor else LightTextColor
    val goldColor = if (darkTheme) DarkGoldColor else LightGoldColor
    val tenseColor = if (darkTheme) DarkTenseColor else LightTenseColor
    val elementColors = if (darkTheme) DarkElementColors else LightElementColors
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = min(cx, cy) * 0.92f // запас под подписи As/Mc снаружи
    val asc = chart.houses.first { it.num == 1 }.absPos

    // Экранный угол долготы: ASC слева, зодиак растёт против часовой стрелки.
    fun angleOf(lon: Double): Double = PI - Math.toRadians(lon - asc)
    fun pos(r: Float, lon: Double): Offset {
        val a = angleOf(lon)
        return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }

    val rZodiacOuter = radius
    val rZodiacInner = radius * 0.84f
    val rPlanet = radius * 0.70f
    val rHouseNum = radius * 0.48f
    val rAspect = radius * 0.42f

    // Фон и окружности.
    drawCircle(frameColor, rZodiacOuter, Offset(cx, cy))
    for (r in listOf(rZodiacOuter, rZodiacInner, rAspect)) {
        drawCircle(lineBright, r, Offset(cx, cy), style = Stroke(radius * 0.004f))
    }

    // Зодиак: границы знаков и символы.
    for (i in 0 until 12) {
        val lon = i * 30.0
        drawLine(lineBright, pos(rZodiacInner, lon), pos(rZodiacOuter, lon), radius * 0.004f)
        drawGlyph(
            measurer, SignGlyphs[i], pos(radius * 0.92f, lon + 15.0),
            elementColors[i % 4], radius * 0.075f,
        )
    }
    // Мелкие деления по 5°.
    for (d in 0 until 72) {
        val lon = d * 5.0
        if (d % 6 != 0) {
            drawLine(lineDim, pos(rZodiacInner, lon), pos(rZodiacInner * 1.025f, lon), radius * 0.003f)
        }
    }

    // Дома: куспиды и номера. Оси (1/4/7/10) — золотом и толще.
    for (h in chart.houses) {
        val isAxis = h.num in listOf(1, 4, 7, 10)
        drawLine(
            if (isAxis) goldColor else lineBright,
            pos(rAspect, h.absPos), pos(rZodiacInner, h.absPos),
            radius * if (isAxis) 0.007f else 0.003f,
            cap = StrokeCap.Round,
        )
        val next = chart.houses.first { it.num == h.num % 12 + 1 }
        val mid = h.absPos + (((next.absPos - h.absPos) % 360.0 + 360.0) % 360.0) / 2.0
        drawGlyph(measurer, h.num.toString(), pos(rHouseNum, mid), lineBright.copy(alpha = 0.9f), radius * 0.045f)
    }

    // Подписи углов снаружи колеса.
    val angleLabels = listOf("Ascendant" to "As", "Medium_Coeli" to "Mc", "Descendant" to "Ds", "Imum_Coeli" to "Ic")
    for ((name, label) in angleLabels) {
        val a = chart.angles.firstOrNull { it.name == name } ?: continue
        drawGlyph(measurer, label, pos(radius * 1.055f, a.absPos), goldColor, radius * 0.05f)
    }

    // Линии аспектов (по истинным долготам): прозрачность растёт с точностью орбиса.
    val lonByName = (chart.points + chart.angles).associate { it.name to it.absPos }
    for (asp in chart.aspects) {
        val l1 = lonByName[asp.p1] ?: continue
        val l2 = lonByName[asp.p2] ?: continue
        val alpha = (1.0f - (asp.orbit / 12.0f).toFloat()).coerceIn(0.25f, 0.9f)
        drawLine(
            aspectColor(asp.aspect, darkTheme).copy(alpha = alpha),
            pos(rAspect, l1), pos(rAspect, l2), radius * 0.005f,
        )
    }

    // Планеты: чёрточка на истинном градусе + символ с раздвижкой от наложений.
    val shown = chart.points.filter { it.name in PointGlyphs }
    val spreadLons = spreadAngles(shown.map { it.absPos }, minSep = 7.0)
    for ((i, p) in shown.withIndex()) {
        drawLine(textColor, pos(rZodiacInner, p.absPos), pos(rZodiacInner * 0.965f, p.absPos), radius * 0.005f)
        val glyphPos = pos(rPlanet, spreadLons[i])
        drawGlyph(measurer, PointGlyphs.getValue(p.name), glyphPos, textColor, radius * 0.08f)
        if (p.retrograde) {
            val rPos = pos(rPlanet * 0.88f, spreadLons[i])
            drawGlyph(measurer, "R", rPos, tenseColor, radius * 0.038f)
        }
    }
}

/** Раздвигает близкие долготы, чтобы символы планет не накладывались. */
internal fun spreadAngles(lons: List<Double>, minSep: Double): List<Double> {
    if (lons.size < 2) return lons
    val indexed = lons.withIndex().sortedBy { it.value }
    val adjusted = indexed.map { it.value }.toMutableList()
    repeat(30) {
        for (i in adjusted.indices) {
            val j = (i + 1) % adjusted.size
            val gap = if (j == 0) (adjusted[j] + 360.0) - adjusted[i] else adjusted[j] - adjusted[i]
            if (gap < minSep) {
                val push = (minSep - gap) / 2.0
                adjusted[i] -= push
                adjusted[j] += push
            }
        }
    }
    val result = DoubleArray(lons.size)
    for ((k, entry) in indexed.withIndex()) result[entry.index] = adjusted[k]
    return result.toList()
}

private fun DrawScope.drawGlyph(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    sizePx: Float,
) {
    val layout = measurer.measure(
        text,
        // Глифы — часть геометрии карты. Компенсируем fontScale,
        // чтобы системный крупный шрифт не нарушал расположение планет.
        TextStyle(color = color, fontSize = (sizePx / (density * fontScale)).sp, fontWeight = FontWeight.Medium),
    )
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
    )
}
