package ru.astrosmap.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Офлайн-справочник городов (GeoNames cities15000, 34 тыс. городов, RU/EN имена,
 * IANA-таймзоны). Готовая SQLite копируется из assets — Room тут не нужен.
 */
class CityStore(private val context: Context) {

    data class City(
        val nameRu: String?,
        val nameEn: String,
        val country: String,
        val lat: Double,
        val lng: Double,
        val tz: String,
    ) {
        /** Подпись в интерфейсе: «Москва, RU». */
        fun label(ruLocale: Boolean): String =
            "${if (ruLocale) nameRu ?: nameEn else nameEn}, $country"
    }

    private val db: SQLiteDatabase by lazy {
        // openFd для сжатых assets не работает — копируем при первом запуске.
        // Версионированное имя гарантирует замену asset-базы у существующих установок.
        // При следующем обновлении справочника увеличить суффикс.
        val target = File(File(context.filesDir, "db").apply { mkdirs() }, "cities_v2.db")
        if (!target.exists()) {
            context.assets.open("db/cities.db").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun search(query: String, limit: Int = 8): List<City> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return db.rawQuery(
            """SELECT name_ru, name_en, country, lat, lng, tz FROM cities
               WHERE search_ru LIKE ?||'%' OR search_en LIKE ?||'%'
               ORDER BY population DESC LIMIT ?""",
            arrayOf(q, q, limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(City(c.getString(0), c.getString(1), c.getString(2), c.getDouble(3), c.getDouble(4), c.getString(5)))
                }
            }
        }
    }

    /** Таймзона ближайшего города — для профилей сайта без tz_str. Приближение по плоскости. */
    fun nearestTz(lat: Double, lng: Double): String? =
        db.rawQuery(
            """SELECT tz FROM cities
               ORDER BY (lat - ?) * (lat - ?) + (lng - ?) * (lng - ?) LIMIT 1""",
            arrayOf(lat.toString(), lat.toString(), lng.toString(), lng.toString()),
        ).use { c -> if (c.moveToNext()) c.getString(0) else null }
}
