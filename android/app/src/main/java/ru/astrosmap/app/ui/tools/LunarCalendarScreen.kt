package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.PrimaryChart
import ru.astrosmap.app.data.ForecastLocationStore
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.data.access.hasEntitlement
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.today.DayForecastCalculator
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** День лунного календаря: фаза и знак Луны на полдень местного времени. */
data class LunarDay(
    val day: Int,
    val phaseKey: String,
    val sign: String,
    val lunarDay: Int = 1,
    val events: List<PersonalCalendarEvent> = emptyList(),
)

@HiltViewModel
class LunarCalendarViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: ChartDao,
    private val engine: AstroEngine,
    private val api: AstroApi,
    analytics: ru.astrosmap.app.data.Analytics,
) : ViewModel() {

    var year by mutableIntStateOf(LocalDate.now().year)
        private set
    var month by mutableIntStateOf(LocalDate.now().monthValue)
        private set
    var days by mutableStateOf<List<LunarDay>>(emptyList())
        private set
    var selected by mutableStateOf<LunarDay?>(null)
    var fullCalendar by mutableStateOf(false)
        private set

    init {
        analytics.track("lunar_calendar_opened")
        load()
    }

    fun shift(delta: Int) {
        val d = LocalDate.of(year, month, 1).plusMonths(delta.toLong())
        year = d.year
        month = d.monthValue
        load()
    }

    private fun load() {
        days = emptyList()
        selected = null
        val targetMonth = YearMonth.of(year, month)
        viewModelScope.launch {
            val charts = runCatching { dao.allOnce() }.getOrDefault(emptyList()).filter { !it.pendingDelete }
            val primary = PrimaryChart.resolve(context, charts)
            fullCalendar = runCatching {
                api.me().hasEntitlement(Entitlement.FULL_CALENDAR)
            }.getOrDefault(false)
            val forecastLocation = ForecastLocationStore.get(context)
            val tz = forecastLocation?.tzStr ?: primary?.tz ?: ZoneId.systemDefault().id
            val lat = forecastLocation?.lat ?: primary?.lat ?: 0.0
            val lng = forecastLocation?.lng ?: primary?.lng ?: 0.0
            val today = LocalDate.now()
            val weekStart = today.with(java.time.DayOfWeek.MONDAY)
            val cacheKey = listOf(
                "month", targetMonth.toString(), primary?.id ?: 0L,
                forecastLocation?.city.orEmpty(),
                if (fullCalendar) "premium" else "free", weekStart.toString(),
            ).joinToString("_")
            PersonalCalendarCache.read(context, cacheKey)?.let { cached ->
                days = cached
                if (YearMonth.from(today) == targetMonth) {
                    selected = cached.getOrNull(today.dayOfMonth - 1)
                }
                return@launch
            }
            val computed = withContext(Dispatchers.Default) {
                val natal = primary?.toBirthInput()
                val natalChart = natal?.let(engine::natal)
                val firstSnapshot = targetMonth.atDay(1).minusDays(1)
                val snapshots = (0..targetMonth.lengthOfMonth() + 1).map { offset ->
                    val date = firstSnapshot.plusDays(offset.toLong())
                    val chart = engine.natal(
                        BirthInput(date.year, date.monthValue, date.dayOfMonth, 12, 0, lat, lng, tz),
                    )
                    val moon = chart.points.first { it.name == "Moon" }
                    val sun = chart.points.first { it.name == "Sun" }
                    CalendarDaySnapshot(
                        date = date,
                        lunarPhaseKey = chart.lunarPhase.name,
                        lunarAgeDegrees = chart.lunarPhase.degreesBetween,
                        moonSign = moon.sign,
                        sunLongitude = sun.absPos,
                        moonLongitude = moon.absPos,
                        retrograde = chart.points
                            .filter { it.name in CALENDAR_PLANETS }
                            .associate { it.name to it.retrograde },
                    )
                }
                val baseDays = PersonalCalendarEventCalculator.buildMonth(
                    month = targetMonth,
                    snapshots = snapshots,
                    birthMonth = primary?.month,
                    birthDay = primary?.day,
                    natalSunLongitude = natalChart?.points?.first { it.name == "Sun" }?.absPos,
                    natalMoonLongitude = natalChart?.points?.first { it.name == "Moon" }?.absPos,
                )
                baseDays.map { day ->
                    val personalEvents = if (
                        natal != null && PersonalCalendarEventCalculator.canShowPersonalTransit(
                            date = day.date,
                            today = today,
                            fullCalendar = fullCalendar,
                        )
                    ) {
                        val transitInput = BirthInput(
                            day.date.year, day.date.monthValue, day.date.dayOfMonth, 12, 0,
                            lat, lng, tz,
                        )
                        DayForecastCalculator.nearestImportant(
                            listOf(day.date to engine.transit(natal, transitInput).aspects),
                        )?.let { important ->
                            listOf(
                                PersonalCalendarEvent(
                                    date = day.date,
                                    type = CalendarEventType.PERSONAL_TRANSIT,
                                    subject = important.hit.p2,
                                    target = important.hit.p1,
                                    aspect = important.hit.aspect,
                                ),
                            )
                        }.orEmpty()
                    } else {
                        emptyList()
                    }
                    LunarDay(
                        day = day.date.dayOfMonth,
                        phaseKey = day.lunarPhaseKey,
                        sign = day.moonSign,
                        lunarDay = day.lunarDay,
                        events = day.events + personalEvents,
                    )
                }
            }
            PersonalCalendarCache.write(context, cacheKey, computed)
            days = computed
            if (YearMonth.from(today) == targetMonth) {
                selected = computed.getOrNull(today.dayOfMonth - 1)
            }
        }
    }

    private fun legacyLoad() {
        days = emptyList()
        selected = null
        val y = year
        val m = month
        viewModelScope.launch {
            val tz = ZoneId.systemDefault().id
            val computed = withContext(Dispatchers.Default) {
                (1..LocalDate.of(y, m, 1).lengthOfMonth()).map { day ->
                    // Фаза/знак не зависят от места — считаем на полдень, точка 0/0.
                    val chart = engine.natal(BirthInput(y, m, day, 12, 0, 0.0, 0.0, tz))
                    val moon = chart.points.first { it.name == "Moon" }
                    LunarDay(day, chart.lunarPhase.name, moon.sign)
                }
            }
            days = computed
            val today = LocalDate.now()
            if (today.year == y && today.monthValue == m) {
                selected = computed.getOrNull(today.dayOfMonth - 1)
            }
        }
    }

    private companion object {
        val CALENDAR_PLANETS = setOf("Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto")
    }
}

@Composable
fun LunarCalendarScreen(viewModel: LunarCalendarViewModel = hiltViewModel()) {
    val locale = if (AstroLabels.isRu()) Locale("ru") else Locale.ENGLISH
    val monthTitle = java.time.Month.of(viewModel.month)
        .getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.uppercase(locale) } + " " + viewModel.year
    var enabledGroups by remember {
        mutableStateOf(setOf(CalendarFilter.MOON, CalendarFilter.RETROGRADE, CalendarFilter.PERSONAL))
    }
    val visibleTypes = enabledGroups.flatMapTo(mutableSetOf()) { it.types }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.tools_luncal))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalendarFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter in enabledGroups,
                    onClick = {
                        enabledGroups = if (filter in enabledGroups) enabledGroups - filter else enabledGroups + filter
                    },
                    label = { Text(stringResource(filter.label)) },
                )
            }
        }

        AstroPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.shift(-1) }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                Text(
                    monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.shift(1) }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
            }

            // Шапка дней недели (Пн–Вс).
            Row(Modifier.fillMaxWidth()) {
                java.time.DayOfWeek.entries.forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val offset = LocalDate.of(viewModel.year, viewModel.month, 1).dayOfWeek.value - 1
            val cells: List<LunarDay?> = List(offset) { null } + viewModel.days
            val today = LocalDate.now()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d -> DayCell(d, viewModel, today, visibleTypes, Modifier.weight(1f)) }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }

            Text(
                stringResource(R.string.luncal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!viewModel.fullCalendar) {
                Text(
                    stringResource(R.string.luncal_free_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val upcoming = viewModel.days
            .flatMap { it.events }
            .filter { it.type in visibleTypes && !it.date.isBefore(LocalDate.now()) }
            .sortedBy { it.date }
            .take(5)
        AstroPanel {
            Text(
                stringResource(R.string.luncal_upcoming),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (upcoming.isEmpty()) {
                Text(
                    stringResource(R.string.luncal_upcoming_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                upcoming.forEach { event ->
                    Text(
                        "%02d.%02d — %s".format(
                            event.date.dayOfMonth,
                            event.date.monthValue,
                            calendarEventLabel(event),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        viewModel.selected?.let { d ->
            AstroPanel {
                Text(
                    "%02d.%02d.%d".format(d.day, viewModel.month, viewModel.year),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${LunarTexts.phaseEmoji[d.phaseKey] ?: ""} ${LunarTexts.phaseName(d.phaseKey)} · " +
                        "${AstroLabels.signGlyphs[d.sign] ?: ""} ${AstroLabels.sign(d.sign)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.luncal_lunar_day, d.lunarDay),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(LunarTexts.moonMood(d.sign), style = MaterialTheme.typography.bodyMedium)
                Text(
                    LunarTexts.phaseAdvice(d.phaseKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                d.events.filter { it.type in visibleTypes }.forEach { event ->
                    Text(
                        calendarEventLabel(event),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    d: LunarDay?,
    viewModel: LunarCalendarViewModel,
    today: LocalDate,
    visibleTypes: Set<CalendarEventType>,
    modifier: Modifier,
) {
    if (d == null) {
        Box(modifier)
        return
    }
    val isToday = today.year == viewModel.year && today.monthValue == viewModel.month && today.dayOfMonth == d.day
    val isSelected = viewModel.selected == d
    Column(
        modifier
            .padding(1.dp)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                RoundedCornerShape(8.dp),
            )
            .clickable { viewModel.selected = d }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            d.day.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(LunarTexts.phaseEmoji[d.phaseKey] ?: "", style = MaterialTheme.typography.labelSmall)
        Text(
            AstroLabels.signGlyphs[d.sign] ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (d.events.any { it.type in visibleTypes }) {
            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private enum class CalendarFilter(val label: Int, val types: Set<CalendarEventType>) {
    MOON(R.string.luncal_filter_moon, setOf(CalendarEventType.LUNAR_PHASE, CalendarEventType.MOON_SIGN)),
    RETROGRADE(
        R.string.luncal_filter_retrograde,
        setOf(CalendarEventType.RETROGRADE_START, CalendarEventType.RETROGRADE_END),
    ),
    PERSONAL(
        R.string.luncal_filter_personal,
        setOf(CalendarEventType.SOLAR_RETURN, CalendarEventType.LUNAR_RETURN, CalendarEventType.PERSONAL_TRANSIT),
    ),
}

@Composable
private fun calendarEventLabel(event: PersonalCalendarEvent): String = when (event.type) {
    CalendarEventType.LUNAR_PHASE -> stringResource(
        R.string.luncal_event_phase,
        LunarTexts.phaseName(event.subject),
    )
    CalendarEventType.MOON_SIGN -> stringResource(
        R.string.luncal_event_moon_sign,
        AstroLabels.sign(event.subject),
    )
    CalendarEventType.RETROGRADE_START -> stringResource(
        R.string.luncal_event_retro_start,
        AstroLabels.point(event.subject),
    )
    CalendarEventType.RETROGRADE_END -> stringResource(
        R.string.luncal_event_retro_end,
        AstroLabels.point(event.subject),
    )
    CalendarEventType.SOLAR_RETURN -> stringResource(R.string.luncal_event_solar)
    CalendarEventType.LUNAR_RETURN -> stringResource(R.string.luncal_event_lunar)
    CalendarEventType.PERSONAL_TRANSIT -> stringResource(
        R.string.luncal_event_personal_transit,
        AstroLabels.point(event.subject),
        AstroLabels.aspect(event.aspect.orEmpty()),
        AstroLabels.point(event.target.orEmpty()),
    )
}
