package ru.astrosmap.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Звёздное небо — как фон сайта. Рисуется один раз, позиции фиксированы сидом. */
@Composable
fun StarryBackground(modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0A0A1A)
    if (!isDark) return
    Canvas(modifier.fillMaxSize()) {
        val rnd = Random(42)
        repeat(90) {
            val x = rnd.nextFloat() * size.width
            val y = rnd.nextFloat() * size.height
            val r = 0.6f + rnd.nextFloat() * 1.6f
            val a = 0.12f + rnd.nextFloat() * 0.5f
            drawCircle(Color(0xFFECE9F5).copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }
}

/** Панель-карточка в стиле сайта: скругление, полупрозрачная заливка, тонкая рамка. */
@Composable
fun AstroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Тёмная тема — полупрозрачная панель поверх звёзд; светлая — белая карточка.
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0A0A1A)
    val panelColor = if (isDark) Color(0xB8161630) else Color(0xF7FFFFFF)
    val panelBorder = if (isDark) Color(0x2E7878C8) else Color(0x33A099C8)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = panelColor,
        border = BorderStroke(1.dp, panelBorder),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Шапка приложения: живой логотип-звёзды + название, как на сайте. */
@Composable
fun AppHeader(subtitle: String) {
    // Две звезды пульсируют поочерёдно: одна разгорается, пока вторая гаснет.
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "logo")
    val bigStar by pulse.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "big",
    )
    val smallStar by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.55f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "small",
    )
    // Резкая вспышка-блик: логотип «сверкает» поверх плавной пульсации.
    val sparkle by pulse.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            keyframes {
                durationMillis = 2600
                0f at 0
                1f at 240 using androidx.compose.animation.core.FastOutSlowInEasing
                0f at 760
                0f at 2600
            },
            androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "sparkle",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(40.dp)) {
            fun star(cx: Float, cy: Float, rl: Float, rs: Float, color: Color) {
                val p = Path()
                for (i in 0 until 8) {
                    val r = if (i % 2 == 0) rl else rs
                    val a = Math.toRadians(i * 45.0 - 90).toFloat()
                    val x = cx + r * kotlin.math.cos(a)
                    val y = cy + r * kotlin.math.sin(a)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, color)
            }
            // Пульсация — через яркость и размер лучей; на пике блика цвет уходит в белый.
            star(
                size.width * 0.45f, size.height * 0.55f,
                size.width * 0.42f * bigStar, size.width * 0.10f,
                lerp(Color(0xFFC9A86A), Color.White, sparkle).copy(alpha = maxOf(bigStar, sparkle)),
            )
            star(
                size.width * 0.78f, size.height * 0.22f,
                size.width * 0.13f * smallStar, size.width * 0.035f,
                Color(0xFF8B7BD8).copy(alpha = smallStar),
            )
            // Блик-крестовина поверх большой звезды — короткая яркая вспышка.
            if (sparkle > 0.02f) {
                val cx = size.width * 0.45f
                val cy = size.height * 0.55f
                val white = Color.White.copy(alpha = sparkle)
                drawCircle(white, radius = size.width * 0.05f * sparkle, center = Offset(cx, cy))
                val len = size.width * 0.5f * sparkle
                val sw = size.width * 0.02f
                drawLine(white, Offset(cx, cy - len), Offset(cx, cy + len), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(white, Offset(cx - len, cy), Offset(cx + len, cy), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            AstroWordmark(fontSize = 30.sp)
            Text(
                "Project Artemisa · " + subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
