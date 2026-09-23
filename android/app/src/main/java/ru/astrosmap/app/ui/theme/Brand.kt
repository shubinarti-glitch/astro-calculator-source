package ru.astrosmap.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Засечное семейство бренда.
 *
 * ponytail: системный serif вместо вшитого Cormorant. Инстанцированные из вариативного
 * шрифта TTF Android отказывался рендерить — текст не отрисовывался, кадровый цикл
 * вставал и приложение зависало на заставке. Системный serif выглядит близко и не рискует.
 */
val Cormorant = FontFamily.Serif

private val WordmarkGoldDark = Color(0xFFE9DBBF)
private val WordmarkPurpleDark = Color(0xFF8B7BD8)
private val WordmarkGoldLight = Color(0xFF76520B)
private val WordmarkPurpleLight = Color(0xFF5D42A8)

/**
 * Логотип «AstroSMap»: начало золотистое, «Map» — фиолетовый, как в референсе.
 * По буквам поочерёдно вспыхивают крошечные искры-звёздочки — логотип «сверкает»
 * деликатно, без бегущей полосы. Цвета букв не меняются.
 */
@Composable
fun AstroWordmark(fontSize: TextUnit = 34.sp, modifier: Modifier = Modifier) {
    val darkTheme = isSystemInDarkTheme()
    val wordmarkGold = if (darkTheme) WordmarkGoldDark else WordmarkGoldLight
    val wordmarkPurple = if (darkTheme) WordmarkPurpleDark else WordmarkPurpleLight
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = wordmarkGold)) { append("AstroS") }
        withStyle(SpanStyle(color = wordmarkPurple)) { append("Map") }
    }
    // Две искры вспыхивают по очереди (в противофазе) с паузами — спокойное мерцание.
    val tw = rememberInfiniteTransition(label = "wordmark-twinkle")
    val a by tw.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3800
                0f at 0
                0.9f at 480 using FastOutSlowInEasing
                0f at 1080
                0f at 3800
            },
        ),
        label = "sparkA",
    )
    val b by tw.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3800
                0f at 1900
                0.9f at 2380 using FastOutSlowInEasing
                0f at 2980
                0f at 3800
            },
        ),
        label = "sparkB",
    )
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            drawContent()
            fun sparkle(fx: Float, fy: Float, alpha: Float) {
                if (alpha <= 0.02f) return
                val cx = size.width * fx
                val cy = size.height * fy
                val r = size.height * 0.24f * alpha
                val c = Color(0xFFFFF6E6).copy(alpha = alpha)
                drawCircle(c, radius = r * 0.3f, center = Offset(cx, cy))
                val sw = r * 0.16f
                drawLine(c, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = sw, cap = StrokeCap.Round)
            }
            sparkle(0.12f, 0.32f, a) // на «A»
            sparkle(0.60f, 0.60f, b) // на «M»
        },
        style = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        ),
    )
}
