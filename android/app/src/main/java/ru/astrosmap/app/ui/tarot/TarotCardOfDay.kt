package ru.astrosmap.app.ui.tarot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.theme.AstroPanel

private enum class DayPhase { IDLE, SHUFFLE, PICK, REVEAL }

/**
 * «Карта дня» на экране «Сегодня». Раз в сутки: тап по колоде → перетасовка →
 * три рубашки → выбор одной → переворот и трактовка. Личное участие — часть смысла.
 */
@Composable
fun CardOfDaySection() {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(DayPhase.IDLE) }
    var three by remember { mutableStateOf<List<TarotCard>>(emptyList()) }
    var chosen by remember { mutableStateOf<TarotCard?>(null) }

    // Если карта уже вытянута сегодня — показываем её сразу.
    LaunchedEffect(Unit) {
        TarotStorage.todayCard(context)?.let { chosen = it; phase = DayPhase.REVEAL }
    }

    // Короткая перетасовка перед раскладкой трёх карт.
    LaunchedEffect(phase) {
        if (phase == DayPhase.SHUFFLE) {
            three = TarotDeck.draw(3)
            delay(1_500)
            phase = DayPhase.PICK
        }
    }

    AstroPanel {
        Text(
            "🔮 " + stringResource(R.string.tarot_day_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        when (phase) {
            DayPhase.IDLE -> {
                Text(
                    stringResource(R.string.tarot_day_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DeckBack(onClick = { phase = DayPhase.SHUFFLE })
                }
            }
            DayPhase.SHUFFLE -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ShuffleAnimation()
                }
            }
            DayPhase.PICK -> {
                Text(
                    stringResource(R.string.tarot_day_pick),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    three.forEachIndexed { index, card ->
                        PickableDayCard(index, Modifier.weight(1f)) {
                            chosen = card
                            TarotStorage.saveDayCard(context, card)
                            phase = DayPhase.REVEAL
                        }
                    }
                }
            }
            DayPhase.REVEAL -> chosen?.let { RevealedCard(it) }
        }
    }
}

/** Крупная рубашка-колода для начального экрана. */
@Composable
private fun DeckBack(onClick: () -> Unit) {
    CardBack(
        Modifier
            .width(140.dp)
            .aspectRatio(0.58f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

/** Рубашка карты — рисуется, а не картинка: космический стиль, золотая рамка и звезда. */
@Composable
fun CardBack(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF1B1740), Color(0xFF0E0B22))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.09f),
            )
            val inset = size.minDimension * 0.08f
            drawRoundRect(
                color = Color(0xFFC9A86A),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.06f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.02f),
            )
        }
        Text("✦", color = Color(0xFFC9A86A), style = MaterialTheme.typography.headlineMedium)
    }
}

/** Живая перетасовка: пять карт перекладываются из руки в руку и снова собираются. */
@Composable
private fun ShuffleAnimation() {
    val transition = rememberInfiniteTransition(label = "tarot_shuffle")
    val motion by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tarot_shuffle_motion",
    )
    Box(Modifier.width(190.dp).aspectRatio(1.05f), contentAlignment = Alignment.Center) {
        repeat(5) { i ->
            val depth = i - 2
            val side = if (i % 2 == 0) 1f else -1f
            CardBack(
                Modifier
                    .width(104.dp)
                    .aspectRatio(0.58f)
                    .graphicsLayer {
                        translationX = side * motion * (34f + i * 4f) * density
                        translationY = kotlin.math.abs(motion) * (8f + i) * density - i * 2f * density
                        rotationZ = depth * 3.5f + side * motion * 10f
                        scaleX = 1f - i * 0.012f
                        scaleY = scaleX
                        alpha = 1f - i * 0.055f
                    },
            )
        }
    }
}

/** Три карты не появляются резко: каждая по очереди выезжает из собранной колоды. */
@Composable
private fun PickableDayCard(index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        delay(index * 120L)
        appear.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    CardBack(
        modifier
            .aspectRatio(0.58f)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 54f * density
                rotationZ = (index - 1) * 7f * appear.value
                scaleX = 0.88f + 0.12f * appear.value
                scaleY = scaleX
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    )
}

/** Выбранная карта переворачивается лицом и показывает трактовку. */
@Composable
private fun RevealedCard(card: TarotCard) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(600), label = "flip",
    )
    LaunchedEffect(card.id) { flipped = true }
    val face = rememberTarotFace(card.id)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(150.dp)
                .aspectRatio(0.58f)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 14f * density
                },
            contentAlignment = Alignment.Center,
        ) {
            if (rotation <= 90f) {
                CardBack(Modifier.fillMaxSize())
            } else {
                // Вторую половину переворота показываем лицо, компенсируя зеркалирование.
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                    if (face != null) {
                        Image(
                            bitmap = face,
                            contentDescription = card.name,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        CardBack(Modifier.fillMaxSize())
                    }
                }
            }
        }
        Text(
            card.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            card.meaning,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "💡 " + card.advice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
