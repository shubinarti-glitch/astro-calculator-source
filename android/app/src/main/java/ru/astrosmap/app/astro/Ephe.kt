package ru.astrosmap.app.astro

import android.content.Context
import java.io.File

/** Копирует файл эфемерид Хирона из assets в filesDir (swisseph читает только с диска). */
object Ephe {
    fun path(context: Context): String {
        val dir = File(context.filesDir, "ephe").apply { mkdirs() }
        val target = File(dir, "seas_18.se1")
        if (!target.exists()) {
            context.assets.open("ephe/seas_18.se1").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        return dir.absolutePath
    }
}
