package ru.astrosmap.app.data

import android.content.Context
import ru.astrosmap.app.data.api.TransitLocationDto

/** Выбранное пользователем место пребывания. GPS и история перемещений не используются. */
object ForecastLocationStore {
    private const val PREFS = "forecast_location"

    fun get(context: Context): TransitLocationDto? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return null
        return TransitLocationDto(
            lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null,
            lng = prefs.getString("lng", null)?.toDoubleOrNull() ?: return null,
            tzStr = prefs.getString("tz", null),
            city = prefs.getString("city", "").orEmpty(),
        )
    }

    fun save(context: Context, city: CityStore.City, label: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true)
            .putString("lat", city.lat.toString())
            .putString("lng", city.lng.toString())
            .putString("tz", city.tz)
            .putString("city", label)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
