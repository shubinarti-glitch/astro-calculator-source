package ru.astrosmap.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра сайта astrosmap.ru (frontend/css/style.css).
// internal: AstroPanel всегда тёмная, поэтому внутри панелей эта палитра нужна и в светлой теме.
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFC9A86A),          // --accent (золото)
    onPrimary = Color(0xFF16132B),
    secondary = Color(0xFF8B7BD8),        // --accent-2 (фиолет)
    onSecondary = Color(0xFF0A0A1A),
    background = Color(0xFF0A0A1A),       // --bg
    onBackground = Color(0xFFECE9F5),     // --text
    surface = Color(0xFF12122B),          // --bg-2
    onSurface = Color(0xFFECE9F5),
    surfaceVariant = Color(0xFF161630),   // --panel
    onSurfaceVariant = Color(0xFF9A98B8), // --text-dim
    error = Color(0xFFE0716F),            // --bad
    outline = Color(0x2E7878C8),          // --panel-border
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6320),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A49B0),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F0EA),
    onBackground = Color(0xFF16132B),
    surface = Color(0xFFE9E6F3),
    onSurface = Color(0xFF16132B),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF514D70),
    error = Color(0xFFC0392B),
    outline = Color(0x477A6EAA),
)

val GoodColor = Color(0xFF5FC98A)   // --good: гармоничные аспекты
val NeutralColor = Color(0xFF8B9BD8) // --neutral

@Composable
fun AstroTheme(
    // Тема следует за системной. Панели и звёздный фон адаптируются (см. Decor.kt),
    // поэтому текст читается в обоих режимах — прежнего бага «тёмная панель на светлом» нет.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
