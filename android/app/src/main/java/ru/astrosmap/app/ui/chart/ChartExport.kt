package ru.astrosmap.app.ui.chart

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import ru.astrosmap.app.astro.NatalChart
import java.io.File

/** Рендерит колесо карты в PNG и открывает системное «Поделиться». */
object ChartExport {

    fun share(context: Context, chart: NatalChart, title: String) {
        val file = renderPng(context, chart)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun renderPng(context: Context, chart: NatalChart, sizePx: Int = 1600): File {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val density = Density(density = sizePx / 400f) // масштаб шрифтов колеса
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(android.graphics.Canvas(bitmap)),
            size = Size(sizePx.toFloat(), sizePx.toFloat()),
        ) {
            drawRect(Color(0xFF0A0A1A)) // фон сайта
            drawWheel(chart, measurer)
        }

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "chart.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}
