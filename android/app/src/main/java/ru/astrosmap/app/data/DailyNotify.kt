package ru.astrosmap.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.tools.LunarTexts
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Ежедневное напоминание — единственный внешний повод вернуться в приложение.
 *
 * Считается офлайн движком astrocore: ни сервера, ни FCM, ни токенов устройств.
 * Сознательно ровно одно уведомление в сутки, время выбирает пользователь,
 * и в тексте никогда нет рекламы подписки.
 */
object DailyNotify {

    const val WORK_NAME = "daily-notify"
    const val CHANNEL_ID = "daily"
    const val EVENT_CHANNEL_ID = "astro-events"
    const val FROM_NOTIFICATION = "from_notification"
    const val ROUTE = "notification_route"
    const val ROUTE_TODAY = "today"
    const val ROUTE_TAROT = "tarot"
    const val ROUTE_LUNAR = "luncal"

    private const val PREFS = "settings"
    private const val KEY_ON = "notify_enabled"
    private const val KEY_HOUR = "notify_hour"
    private const val KEY_MIN = "notify_min"
    private const val KEY_PROMPT_SHOWN = "notify_prompt_shown_v1"
    private const val KEY_PREMIUM = "notify_cached_premium"
    private const val KEY_LAST_EVENT = "notify_last_event"
    const val CATEGORY_FORECAST = "notify_forecast"
    const val CATEGORY_MOON = "notify_moon"
    const val CATEGORY_TRANSIT = "notify_transit"
    const val CATEGORY_EVENTS = "notify_events"
    const val CATEGORY_TAROT = "notify_tarot"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)
    fun hour(context: Context): Int = prefs(context).getInt(KEY_HOUR, 9)
    fun minute(context: Context): Int = prefs(context).getInt(KEY_MIN, 0)
    fun category(context: Context, key: String): Boolean = prefs(context).getBoolean(key, true)
    fun cachedPremium(context: Context): Boolean = prefs(context).getBoolean(KEY_PREMIUM, false)
    fun setPremium(context: Context, premium: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREMIUM, premium).apply()
    }
    fun markEventShown(context: Context, key: String): Boolean {
        val settings = prefs(context)
        if (settings.getString(KEY_LAST_EVENT, null) == key) return false
        settings.edit().putString(KEY_LAST_EVENT, key).apply()
        return true
    }
    fun setCategory(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(key, enabled).apply()
    }
    fun shouldShowPrompt(context: Context): Boolean =
        !isEnabled(context) && !prefs(context).getBoolean(KEY_PROMPT_SHOWN, false)

    fun markPromptShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPT_SHOWN, true).apply()
    }

    fun setTime(context: Context, h: Int, m: Int) {
        prefs(context).edit().putInt(KEY_HOUR, h).putInt(KEY_MIN, m).apply()
        if (isEnabled(context)) schedule(context)
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        if (on) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Ставит задачу на ближайшее наступление выбранного времени.
     *
     * Одноразовая задача, а не периодическая: периодическая с политикой UPDATE
     * срабатывала раньше выбранного времени (WorkManager считает период от старой
     * постановки). После показа воркер сам ставит следующий день — см. DailyWorker.
     */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour(context), minute(context)))
        if (!next.isAfter(now)) next = next.plusDays(1)
        // Именно в секундах: toMinutes() отбрасывает остаток и уводит показ на минуту раньше.
        val delay = Duration.between(now, next).seconds.coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<DailyWorker>()
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notify_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notify_channel_desc) },
            )
        }
        if (mgr.getNotificationChannel(EVENT_CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    EVENT_CHANNEL_ID,
                    context.getString(R.string.notify_event_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}

class NotificationRescheduleReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ((intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) &&
            DailyNotify.isEnabled(context)
        ) DailyNotify.schedule(context)
    }
}

private data class EventNotice(val key: String, val text: String, val route: String)

@HiltWorker
class DailyWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val dao: ChartDao,
    private val engine: AstroEngine,
    private val analytics: Analytics,
    private val api: ru.astrosmap.app.data.api.AstroApi,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!DailyNotify.isEnabled(context)) return Result.success()
        ru.astrosmap.app.editorial.RemoteEditorial.refresh(api)
        val (title, text) = runCatching { buildText() }.getOrElse {
            // Сбой расчёта не роняет задачу, но должен быть виден в логах, а не проглатываться.
            Log.w("DailyWorker", "Не удалось собрать текст напоминания", it)
            return Result.success()
        }
        show(1, DailyNotify.CHANNEL_ID, title, text, DailyNotify.ROUTE_TODAY)
        if (DailyNotify.category(context, DailyNotify.CATEGORY_EVENTS)) {
            runCatching { buildEvent() }.getOrNull()
                ?.takeIf { DailyNotify.markEventShown(context, it.key) }
                ?.let { show(2, DailyNotify.EVENT_CHANNEL_ID, context.getString(R.string.notify_event_title), it.text, it.route) }
        }
        analytics.track("notif_shown")
        DailyNotify.schedule(context) // ставим завтрашний показ
        return Result.success()
    }

    /** Фаза и знак Луны на сегодня + самый точный транзит к сохранённой карте (всё офлайн). */
    private suspend fun buildText(): Pair<String, String> {
        val today = LocalDate.now()
        val tz = ZoneId.systemDefault().id
        // Луна не зависит от места — считаем на полдень в нулевой точке, как в лунном календаре.
        val sky = engine.natal(BirthInput(today.year, today.monthValue, today.dayOfMonth, 12, 0, 0.0, 0.0, tz))
        val phaseKey = sky.lunarPhase.name
        val moonSign = sky.points.first { it.name == "Moon" }.sign
        val title = context.getString(
            R.string.notify_title,
            "${LunarTexts.phaseEmoji[phaseKey] ?: ""} ${LunarTexts.phaseName(phaseKey)}",
            AstroLabels.sign(moonSign),
        )

        val parts = mutableListOf<String>()
        if (DailyNotify.category(context, DailyNotify.CATEGORY_MOON)) {
            parts += LunarTexts.moonMood(moonSign)
        }
        if (DailyNotify.category(context, DailyNotify.CATEGORY_TAROT)) {
            parts += context.getString(R.string.notify_tarot_reminder)
        }
        val chart = PrimaryChart.resolve(context, dao.allOnce().filter { !it.pendingDelete })
            ?: return title to parts.joinToString(" · ")
        val natal = chart.toBirthInput()
        val transit = BirthInput(
            year = today.year, month = today.monthValue, day = today.dayOfMonth,
            hour = 12, minute = 0, lat = natal.lat, lng = natal.lng, tzId = natal.tzId,
        )
        val strongest = engine.transit(natal, transit).aspects.minByOrNull { it.orbit }
            ?: return title to parts.joinToString(" · ")
        val line = "${AstroLabels.pointGlyphs[strongest.p2] ?: strongest.p2} " +
            "${AstroLabels.aspectGlyphs[strongest.aspect] ?: ""} " +
            "${AstroLabels.pointGlyphs[strongest.p1] ?: strongest.p1} · ${AstroLabels.aspect(strongest.aspect)}"
        if (DailyNotify.cachedPremium(context) && DailyNotify.category(context, DailyNotify.CATEGORY_TRANSIT)) parts.add(0, line)
        return title to parts.joinToString(" · ")
    }

    private fun buildEvent(): EventNotice? {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val tz = ZoneId.systemDefault().id
        fun sky(date: LocalDate) = engine.natal(
            BirthInput(date.year, date.monthValue, date.dayOfMonth, 12, 0, 0.0, 0.0, tz),
        )
        val current = sky(today)
        val next = sky(tomorrow)
        if (current.lunarPhase.name != next.lunarPhase.name) {
            return EventNotice(
                "phase:$tomorrow:${next.lunarPhase.name}",
                context.getString(R.string.notify_phase_event, LunarTexts.phaseName(next.lunarPhase.name)),
                DailyNotify.ROUTE_LUNAR,
            )
        }
        val changed = next.points.firstOrNull { point ->
            current.points.firstOrNull { it.name == point.name }?.retrograde != point.retrograde
        } ?: return null
        return EventNotice(
            "retro:$tomorrow:${changed.name}:${changed.retrograde}",
            context.getString(
                if (changed.retrograde) R.string.notify_retro_start else R.string.notify_retro_end,
                AstroLabels.point(changed.name),
            ),
            DailyNotify.ROUTE_LUNAR,
        )
    }

    private fun show(id: Int, channel: String, title: String, text: String, route: String) {
        DailyNotify.ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(DailyNotify.FROM_NOTIFICATION, true)
            .putExtra(DailyNotify.ROUTE, route)
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_chart)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
