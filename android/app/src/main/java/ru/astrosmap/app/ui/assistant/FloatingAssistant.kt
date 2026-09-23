package ru.astrosmap.app.ui.assistant

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import ru.astrosmap.app.R
import ru.astrosmap.app.data.DailyNotify
import ru.astrosmap.app.ui.tarot.TarotStorage

private enum class AssistantMotion { IDLE, SLEEPING, REACTING }

private val starFrames = intArrayOf(
    R.drawable.assistant_star_00, R.drawable.assistant_star_01,
    R.drawable.assistant_star_02, R.drawable.assistant_star_03,
    R.drawable.assistant_star_04, R.drawable.assistant_star_05,
    R.drawable.assistant_star_06, R.drawable.assistant_star_07,
    R.drawable.assistant_star_08, R.drawable.assistant_star_09,
    R.drawable.assistant_star_10, R.drawable.assistant_star_11,
)

private val sunFrames = intArrayOf(
    R.drawable.assistant_sun_00, R.drawable.assistant_sun_01,
    R.drawable.assistant_sun_02, R.drawable.assistant_sun_03,
    R.drawable.assistant_sun_04, R.drawable.assistant_sun_05,
    R.drawable.assistant_sun_06, R.drawable.assistant_sun_07,
    R.drawable.assistant_sun_08, R.drawable.assistant_sun_09,
    R.drawable.assistant_sun_10, R.drawable.assistant_sun_11,
)

private val moonFrames = intArrayOf(
    R.drawable.assistant_moon_00, R.drawable.assistant_moon_01,
    R.drawable.assistant_moon_02, R.drawable.assistant_moon_03,
    R.drawable.assistant_moon_04, R.drawable.assistant_moon_05,
    R.drawable.assistant_moon_06, R.drawable.assistant_moon_07,
    R.drawable.assistant_moon_08, R.drawable.assistant_moon_09,
    R.drawable.assistant_moon_10, R.drawable.assistant_moon_11,
)

private fun assistantFrames(character: AssistantCharacter) = when (character) {
    AssistantCharacter.STAR -> starFrames
    AssistantCharacter.SUN -> sunFrames
    AssistantCharacter.MOON -> moonFrames
}

private fun cloudBubbleShape(tailOnRight: Boolean) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.10f, h * 0.28f)
    cubicTo(w * 0.03f, h * 0.28f, w * 0.02f, h * 0.43f, w * 0.08f, h * 0.48f)
    cubicTo(w * 0.02f, h * 0.57f, w * 0.06f, h * 0.74f, w * 0.16f, h * 0.74f)
    cubicTo(w * 0.18f, h * 0.84f, w * 0.30f, h * 0.86f, w * 0.36f, h * 0.79f)
    lineTo(if (tailOnRight) w * 0.72f else w * 0.28f, h * 0.79f)
    if (tailOnRight) {
        lineTo(w * 0.94f, h * 0.97f)
        lineTo(w * 0.84f, h * 0.73f)
    } else {
        lineTo(w * 0.06f, h * 0.97f)
        lineTo(w * 0.16f, h * 0.73f)
    }
    cubicTo(w * 0.95f, h * 0.73f, w * 0.98f, h * 0.57f, w * 0.92f, h * 0.48f)
    cubicTo(w * 0.98f, h * 0.39f, w * 0.95f, h * 0.26f, w * 0.87f, h * 0.25f)
    cubicTo(w * 0.85f, h * 0.12f, w * 0.71f, h * 0.08f, w * 0.63f, h * 0.16f)
    cubicTo(w * 0.56f, h * 0.03f, w * 0.39f, h * 0.04f, w * 0.33f, h * 0.16f)
    cubicTo(w * 0.25f, h * 0.07f, w * 0.12f, h * 0.13f, w * 0.10f, h * 0.28f)
    close()
}

@Composable
fun FloatingAssistant(route: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    var revision by remember { mutableIntStateOf(0) }
    val settings = context.getSharedPreferences("astro_assistant", Context.MODE_PRIVATE)
    DisposableEffect(settings) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        settings.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    revision
    if (!AssistantPrefs.enabled(context)) return
    val character = AssistantPrefs.character(context)
    val frames = assistantFrames(character)
    val assistantInteraction = remember { MutableInteractionSource() }
    val animationsEnabled = AssistantPrefs.animations(context)
    val hintsEnabled = AssistantPrefs.hints(context)
    var showHint by remember(route) { mutableStateOf(false) }
    var motionState by remember { mutableStateOf(AssistantMotion.IDLE) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var activityToken by remember { mutableIntStateOf(0) }
    val motion = rememberInfiniteTransition(label = "assistant_idle")
    val floatY by motion.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "assistant_float",
    )
    val scale by motion.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "assistant_breathe",
    )
    val hint = assistantHint(context, route)

    LaunchedEffect(character, animationsEnabled, motionState, activityToken) {
        if (!animationsEnabled) {
            frameIndex = 0
            return@LaunchedEffect
        }
        when (motionState) {
            AssistantMotion.IDLE -> {
                val frames = intArrayOf(0, 1, 2, 0, 3, 4, 5, 0)
                val durations = longArrayOf(700, 220, 220, 850, 120, 140, 180, 900)
                while (true) {
                    frames.forEachIndexed { index, frame ->
                        frameIndex = frame
                        delay(durations[index])
                    }
                }
            }
            AssistantMotion.SLEEPING -> while (true) {
                frameIndex = 6
                delay(500)
                frameIndex = 7
                delay(1_100)
            }
            AssistantMotion.REACTING -> {
                intArrayOf(8, 9, 10, 11).forEach { frame ->
                    frameIndex = frame
                    delay(320)
                }
                motionState = AssistantMotion.IDLE
            }
        }
    }

    LaunchedEffect(activityToken, animationsEnabled, character) {
        if (animationsEnabled) {
            delay(15_000)
            motionState = AssistantMotion.SLEEPING
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val assistantSize = AssistantPrefs.size(context).dp
        val maxX = with(density) { (maxWidth - assistantSize).coerceAtLeast(0.dp).toPx() }
        val maxY = with(density) { (maxHeight - assistantSize).coerceAtLeast(0.dp).toPx() }
        val bubbleWidth = 232.dp.coerceAtMost(maxWidth - 16.dp)
        val bubbleWidthPx = with(density) { bubbleWidth.toPx() }
        val bubbleGapPx = with(density) { 8.dp.toPx() }
        val bubbleLiftPx = with(density) { 112.dp.toPx() }
        var offset by remember { mutableStateOf(IntOffset.Zero) }
        var initialized by remember { mutableStateOf(false) }

        LaunchedEffect(maxX, maxY) {
            if (!initialized) {
                offset = IntOffset(
                    (AssistantPrefs.positionX(context) * maxX).roundToInt(),
                    (AssistantPrefs.positionY(context) * maxY).roundToInt(),
                )
                initialized = true
            } else {
                offset = IntOffset(offset.x.coerceIn(0, maxX.roundToInt()), offset.y.coerceIn(0, maxY.roundToInt()))
            }
        }

        if (showHint && hintsEnabled) {
            val bubbleX = if (offset.x > maxX / 2f) {
                offset.x - bubbleWidthPx - bubbleGapPx
            } else {
                offset.x + with(density) { assistantSize.toPx() } + bubbleGapPx
            }.coerceIn(8f, (with(density) { maxWidth.toPx() } - bubbleWidthPx - 8f).coerceAtLeast(8f))
            val bubbleY = (offset.y - bubbleLiftPx).coerceIn(8f, maxY.coerceAtLeast(8f))
            val tailOnRight = offset.x > maxX / 2f
            Surface(
                modifier = Modifier.widthIn(max = bubbleWidth).graphicsLayer {
                    translationX = bubbleX
                    translationY = bubbleY
                }.clickable { showHint = false },
                shape = cloudBubbleShape(tailOnRight),
                color = if (darkTheme) Color(0xFFF5F0FF) else Color(0xFFFFFBEE),
                contentColor = Color(0xFF29223D),
                border = BorderStroke(
                    1.5.dp,
                    if (darkTheme) Color(0xFFB9A5FF) else Color(0xFFD4AA55),
                ),
                tonalElevation = 2.dp,
                shadowElevation = 12.dp,
            ) {
                Text(
                    hint,
                    Modifier.padding(start = 32.dp, end = 32.dp, top = 30.dp, bottom = 42.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF29223D),
                )
            }
        }

        Image(
            painter = painterResource(
                if (animationsEnabled) {
                    frames[frameIndex.coerceIn(frames.indices)]
                } else {
                    character.imageRes
                },
            ),
            contentDescription = stringResource(character.nameRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(assistantSize)
                .graphicsLayer {
                    translationX = offset.x.toFloat()
                    translationY = offset.y.toFloat() + if (animationsEnabled) floatY else 0f
                    // The generated sprite cells are visually wider than the intended mascot.
                    // Keep the assistant compact and slightly vertical without changing its hit area.
                    scaleX = (if (animationsEnabled) scale else 1f) * 0.88f
                    scaleY = (if (animationsEnabled) scale else 1f) * 1.02f
                }
                .clickable(
                    interactionSource = assistantInteraction,
                    indication = null,
                ) {
                    activityToken++
                    motionState = AssistantMotion.REACTING
                    if (hintsEnabled) showHint = !showHint
                }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragStart = {
                            activityToken++
                            motionState = AssistantMotion.IDLE
                        },
                        onDragEnd = {
                            AssistantPrefs.setPosition(
                                context,
                                if (maxX > 0f) offset.x / maxX else 0f,
                                if (maxY > 0f) offset.y / maxY else 0f,
                            )
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offset = IntOffset(
                            (offset.x + dragAmount.x).roundToInt().coerceIn(0, maxX.roundToInt()),
                            (offset.y + dragAmount.y).roundToInt().coerceIn(0, maxY.roundToInt()),
                        )
                    }
                },
        )
    }
}

@Composable
private fun assistantHint(context: Context, route: String?): String {
    val textRes = when {
        route == "today" && TarotStorage.todayCard(context) == null -> R.string.assistant_hint_day_card
        route == "today" && !DailyNotify.isEnabled(context) -> R.string.assistant_hint_notifications
        route == "today" -> R.string.assistant_hint_today
        route == "tarot" -> R.string.assistant_hint_tarot
        route == "chart" -> R.string.assistant_hint_chart
        route == "saved" || route == "materials" -> R.string.assistant_hint_saved
        route == "account" -> R.string.assistant_hint_account
        route?.startsWith("forecast") == true || route?.startsWith("transit") == true -> R.string.assistant_hint_forecast
        route == "tools" -> R.string.assistant_hint_tools
        else -> R.string.assistant_hint_default
    }
    return stringResource(textRes)
}
