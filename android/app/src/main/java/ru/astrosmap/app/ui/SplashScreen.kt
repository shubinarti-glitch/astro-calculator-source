package ru.astrosmap.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.theme.AstroWordmark
import ru.astrosmap.app.ui.theme.Cormorant
import ru.astrosmap.app.ui.theme.StarryBackground

/**
 * Заставка при входе в стиле референса: монограмма-логотип, двухцветное «AstroSMap»
 * засечным шрифтом, слоган. Логотип появляется с масштабом, звёздный блик мерцает,
 * текст плавно проступает. По завершении вызывается onFinished — переход в приложение.
 */
@Composable
fun SplashScreen(start: Boolean, onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    // Мягкое мерцание ореола вокруг логотипа.
    val twinkle = rememberInfiniteTransition(label = "twinkle")
    val glow by twinkle.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )

    // Анимация запускается только когда системная заставка ушла (start=true).
    // Если её ухода так и не дождались — уходим сами: лучше без анимации, чем навсегда
    // застрять на заставке. Когда start станет true, эффект перезапустится, а эта ветка
    // отменится.
    LaunchedEffect(start) {
        if (!start) {
            delay(1500)
            onFinished()
            return@LaunchedEffect
        }
        logoAlpha.animateTo(1f, tween(450))
        logoScale.animateTo(1f, tween(650))
        textAlpha.animateTo(1f, tween(550))
        delay(1000)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A1A), Color(0xFF12122B), Color(0xFF0A0A1A)))),
        contentAlignment = Alignment.Center,
    ) {
        StarryBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Ореол
                Box(
                    Modifier
                        .size(150.dp)
                        .alpha(glow * logoAlpha.value)
                        .background(
                            Brush.radialGradient(listOf(Color(0x66C9A86A), Color(0x00000000))),
                            RoundedCornerShape(50),
                        ),
                )
                Image(
                    painter = painterResource(R.drawable.brand_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(128.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value; scaleY = logoScale.value
                            alpha = logoAlpha.value
                        }
                        .clip(RoundedCornerShape(28.dp)),
                )
            }

            AstroWordmark(
                fontSize = 44.sp,
                modifier = Modifier.padding(top = 22.dp).alpha(textAlpha.value),
            )
            Text(
                stringResource(R.string.splash_tagline),
                color = Color(0xFF9A98B8),
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).alpha(textAlpha.value),
            )
        }

        Text(
            stringResource(R.string.splash_footer),
            color = Color(0xFF6A6890),
            fontFamily = Cormorant,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(textAlpha.value),
        )
    }
}
