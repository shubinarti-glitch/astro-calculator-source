package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.astro.TransitChart
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartTexts
import ru.astrosmap.app.data.ForecastLocationStore
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.api.TransitApiRequest
import ru.astrosmap.app.data.api.TransitDateDto
import ru.astrosmap.app.data.access.AccessState
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.InterpretationMode
import ru.astrosmap.app.ui.InterpretationModeSelector
import ru.astrosmap.app.ui.saved.SaveMaterialButton
import java.time.LocalDate
import javax.inject.Inject

data class TransitState(
    val title: String = "",
    val chart: TransitChart? = null,
    val aspectTexts: Map<String, String> = emptyMap(),
    val textsOffline: Boolean = false,
    val access: AccessState = AccessState(),
)

@HiltViewModel
class TransitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: android.content.Context,
    private val dao: ChartDao,
    private val engine: AstroEngine,
    private val api: AstroApi,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val _state = MutableStateFlow(TransitState())
    val state: StateFlow<TransitState> = _state

    init {
        calculate(LocalDate.now())
    }

    fun calculate(date: LocalDate) {
        viewModelScope.launch {
            val entity = dao.byId(chartId) ?: return@launch
            val natal = entity.toBirthInput()
            val location = ForecastLocationStore.get(context)
            // Транзит на полдень выбранной даты в месте рождения — как транзит дня на сайте.
            val transitInput = BirthInput(
                year = date.year, month = date.monthValue, day = date.dayOfMonth,
                hour = 12, minute = 0,
                lat = location?.lat ?: natal.lat,
                lng = location?.lng ?: natal.lng,
                tzId = location?.tzStr ?: natal.tzId,
            )
            val chart = withContext(Dispatchers.Default) { engine.transit(natal, transitInput) }
            _state.value = TransitState(
                title = "${entity.name} · ${date.dayOfMonth}.${date.monthValue}.${date.year}",
                chart = chart,
                access = runCatching { api.me().accessState() }.getOrDefault(AccessState()),
            )
            loadTexts(entity.name, natal, entity.city, date)
        }
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
            _state.value = _state.value.copy(aspectTexts = texts, textsOffline = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(textsOffline = true)
        }
    }
}

/** Транзиты на дату: позиции транзитных планет и аспекты к наталу (считается офлайн). */
@Composable
fun TransitScreen(viewModel: TransitViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val expanded = remember { mutableStateOf(setOf<String>()) }
    var mode by remember { mutableStateOf(InterpretationMode.BRIEF) }
    val chart = state.chart ?: return
    val materialBody = chart.aspects.mapNotNull { state.aspectTexts[ChartTexts.aspectKey(it.p1, it.aspect, it.p2)] }
        .take(if (state.access.premium) 12 else 3).joinToString("\n\n")

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.tools_transits) + " — " + state.title,
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
        if (mode == InterpretationMode.DETAILED && !state.access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) {
            item { TransitAccessNote(R.string.interpretation_free_preview) }
        }
        if (mode == InterpretationMode.TECHNICAL && !state.access.hasEntitlement(Entitlement.PROFESSIONAL_TOOLS)) {
            item { TransitAccessNote(R.string.interpretation_professional_preview) }
        }
        if (state.textsOffline) {
            item {
                Text(
                    stringResource(R.string.texts_offline_short),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (materialBody.isNotBlank()) item {
            SaveMaterialButton(
                sourceType = "transit", sourceId = state.title,
                title = stringResource(R.string.tools_transits) + " - " + state.title,
                body = materialBody, premium = state.access.premium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (mode == InterpretationMode.TECHNICAL) item { Section(stringResource(R.string.transit_planets)) }
        if (mode == InterpretationMode.TECHNICAL) items(chart.transitPoints) { p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(AstroLabels.pointGlyphs[p.name] ?: "", color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(AstroLabels.point(p.name))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (p.retrograde) {
                            Text("R", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${AstroLabels.signGlyphs[p.sign]} ${AstroLabels.sign(p.sign)}")
                        Text(AstroLabels.degMin(p.position))
                    }
                }
            }
        }
        item { Section(stringResource(R.string.transit_aspects)) }
        val visibleAspects = if (mode == InterpretationMode.BRIEF) chart.aspects.take(3) else chart.aspects
        items(visibleAspects) { a ->
            val key = ChartTexts.aspectKey(a.p1, a.aspect, a.p2)
            val interp = state.aspectTexts[key]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = interp != null) {
                        expanded.value = if (key in expanded.value) expanded.value - key else expanded.value + key
                    }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // p2 — транзитная планета, p1 — натальная точка.
                    Text(AstroLabels.pointGlyphs[a.p2] ?: a.p2, color = MaterialTheme.colorScheme.secondary)
                    Text(AstroLabels.aspectGlyphs[a.aspect] ?: "")
                    Text(AstroLabels.pointGlyphs[a.p1] ?: a.p1, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${AstroLabels.point(a.p2)} → ${AstroLabels.point(a.p1)}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "%.2f°".format(a.orbit),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (mode == InterpretationMode.DETAILED && key in expanded.value && interp != null) {
                    Text(interp, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun TransitAccessNote(textRes: Int) {
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
