package ru.astrosmap.app.ui.tarot

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Загрузка лиц карт из assets/tarot/<id>.webp.
 *
 * ponytail: без Coil — картинок 78, они мелкие (340px), грузим напрямую и кэшируем
 * в памяти процесса. Новая зависимость ради этого не нужна.
 */
object TarotImages {
    private val cache = HashMap<String, ImageBitmap>()

    fun load(context: Context, id: String): ImageBitmap? =
        cache[id] ?: runCatching {
            context.assets.open("tarot/$id.webp").use { BitmapFactory.decodeStream(it) }
                .asImageBitmap().also { cache[id] = it }
        }.getOrNull()
}

@Composable
fun rememberTarotFace(id: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(id) { TarotImages.load(context, id) }
}
