package ru.astrosmap.app.ui.assistant

import android.content.Context
import ru.astrosmap.app.R

enum class AssistantCharacter(val key: String, val imageRes: Int, val nameRes: Int) {
    STAR("star", R.drawable.assistant_star, R.string.assistant_star),
    SUN("sun", R.drawable.assistant_sun, R.string.assistant_sun),
    MOON("moon", R.drawable.assistant_moon, R.string.assistant_moon),
}

object AssistantPrefs {
    private const val PREFS = "astro_assistant"
    private const val CHARACTER = "character"
    private const val ENABLED = "enabled"
    private const val ANIMATIONS = "animations"
    private const val HINTS = "hints"
    private const val POSITION_X = "position_x"
    private const val POSITION_Y = "position_y"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun character(context: Context): AssistantCharacter {
        val key = prefs(context).getString(CHARACTER, AssistantCharacter.STAR.key)
        return AssistantCharacter.entries.firstOrNull { it.key == key } ?: AssistantCharacter.STAR
    }
    fun setCharacter(context: Context, value: AssistantCharacter) = prefs(context).edit().putString(CHARACTER, value.key).apply()
    fun enabled(context: Context) = prefs(context).getBoolean(ENABLED, true)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(ENABLED, value).apply()
    fun animations(context: Context) = prefs(context).getBoolean(ANIMATIONS, true)
    fun setAnimations(context: Context, value: Boolean) = prefs(context).edit().putBoolean(ANIMATIONS, value).apply()
    fun hints(context: Context) = prefs(context).getBoolean(HINTS, true)
    fun size(context: Context) = prefs(context).getInt("size_dp", 88).coerceIn(64, 104)
    fun setSize(context: Context, value: Int) = prefs(context).edit().putInt("size_dp", value.coerceIn(64, 104)).apply()
    fun setHints(context: Context, value: Boolean) = prefs(context).edit().putBoolean(HINTS, value).apply()
    fun positionX(context: Context) = prefs(context).getFloat(POSITION_X, 0.92f).coerceIn(0f, 1f)
    fun positionY(context: Context) = prefs(context).getFloat(POSITION_Y, 0.72f).coerceIn(0f, 1f)
    fun setPosition(context: Context, x: Float, y: Float) = prefs(context).edit()
        .putFloat(POSITION_X, x.coerceIn(0f, 1f))
        .putFloat(POSITION_Y, y.coerceIn(0f, 1f))
        .apply()
}
