package ru.astrosmap.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.DailyNotify
import ru.astrosmap.app.data.PrimaryChart
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.LangPref
import ru.astrosmap.app.ui.assistant.AssistantPrefs
import ru.astrosmap.app.ui.tarot.TarotStorage
import ru.astrosmap.app.ui.tools.LunarTexts
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Считает данные дня офлайн (движок) и обновляет все виджеты AstroSMap: «День», «Луна»,
 * «Совет дня». Язык — как выбран в приложении (LangPref), а не системный. По завершении
 * переставляет суточную задачу на следующую полночь.
 */
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val engine: AstroEngine,
    private val dao: ChartDao,
) : CoroutineWorker(context, params) {

    private data class DayData(
        val date: String,
        val moonLine: String,
        val mood: String,
        val advice: String,
        val personal: String? = null,
    )

    override suspend fun doWork(): Result {
        // Язык виджета следует за выбором в приложении.
        val lang = LangPref.get(context)
        Locale.setDefault(Locale.forLanguageTag(lang))
        val res = localizedContext(lang)

        val mgr = AppWidgetManager.getInstance(context)
        val data = runCatching { enrich(compute()) }.getOrNull()
        if (data != null) {
            updateType(mgr, TodayWidgetProvider::class.java) { dayViews(data) }
            updateType(mgr, MoonWidgetProvider::class.java) { moonViews(data) }
            updateType(mgr, AdviceWidgetProvider::class.java) { adviceViews(res, data) }
            updateType(mgr, CalendarWidgetProvider::class.java) { calendarViews(res, data) }
            updateType(mgr, AssistantCompactWidgetProvider::class.java) { assistantCompactViews() }
            updateType(mgr, AssistantMessageWidgetProvider::class.java) { assistantMessageViews(res) }
            updateType(mgr, MonthCalendarWidgetProvider::class.java) { monthCalendarViews(res) }
        }
        WidgetUpdater.scheduleMidnight(context) // следующий показ — в полночь
        return Result.success()
    }

    private fun localizedContext(lang: String): Context {
        val cfg = Configuration(context.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(lang))
        return context.createConfigurationContext(cfg)
    }

    private fun updateType(mgr: AppWidgetManager, cls: Class<*>, build: () -> RemoteViews) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        val views = build()
        ids.forEach { mgr.updateAppWidget(it, views) }
    }

    private fun compute(): DayData {
        val today = LocalDate.now()
        val tz = ZoneId.systemDefault().id
        val sky = engine.natal(BirthInput(today.year, today.monthValue, today.dayOfMonth, 12, 0, 0.0, 0.0, tz))
        val phaseKey = sky.lunarPhase.name
        val moonSign = sky.points.first { it.name == "Moon" }.sign
        val date = today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.getDefault()))
            .replaceFirstChar { it.uppercase() }
        val moonLine = "${LunarTexts.phaseEmoji[phaseKey] ?: ""} ${LunarTexts.phaseName(phaseKey)} · ${AstroLabels.sign(moonSign)}"
        return DayData(date, moonLine, LunarTexts.moonMood(moonSign), LunarTexts.phaseAdvice(phaseKey))
    }

    private suspend fun enrich(data: DayData): DayData {
        if (!DailyNotify.cachedPremium(context)) return data
        val chart = PrimaryChart.resolve(context, dao.allOnce().filterNot { it.pendingDelete }) ?: return data
        val today = LocalDate.now()
        val natal = chart.toBirthInput()
        val transit = BirthInput(today.year, today.monthValue, today.dayOfMonth, 12, 0, natal.lat, natal.lng, natal.tzId)
        val aspect = engine.transit(natal, transit).aspects.minByOrNull { it.orbit } ?: return data
        val prefix = if (WidgetPrefs.hidePersonal(context)) "" else "${chart.name}: "
        return data.copy(
            personal = prefix + "${AstroLabels.point(aspect.p2)} ${AstroLabels.aspect(aspect.aspect)} ${AstroLabels.point(aspect.p1)}",
        )
    }

    private fun tapIntent(route: String = DailyNotify.ROUTE_TODAY, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(DailyNotify.ROUTE, route)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dayViews(d: DayData) = RemoteViews(context.packageName, R.layout.widget_today).apply {
        setTextViewText(R.id.w_date, d.date)
        setTextViewText(R.id.w_moon, d.moonLine.replace('\uFFFD', '-'))
        setTextViewText(R.id.w_mood, d.mood)
        setOnClickPendingIntent(R.id.w_root, tapIntent())
    }

    private fun moonViews(d: DayData) = RemoteViews(context.packageName, R.layout.widget_moon).apply {
        setTextViewText(R.id.wm_moon, d.moonLine.replace('\uFFFD', '-'))
        setTextViewText(R.id.wm_advice, d.advice.ifBlank { d.mood })
        setOnClickPendingIntent(R.id.wm_root, tapIntent())
    }

    private fun adviceViews(res: Context, d: DayData) = RemoteViews(context.packageName, R.layout.widget_advice).apply {
        setTextViewText(R.id.wa_title, "💡 " + res.getString(R.string.widget_advice_title))
        setTextViewText(R.id.wa_text, d.personal ?: d.advice.ifBlank { d.mood })
        setOnClickPendingIntent(R.id.wa_root, tapIntent())
    }

    private fun calendarViews(res: Context, d: DayData) = RemoteViews(context.packageName, R.layout.widget_calendar).apply {
        setTextViewText(R.id.wc_title, res.getString(R.string.widget_calendar_title))
        setTextViewText(R.id.wc_date, d.date)
        setTextViewText(R.id.wc_event, d.personal ?: res.getString(R.string.widget_calendar_general, d.moonLine.replace('\uFFFD', '-')))
        setOnClickPendingIntent(R.id.wc_root, tapIntent(DailyNotify.ROUTE_LUNAR, 4))
    }

    private fun assistantCompactViews() = RemoteViews(context.packageName, R.layout.widget_assistant_compact).apply {
        setImageViewResource(R.id.wac_character, AssistantPrefs.character(context).imageRes)
        setOnClickPendingIntent(R.id.wac_root, tapIntent(requestCode = 21))
    }

    private fun assistantMessageViews(res: Context) = RemoteViews(context.packageName, R.layout.widget_assistant_message).apply {
        setImageViewResource(R.id.wam_character, AssistantPrefs.character(context).imageRes)
        val message = when {
            TarotStorage.todayCard(context) == null -> res.getString(R.string.assistant_widget_day_card)
            !DailyNotify.isEnabled(context) -> res.getString(R.string.assistant_widget_notifications)
            java.time.LocalTime.now().hour < 12 -> res.getString(R.string.assistant_widget_morning)
            java.time.LocalTime.now().hour >= 20 -> res.getString(R.string.assistant_widget_evening)
            else -> res.getString(R.string.assistant_widget_day)
        }
        setTextViewText(R.id.wam_text, message)
        setOnClickPendingIntent(R.id.wam_root, tapIntent(requestCode = 22))
    }

    private fun monthCalendarViews(res: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_month_calendar)
        val today = LocalDate.now()
        val month = today.withDayOfMonth(1)
        val start = month.minusDays((month.dayOfWeek.value - 1).toLong())
        val locale = res.resources.configuration.locales[0]
        val title = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        views.setTextViewText(R.id.wmc_title, title)
        views.removeAllViews(R.id.wmc_weeks)
        val ids = intArrayOf(R.id.wmc_d1, R.id.wmc_d2, R.id.wmc_d3, R.id.wmc_d4, R.id.wmc_d5, R.id.wmc_d6, R.id.wmc_d7)
        repeat(6) { week ->
            val row = RemoteViews(context.packageName, R.layout.widget_month_calendar_week)
            repeat(7) { day ->
                val date = start.plusDays((week * 7L) + day)
                row.setTextViewText(ids[day], date.dayOfMonth.toString())
                row.setTextColor(ids[day], when {
                    date == today -> 0xFFC9A86A.toInt()
                    date.month != month.month -> 0xFF5F5D78.toInt()
                    day >= 5 -> 0xFF9C8FE0.toInt()
                    else -> 0xFFF2F0FA.toInt()
                })
            }
            views.addView(R.id.wmc_weeks, row)
        }
        views.setOnClickPendingIntent(R.id.wmc_root, tapIntent(DailyNotify.ROUTE_LUNAR, 23))
        return views
    }
}
