package ru.astrosmap.app.ui.tools

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.JournalDao
import ru.astrosmap.app.data.ForecastLocationStore
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.DateDto
import ru.astrosmap.app.data.api.ForecastApiRequest
import ru.astrosmap.app.data.api.toNatalRequest
import ru.astrosmap.app.data.access.AccessState
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.ui.InterpretationMode
import ru.astrosmap.app.ui.InterpretationModeSelector
import ru.astrosmap.app.ui.saved.SaveMaterialButton
import ru.astrosmap.app.ui.theme.GoodColor
import java.time.LocalDate
import javax.inject.Inject

/** Прогноз на месяц вперёд: профекция, прогрессивная Луна, сферы, события. */
@HiltViewModel
class ForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val dao: ChartDao,
    private val journalDao: JournalDao,
    private val api: AstroApi,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state
    private val _access = MutableStateFlow(AccessState())
    val access: StateFlow<AccessState> = _access
    private val _period = MutableStateFlow(ForecastPeriod.THIS_MONTH)
    val period: StateFlow<ForecastPeriod> = _period
    private val _history = MutableStateFlow<List<ForecastSnapshot>>(emptyList())
    val history: StateFlow<List<ForecastSnapshot>> = _history
    private val _comparison = MutableStateFlow<ForecastComparison?>(null)
    val comparison: StateFlow<ForecastComparison?> = _comparison
    private val _journalCount = MutableStateFlow(0)
    val journalCount: StateFlow<Int> = _journalCount

    init {
        load()
    }

    fun load() {
        _state.value = ReportState.Loading
        viewModelScope.launch {
            _access.value = runCatching { api.me().accessState() }.getOrDefault(AccessState())
            val entity = dao.byId(chartId) ?: return@launch
            val range = _period.value.range()
            val previous = ForecastHistory.list(context, chartId).firstOrNull()?.let(ForecastHistory::payload)
            val loaded = loadReport {
                api.forecast(
                    ForecastApiRequest(
                        natal = entity.toNatalRequest(),
                        start = DateDto(range.first.year, range.first.monthValue, range.first.dayOfMonth),
                        end = DateDto(range.second.year, range.second.monthValue, range.second.dayOfMonth),
                        location = ForecastLocationStore.get(context),
                    ),
                )
            }
            _state.value = loaded
            if (loaded is ReportState.Ready) {
                _comparison.value = previous?.let { ForecastHistory.compare(it, loaded.data) }
                ForecastHistory.save(context, chartId, _period.value, range, loaded.data)
                _history.value = ForecastHistory.list(context, chartId)
                if (_access.value.hasEntitlement(Entitlement.JOURNAL_HISTORY)) {
                    val owner = context.getSharedPreferences("journal_owner", Context.MODE_PRIVATE)
                        .getString("last_owner", null) ?: "guest"
                    _journalCount.value = journalDao.since(owner, range.first.toEpochDay())
                        .count { it.epochDay <= range.second.toEpochDay() }
                }
            }
        }
    }

    fun selectPeriod(value: ForecastPeriod) {
        _period.value = value
        load()
    }

    fun openHistory(snapshot: ForecastSnapshot) {
        ForecastHistory.payload(snapshot)?.let {
            _period.value = runCatching { ForecastPeriod.valueOf(snapshot.period) }.getOrDefault(ForecastPeriod.THIS_MONTH)
            _state.value = ReportState.Ready(it)
            _comparison.value = null
        }
    }
}

@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val access by viewModel.access.collectAsState()
    val period by viewModel.period.collectAsState()
    val history by viewModel.history.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val journalCount by viewModel.journalCount.collectAsState()
    var mode by remember { mutableStateOf(InterpretationMode.BRIEF) }

    ReportScaffold(state, onRetry = viewModel::load) { data ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                ) {
                    ForecastPeriod.entries.forEach { item ->
                        FilterChip(
                            selected = period == item,
                            onClick = { viewModel.selectPeriod(item) },
                            label = { Text(stringResource(periodLabel(item))) },
                            modifier = Modifier.padding(horizontal = 3.dp),
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.tools_forecast) +
                        " · ${data.s("start").orEmpty()} — ${data.s("end").orEmpty()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                InterpretationModeSelector(
                    selected = mode,
                    onSelect = { mode = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            comparison?.let { value ->
                item {
                    ToolSection(stringResource(R.string.forecast_compare_title))
                    ForecastComparisonCard(value)
                }
            }
            if (access.hasEntitlement(Entitlement.JOURNAL_HISTORY) && journalCount > 0) {
                item { ForecastAccessNote(R.string.forecast_journal_link, journalCount) }
            }
            data.s("summary")?.let {
                item { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
                item {
                    SaveMaterialButton(
                        sourceType = "forecast",
                        sourceId = "${data.s("start").orEmpty()}_${data.s("end").orEmpty()}",
                        title = stringResource(R.string.tools_forecast),
                        body = it,
                        premium = access.premium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            if (mode == InterpretationMode.DETAILED && !access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) {
                item { ForecastAccessNote(R.string.interpretation_free_preview) }
            }
            if (mode == InterpretationMode.TECHNICAL && !access.hasEntitlement(Entitlement.PROFESSIONAL_TOOLS)) {
                item { ForecastAccessNote(R.string.interpretation_professional_preview) }
            }
            if (mode == InterpretationMode.DETAILED) for (key in listOf("profection", "progressed_moon")) {
                val text = data.o(key)?.s("text") ?: continue
                item {
                    Text(
                        text,
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (mode != InterpretationMode.TECHNICAL) item { ToolSection(stringResource(R.string.forecast_spheres)) }
            val spheres = data.a("sphere_forecast").let {
                if (mode == InterpretationMode.BRIEF || !access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) it.take(2) else it
            }
            if (mode != InterpretationMode.TECHNICAL) items(spheres) { s ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${s.s("icon").orEmpty()} ${s.s("name").orEmpty()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (s.s("tone") == "favorable") GoodColor else MaterialTheme.colorScheme.secondary,
                    )
                    s.s("text")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item { ToolSection(stringResource(R.string.forecast_events)) }
            val events = data.a("events").let {
                if (mode == InterpretationMode.BRIEF) it.take(3) else it
            }
            items(events) { e ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row {
                        Text(
                            e.s("date").orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "  ${e.s("p1_symbol").orEmpty()} ${e.s("aspect_symbol").orEmpty()} ${e.s("p2_symbol").orEmpty()}" +
                                "  ${e.s("p1_ru").orEmpty()} — ${e.s("p2_ru").orEmpty()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (mode == InterpretationMode.DETAILED) {
                        e.s("text")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (mode == InterpretationMode.TECHNICAL) {
                        val orbit = e.s("orbit") ?: e.d("orbit")?.toString()
                        orbit?.let { Text(stringResource(R.string.aspect_orb, it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { ToolSection(stringResource(R.string.forecast_history_title)) }
            val visibleHistory = if (access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) history else history.take(1)
            if (visibleHistory.isEmpty()) {
                item { ForecastAccessNote(R.string.forecast_history_empty) }
            } else items(visibleHistory, key = { it.createdAt }) { snapshot ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.openHistory(snapshot) },
                    label = { Text("${snapshot.start} — ${snapshot.end}") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
            if (!access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) {
                item { ForecastAccessNote(R.string.forecast_history_premium) }
            }
        }
    }
}

@Composable
private fun ForecastComparisonCard(value: ForecastComparison) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.forecast_compare_added, value.added))
        Text(stringResource(R.string.forecast_compare_ended, value.ended))
        Text(stringResource(R.string.forecast_compare_continued, value.continued))
        val tone = when {
            value.favorableDelta > 0 -> R.string.forecast_compare_better
            value.favorableDelta < 0 -> R.string.forecast_compare_harder
            else -> R.string.forecast_compare_stable
        }
        Text(stringResource(tone), color = MaterialTheme.colorScheme.secondary)
    }
}

private fun periodLabel(period: ForecastPeriod): Int = when (period) {
    ForecastPeriod.TODAY -> R.string.forecast_period_today
    ForecastPeriod.TOMORROW -> R.string.forecast_period_tomorrow
    ForecastPeriod.THIS_WEEK -> R.string.forecast_period_this_week
    ForecastPeriod.NEXT_WEEK -> R.string.forecast_period_next_week
    ForecastPeriod.THIS_MONTH -> R.string.forecast_period_this_month
    ForecastPeriod.NEXT_MONTH -> R.string.forecast_period_next_month
}

@Composable
private fun ForecastAccessNote(textRes: Int, vararg args: Any) {
    Text(
        stringResource(textRes, *args),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
