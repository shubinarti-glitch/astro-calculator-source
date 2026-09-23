package ru.astrosmap.app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AspectHit
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.data.Analytics
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.data.ChartTexts
import ru.astrosmap.app.data.ForecastLocationStore
import ru.astrosmap.app.data.PrimaryChart
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.api.TransitApiRequest
import ru.astrosmap.app.data.api.TransitDateDto
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.ChartPicker
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import ru.astrosmap.app.ui.tools.LunarTexts
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/** Одна строка «главного за день»: аспект + трактовка (если пришла с сервера). */
data class DayAspect(val hit: AspectHit, val interp: String?)

data class TodayState(
    val calculatedAt: java.time.ZonedDateTime = java.time.ZonedDateTime.now(),
    val forecastCity: String = "",
    val loading: Boolean = true,
    val chartName: String? = null,     // null — нет сохранённых карт
    val moonPhaseKey: String = "",
    val moonSign: String = "",
    val lunarDay: Int = 1,
    val aspects: List<DayAspect> = emptyList(),
    val insights: DayInsights? = null,
    val textsOffline: Boolean = false,
    /** Все карты — для выбора «кто я»; переключатель показываем только когда их больше одной. */
    val charts: List<ChartEntity> = emptyList(),
    val chartId: Long = 0,
    val upcomingEvent: UpcomingDayEvent? = null,
    val upcomingEventLoading: Boolean = false,
)

/**
 * Экран «Сегодня» — стартовый. Отвечает на вопрос «что у меня сегодня», а не «какая у меня карта».
 *
 * Расчёт полностью офлайн (astrocore): фаза и знак Луны + главные транзиты к натальной карте.
 * Авторские трактовки подгружаются с сервера при сети — в APK их не зашиваем.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: ChartDao,
    private val engine: AstroEngine,
    private val api: AstroApi,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state
    private var loadJob: Job? = null

    init {
        analytics.track("today_viewed")
        // Реактивно: перечитываем «Сегодня» при изменении набора карт (в т.ч. при авто-сохранении
        // первой карты) — иначе личные транзиты появлялись только после перезапуска приложения.
        viewModelScope.launch {
            dao.search("")
                .distinctUntilChanged()
                .collect { list -> scheduleLoad(list.filter { !it.pendingDelete }) }
        }
    }

    /** Сменить карту «это я» — выбор запоминается между запусками. */
    fun selectChart(id: Long) {
        PrimaryChart.set(context, id)
        scheduleLoad(_state.value.charts)
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val all = runCatching { dao.allOnce() }.getOrDefault(emptyList()).filter { !it.pendingDelete }
            calculate(all)
        }
    }

    private fun scheduleLoad(charts: List<ChartEntity>) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { calculate(charts) }
    }

    private suspend fun calculate(all: List<ChartEntity>) {
            val now = java.time.ZonedDateTime.now()
            val today = now.toLocalDate()
            val moon = withContext(Dispatchers.Default) { moonOnly(now) }
            // Карта «это я»: выбранная пользователем, иначе самая ранняя.
            val chart = PrimaryChart.resolve(context, all)
            if (chart == null) {
                // Без карты показываем только лунную часть — она считается без данных рождения.
                _state.value = TodayState(
                    calculatedAt = now,
                    loading = false,
                    chartName = null,
                    moonPhaseKey = moon.first,
                    moonSign = moon.second,
                    lunarDay = moon.third,
                    charts = all,
                )
                return
            }
            val natal = chart.toBirthInput()
            val location = ForecastLocationStore.get(context)
            val transitInput = BirthInput(
                year = today.year, month = today.monthValue, day = today.dayOfMonth,
                hour = 12, minute = 0,
                lat = location?.lat ?: natal.lat,
                lng = location?.lng ?: natal.lng,
                tzId = location?.tzStr ?: natal.tzId,
            )
            val tc = withContext(Dispatchers.Default) { engine.transit(natal, transitInput) }
            val sorted = tc.aspects.sortedByDescending { strength(it) }
            val top = sorted.take(3).map { DayAspect(it, null) }
            _state.value = TodayState(
                calculatedAt = now,
                forecastCity = location?.city?.takeIf { it.isNotBlank() } ?: chart.city,
                loading = false,
                chartName = chart.name,
                moonPhaseKey = moon.first,
                moonSign = moon.second,
                lunarDay = moon.third,
                aspects = top,
                insights = DayInsightCalculator.calculate(sorted),
                charts = all,
                chartId = chart.id,
                upcomingEventLoading = true,
            )
            loadTexts(chart.name, natal, chart.city, today)
            val upcomingEvent = withContext(Dispatchers.Default) {
                val future = (1..14).map { offset ->
                    val date = today.plusDays(offset.toLong())
                    val input = BirthInput(
                        date.year, date.monthValue, date.dayOfMonth, 12, 0,
                        location?.lat ?: natal.lat,
                        location?.lng ?: natal.lng,
                        location?.tzStr ?: natal.tzId,
                    )
                    date to engine.transit(natal, input).aspects
                }
                DayForecastCalculator.nearestImportant(future)
            }
            if (_state.value.chartId == chart.id) {
                _state.value = _state.value.copy(
                    upcomingEvent = upcomingEvent,
                    upcomingEventLoading = false,
                )
            }
    }

    /** Общий лунный фон на один момент UTC, независимо от натальной карты. */
    private fun moonOnly(now: java.time.ZonedDateTime): Triple<String, String, Int> {
        val date = now.withZoneSameInstant(ZoneOffset.UTC)
        val input = BirthInput(date.year, date.monthValue, date.dayOfMonth, date.hour, date.minute, 0.0, 0.0, "UTC")
        val chart = engine.natal(input)
        val moon = chart.points.first { it.name == "Moon" }
        return Triple(chart.lunarPhase.name, moon.sign,
            ru.astrosmap.app.ui.tools.PersonalCalendarEventCalculator.lunarDay(chart.lunarPhase.degreesBetween))
    }

    private suspend fun loadTexts(name: String, natal: BirthInput, city: String, date: LocalDate) {
        try {
            val resp = api.transit(
                TransitApiRequest(
                    natal = NatalRequest(
                        name = name, year = natal.year, month = natal.month, day = natal.day,
                        hour = natal.hour, minute = natal.minute, lat = natal.lat, lng = natal.lng,
                        tzStr = natal.tzId, city = city,
                        lang = if (AstroLabels.isRu()) "ru" else "en",
                    ),
                    transitDate = TransitDateDto(date.year, date.monthValue, date.dayOfMonth),
                    transitLocation = ForecastLocationStore.get(context),
                ),
            )
            val texts = resp["aspects"]?.jsonArray.orEmpty().mapNotNull { a ->
                val o = a.jsonObject
                fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val interp = str("interp") ?: return@mapNotNull null
                ChartTexts.aspectKey(
                    str("p1") ?: return@mapNotNull null,
                    str("aspect") ?: return@mapNotNull null,
                    str("p2") ?: return@mapNotNull null,
                ) to interp
            }.toMap()
            _state.value = _state.value.copy(
                aspects = _state.value.aspects.map {
                    it.copy(interp = texts[ChartTexts.aspectKey(it.hit.p1, it.hit.aspect, it.hit.p2)])
                },
                textsOffline = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(textsOffline = true)
        }
    }

    private companion object {
        val PLANET_W = mapOf(
            "Sun" to 1.0, "Moon" to 0.6, "Mercury" to 0.8, "Venus" to 0.8, "Mars" to 0.9,
            "Jupiter" to 1.0, "Saturn" to 1.0, "Uranus" to 0.7, "Neptune" to 0.7, "Pluto" to 0.7,
        )
        val ASPECT_W = mapOf(
            "conjunction" to 1.0, "opposition" to 1.0, "square" to 0.95,
            "trine" to 0.9, "sextile" to 0.7, "quintile" to 0.4,
        )

        /** Сила аспекта дня: важность планеты × тип аспекта × точность × сходящийся/расходящийся. */
        fun strength(a: AspectHit): Double {
            val planet = PLANET_W[a.p2] ?: 0.5
            val aspect = ASPECT_W[a.aspect] ?: 0.5
            val tightness = (1.0 - (abs(a.orbit) / 10.0)).coerceIn(0.0, 1.0)
            val movement = when (a.movement) {
                "Applying" -> 1.15
                "Separating" -> 0.9
                else -> 1.0
            }
            return planet * aspect * tightness * movement
        }
    }
}

@Composable
fun TodayScreen(
    onCreateChart: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val locale = if (AstroLabels.isRu()) Locale("ru") else Locale.ENGLISH
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.load()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    val today = state.calculatedAt.toLocalDate()
    val dateLine = "%d %s, %s".format(
        today.dayOfMonth,
        today.month.getDisplayName(TextStyle.FULL, locale),
        today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.section_today))
        Text(stringResource(R.string.today_calculated_at,
            state.calculatedAt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm z"))),
            style = MaterialTheme.typography.bodySmall)
        if (state.forecastCity.isNotBlank()) {
            Text(stringResource(R.string.forecast_location_selected, state.forecastCity),
                style = MaterialTheme.typography.bodySmall)
        }

        AstroPanel {
            Text(
                dateLine.replaceFirstChar { it.uppercase(locale) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.moonPhaseKey.isNotBlank()) {
                Text(
                    "${LunarTexts.phaseEmoji[state.moonPhaseKey] ?: ""} ${LunarTexts.phaseName(state.moonPhaseKey)} · " +
                        "${AstroLabels.signGlyphs[state.moonSign] ?: ""} ${AstroLabels.sign(state.moonSign)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.today_lunar_day_estimate, state.lunarDay),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(LunarTexts.moonMood(state.moonSign), style = MaterialTheme.typography.bodyMedium)
                Text(
                    LunarTexts.phaseAdvice(state.moonPhaseKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.chartName == null) {
            if (!state.loading) {
                AstroPanel {
                    Text(
                        stringResource(R.string.today_no_chart),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onCreateChart, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.today_make_chart))
                    }
                }
                // Без натальной карты транзитов нет, но карта дня доступна всегда.
                ru.astrosmap.app.ui.tarot.CardOfDaySection()
            }
            return@Column
        }

        AstroPanel {
            Text(
                stringResource(R.string.today_main),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // Больше одной карты — даём явно выбрать, по кому считать день.
            if (state.charts.size > 1) {
                ChartPicker(
                    charts = state.charts,
                    selectedId = state.chartId,
                    onSelect = viewModel::selectChart,
                )
            } else {
                Text(
                    stringResource(R.string.today_for, state.chartName.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.aspects.isEmpty()) {
                Text(stringResource(R.string.today_quiet), style = MaterialTheme.typography.bodyMedium)
            }
            state.aspects.firstOrNull()?.let { da ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.today_main_transit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(AstroLabels.pointGlyphs[da.hit.p2] ?: "", color = MaterialTheme.colorScheme.primary)
                        Text(AstroLabels.point(da.hit.p2), style = MaterialTheme.typography.titleSmall)
                        Text(AstroLabels.aspectGlyphs[da.hit.aspect] ?: "", color = MaterialTheme.colorScheme.secondary)
                        Text(AstroLabels.point(da.hit.p1), style = MaterialTheme.typography.titleSmall)
                    }
                    da.interp?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (state.aspects.size > 1) {
                Text(
                    stringResource(R.string.today_other_influences),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                state.aspects.drop(1).forEach { da ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(AstroLabels.pointGlyphs[da.hit.p2] ?: "", color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${AstroLabels.point(da.hit.p2)} ${AstroLabels.aspectGlyphs[da.hit.aspect] ?: ""} ${AstroLabels.point(da.hit.p1)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (state.textsOffline) {
                Text(
                    stringResource(R.string.today_texts_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.insights?.let { insights ->
            AstroPanel {
                Text(
                    stringResource(R.string.today_forecast_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(
                        R.string.today_forecast_text,
                        dayDomainName(insights.strongest.domain),
                        dayDomainName(insights.mostSensitive.domain),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            AstroPanel {
                Text(
                    stringResource(R.string.today_indices),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.today_indices_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                insights.indicators.forEach { DayIndicatorRow(it) }
            }

            AstroPanel {
                AdviceBlock(
                    title = stringResource(R.string.today_useful),
                    indicator = insights.strongest,
                    textRes = R.string.today_useful_text,
                )
                AdviceBlock(
                    title = stringResource(R.string.today_protect),
                    indicator = insights.mostSensitive,
                    textRes = R.string.today_protect_text,
                )
            }
        }

        // Карта дня — под транзитами: сначала астрология дня, потом Таро.
        AstroPanel {
            Text(
                stringResource(R.string.today_next_event_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val event = state.upcomingEvent
            if (state.upcomingEventLoading) {
                Text(
                    stringResource(R.string.today_next_event_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (event == null) {
                Text(
                    stringResource(R.string.today_next_event_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val days = ChronoUnit.DAYS.between(today, event.date).toInt()
                Text(
                    "${AstroLabels.point(event.hit.p2)} " +
                        "${AstroLabels.aspectGlyphs[event.hit.aspect] ?: ""} " +
                        AstroLabels.point(event.hit.p1),
                    style = MaterialTheme.typography.titleSmall,
                )
                val dateText = "${event.date.dayOfMonth} " +
                    event.date.month.getDisplayName(TextStyle.FULL, locale)
                Text(
                    stringResource(R.string.today_next_event_date, dateText) + " · " +
                        pluralStringResource(R.plurals.today_next_event_in_days, days, days),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    stringResource(R.string.today_next_event_advice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ru.astrosmap.app.ui.tarot.CardOfDaySection()
    }
}

@Composable
private fun DayIndicatorRow(indicator: DayIndicator) {
    var expanded by remember(indicator.domain) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayDomainName(indicator.domain), style = MaterialTheme.typography.bodyMedium)
            Text("${indicator.score}/100 ${if (expanded) "−" else "+"}", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { indicator.score / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        if (expanded) {
            if (indicator.contributors.isEmpty()) {
                Text(
                    stringResource(R.string.today_no_contributors),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                indicator.contributors.forEach { hit ->
                    Text(
                        "• ${AstroLabels.point(hit.p2)} ${AstroLabels.aspectGlyphs[hit.aspect] ?: ""} ${AstroLabels.point(hit.p1)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdviceBlock(title: String, indicator: DayIndicator, textRes: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(stringResource(textRes, dayDomainName(indicator.domain)), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun dayDomainName(domain: DayDomain): String = stringResource(
    when (domain) {
        DayDomain.ENERGY -> R.string.today_domain_energy
        DayDomain.EMOTIONS -> R.string.today_domain_emotions
        DayDomain.RELATIONSHIPS -> R.string.today_domain_relationships
        DayDomain.COMMUNICATION -> R.string.today_domain_communication
        DayDomain.PRODUCTIVITY -> R.string.today_domain_productivity
    },
)
