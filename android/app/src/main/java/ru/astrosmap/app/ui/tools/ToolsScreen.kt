package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.data.CityStore
import ru.astrosmap.app.data.ForecastLocationStore
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    dao: ChartDao,
    private val cities: CityStore,
    private val api: ru.astrosmap.app.data.api.AstroApi,
) : ViewModel() {
    val charts = dao.search("").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Общие транзиты (общий небесный фон) — не требуют карты; при офлайне просто пусто.
    var transits by mutableStateOf<List<ru.astrosmap.app.data.api.PlanetTransit>>(emptyList())
        private set
    var locationQuery by mutableStateOf("")
        private set
    var locationSuggestions by mutableStateOf<List<CityStore.City>>(emptyList())
        private set
    var forecastLocationName by mutableStateOf(ForecastLocationStore.get(context)?.city.orEmpty())
        private set

    init {
        viewModelScope.launch {
            transits = runCatching {
                api.currentTransits(
                    lang = if (ru.astrosmap.app.ui.AstroLabels.isRu()) "ru" else "en",
                    timezone = java.time.ZoneId.systemDefault().id,
                ).transits
            }.getOrDefault(emptyList())
        }
    }

    fun onLocationQuery(value: String) {
        locationQuery = value
        viewModelScope.launch {
            locationSuggestions = withContext(Dispatchers.IO) { cities.search(value) }
        }
    }

    fun selectLocation(city: CityStore.City) {
        val label = city.label(ru.astrosmap.app.ui.AstroLabels.isRu())
        ForecastLocationStore.save(context, city, label)
        forecastLocationName = label
        locationQuery = ""
        locationSuggestions = emptyList()
    }

    fun useBirthLocation() {
        ForecastLocationStore.clear(context)
        forecastLocationName = ""
        locationQuery = ""
        locationSuggestions = emptyList()
    }
}

/** Раздел «Прогнозы»: карта + все техники. ✦ — по подписке «Премиум». */
@Composable
fun ToolsScreen(
    onTransits: (Long) -> Unit,
    onProgression: (Long) -> Unit,
    onForecast: (Long) -> Unit,
    onSolar: (Long) -> Unit,
    onLunar: (Long) -> Unit,
    onSynastry: (Long, Long) -> Unit,
    onLunarCalendar: () -> Unit,
    onTarot: () -> Unit,
    onJournal: () -> Unit,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val charts by viewModel.charts.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickPartner by remember { mutableStateOf(false) }
    // По умолчанию — та же карта «это я», что и на экране «Сегодня».
    val primary = ru.astrosmap.app.data.PrimaryChart.resolve(context, charts)
    val selected = charts.firstOrNull { it.id == selectedId } ?: primary

    if (pickPartner && selected != null) {
        AlertDialog(
            onDismissRequest = { pickPartner = false },
            title = { Text(stringResource(R.string.syn_pick_partner)) },
            text = {
                Column {
                    charts.filter { it.id != selected.id }.forEach { chart ->
                        TextButton(onClick = {
                            pickPartner = false
                            onSynastry(selected.id, chart.id)
                        }) { Text("${chart.name} · ${chart.day}.${chart.month}.${chart.year}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickPartner = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.section_tools))

        AstroPanel {
            Text(
                stringResource(R.string.forecast_location_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (viewModel.forecastLocationName.isBlank()) {
                    stringResource(R.string.forecast_location_birth)
                } else {
                    stringResource(R.string.forecast_location_selected, viewModel.forecastLocationName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = viewModel.locationQuery,
                onValueChange = viewModel::onLocationQuery,
                label = { Text(stringResource(R.string.forecast_location_choose)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.locationSuggestions.forEach { city ->
                Surface(
                    onClick = { viewModel.selectLocation(city) },
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        city.label(ru.astrosmap.app.ui.AstroLabels.isRu()),
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            if (viewModel.forecastLocationName.isNotBlank()) {
                TextButton(onClick = viewModel::useBirthLocation) {
                    Text(stringResource(R.string.forecast_location_use_birth))
                }
            }
        }

        // Лунный календарь и Таро не требуют сохранённой карты — доступны всегда.
        AstroPanel {
            ToolButton("🌙 " + stringResource(R.string.tools_luncal)) { onLunarCalendar() }
            ToolButton("✎ " + stringResource(R.string.journal_title)) { onJournal() }
            ToolButton("🔮 " + stringResource(R.string.section_tarot)) { onTarot() }
        }

        // Общий небесный фон — где сейчас планеты. Не требует карты, показываем всем.
        if (viewModel.transits.isNotEmpty()) {
            AstroPanel {
                Text(
                    "🪐 " + stringResource(R.string.sky_now_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.sky_now_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                viewModel.transits.forEach { TransitRow(it) }
            }
        }

        if (charts.isEmpty()) {
            AstroPanel {
                Text(
                    stringResource(R.string.tools_need_saved),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // Сначала выбор карты одной компактной строкой, сразу под ней — техники.
        // Списком радиокнопок при десятке карт кнопки уезжали за пределы экрана.
        AstroPanel {
            ru.astrosmap.app.ui.ChartPicker(
                charts = charts,
                selectedId = selected?.id ?: 0L,
                onSelect = { id ->
                    selectedId = id
                },
                modifier = Modifier.fillMaxWidth(),
                primaryId = primary?.id ?: 0L,
            )

            ToolButton(stringResource(R.string.tools_transits)) { selected?.let { onTransits(it.id) } }
            ToolButton(stringResource(R.string.tools_progression)) { selected?.let { onProgression(it.id) } }
            ToolButton(stringResource(R.string.tools_forecast)) { selected?.let { onForecast(it.id) } }
            ToolButton(stringResource(R.string.tools_solar) + " ✦") { selected?.let { onSolar(it.id) } }
            ToolButton(stringResource(R.string.tools_lunar) + " ✦") { selected?.let { onLunar(it.id) } }
            ToolButton(
                stringResource(R.string.tools_synastry) + " ✦",
                enabled = charts.size >= 2,
            ) { pickPartner = true }
            if (charts.size < 2) {
                Text(
                    stringResource(R.string.syn_need_two),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

/** «2026-08-23» → «23.08.2026»; пусто → «…». */
private fun fmtTransitDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "…"
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}.${p[1]}.${p[0]}" else iso
}

/** Строка планеты: знак и период всегда, значение — по тапу. */
@Composable
private fun TransitRow(t: ru.astrosmap.app.data.api.PlanetTransit) {
    var open by rememberSaveable(t.planetRu) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.planetRu, fontWeight = FontWeight.SemiBold)
            if (t.retrograde) {
                Text(" ℞", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text((t.signSymbol + " " + t.signRu).trim(), color = MaterialTheme.colorScheme.primary)
        }
        Text(
            fmtTransitDate(t.since) + " — " + fmtTransitDate(t.until),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (open) {
            Text(t.meaning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
