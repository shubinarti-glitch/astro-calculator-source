package ru.astrosmap.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Общая логика всех виджетов AstroSMap. Обновление считает единый воркер
 * (WidgetUpdateWorker) — сразу при добавлении и затем раз в сутки в полночь,
 * чтобы дата и суточные тексты (фаза Луны, настрой, совет) менялись вовремя.
 */
object WidgetUpdater {
    const val MIDNIGHT_WORK = "widget_midnight_refresh"

    /** Провайдеры всех типов виджетов — чтобы обновлять и проверять их разом. */
    val providers = listOf(
        TodayWidgetProvider::class.java,
        MoonWidgetProvider::class.java,
        AdviceWidgetProvider::class.java,
        CalendarWidgetProvider::class.java,
        AssistantCompactWidgetProvider::class.java,
        AssistantMessageWidgetProvider::class.java,
        MonthCalendarWidgetProvider::class.java,
    )

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
    }

    /** Одноразовая задача на ближайшую полночь; воркер по завершении ставит следующую. */
    fun scheduleMidnight(context: Context) {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(2)
        val delay = Duration.between(now, next).seconds.coerceAtLeast(60)
        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun hasAnyWidget(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        return providers.any { mgr.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }
}

/** База: все виджеты обновляются одинаково; тип различается лишь layout'ом в воркере. */
abstract class BaseAstroWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.refreshNow(context)
        WidgetUpdater.scheduleMidnight(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        // Последний виджет любого типа убрали — гасим суточную задачу.
        if (!WidgetUpdater.hasAnyWidget(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(WidgetUpdater.MIDNIGHT_WORK)
        }
    }
}

/** «День»: дата, фаза и знак Луны, настрой дня. */
class TodayWidgetProvider : BaseAstroWidget()

/** «Луна»: фаза и знак Луны + лунный совет. */
class MoonWidgetProvider : BaseAstroWidget()

/** «Совет дня»: короткая рекомендация по фазе Луны. */
class AdviceWidgetProvider : BaseAstroWidget()

/** «Календарь»: ближайшее общее или персональное событие. */
class CalendarWidgetProvider : BaseAstroWidget()

/** Выбранный помощник без текста. */
class AssistantCompactWidgetProvider : BaseAstroWidget()

/** Выбранный помощник с короткой подсказкой. */
class AssistantMessageWidgetProvider : BaseAstroWidget()

/** Полная сетка текущего месяца. */
class MonthCalendarWidgetProvider : BaseAstroWidget()

object WidgetPrefs {
    private const val PREFS = "settings"
    private const val HIDE_PERSONAL = "widget_hide_personal"
    fun hidePersonal(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(HIDE_PERSONAL, false)
    fun setHidePersonal(context: Context, hide: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(HIDE_PERSONAL, hide).apply()
        WidgetUpdater.refreshNow(context)
    }
}
