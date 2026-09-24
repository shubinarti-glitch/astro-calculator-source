package ru.astrosmap.app.data

import android.content.Context

/**
 * Какая карта считается «моей»: по ней строится экран «Сегодня» и подставляется
 * в «Прогнозы». Раньше бралась последняя изменённая — это оказывалась чужая карта.
 *
 * ponytail: хранится в тех же SharedPreferences, что язык и напоминание —
 * миграция Room ради одного числа не нужна.
 */
object PrimaryChart {

    private const val PREFS = "settings"
    private const val KEY = "primary_chart_id"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): Long = prefs(context).getLong(KEY, 0L)

    fun set(context: Context, id: Long) {
        prefs(context).edit().putLong(KEY, id).apply()
    }

    /**
     * Выбранная карта, а если выбора нет или карта удалена — самая ранняя.
     * Именно ранняя, а не последняя правленая: первую карту человек заводит на себя.
     */
    fun resolve(context: Context, charts: List<ChartEntity>): ChartEntity? {
        if (charts.isEmpty()) return null
        val chosen = get(context)
        charts.firstOrNull { it.id == chosen }?.let { return it }
        return charts.minByOrNull { it.id }?.also { fallback -> set(context, fallback.id) }
    }
}
