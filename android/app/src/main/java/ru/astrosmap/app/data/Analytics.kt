package ru.astrosmap.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.EventBatch
import ru.astrosmap.app.data.api.EventIn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обезличенная продуктовая аналитика: воронка и возвраты.
 *
 * Никаких персональных данных — только случайный device_id и имя события.
 * Отправка «выстрелил и забыл»: сбой сети никогда не должен мешать пользователю.
 *
 * ponytail: события уходят по одному сразу. Батчинг с очередью на диске —
 * когда объём вырастет настолько, что это станет заметно по трафику/батарее.
 */
@Singleton
class Analytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AstroApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }
    }

    fun track(name: String, props: Map<String, String>? = null) {
        scope.launch {
            runCatching { api.events(EventBatch(deviceId, listOf(EventIn(name, props)))) }
        }
    }

    private companion object {
        const val KEY_DEVICE = "device_id"
    }
}
