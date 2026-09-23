package ru.astrosmap.app.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Язык интерфейса: явный выбор пользователя либо RU для русской системы, EN для остальных. */
object LangPref {
    private const val PREFS = "settings"
    private const val KEY = "lang" // "ru" | "en" | отсутствует = система

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: if (context.resources.configuration.locales[0]?.language == "ru") "ru" else "en"

    fun set(context: Context, lang: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (lang == null) remove(KEY) else putString(KEY, lang)
            apply()
        }
    }

    /** Оборачивает контекст выбранной локалью. Вызывается в MainActivity.attachBaseContext. */
    fun wrap(base: Context): Context {
        val lang = get(base)
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale) // AstroLabels.isRu() читает Locale.getDefault()
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
