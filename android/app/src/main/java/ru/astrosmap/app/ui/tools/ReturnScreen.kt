package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ForecastLocationStore
import ru.astrosmap.app.data.RemoteChart
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.ReturnApiRequest
import ru.astrosmap.app.data.api.toNatalRequest
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.chart.ChartWheel
import java.time.LocalDate
import javax.inject.Inject

/** Соляр (год) или лунар (месяц) — серверный расчёт, рисуем своим колесом. */
@HiltViewModel
class ReturnViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: android.content.Context,
    private val dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    val isLunar: Boolean = savedStateHandle.get<String>("type") == "lunar"

    var year = LocalDate.now().year
        private set
    var month = LocalDate.now().monthValue
        private set

    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state
    private val _comparison = MutableStateFlow<SolarComparison?>(null)
    val comparison: StateFlow<SolarComparison?> = _comparison

    init {
        load()
    }

    fun shift(delta: Int) {
        if (isLunar) {
            val d = LocalDate.of(year, month, 1).plusMonths(delta.toLong())
            year = d.year
            month = d.monthValue
        } else {
            year += delta
        }
        load()
    }

    fun load() {
        _state.value = ReportState.Loading
        viewModelScope.launch {
            val entity = dao.byId(chartId) ?: return@launch
            val loaded = loadReport {
                api.solarReturn(
                    ReturnApiRequest(
                        natal = entity.toNatalRequest(),
                        year = year,
                        month = if (isLunar) month else null,
                        returnType = if (isLunar) "Lunar" else "Solar",
                        location = ForecastLocationStore.get(context),
                    ),
                )
            }
            _state.value = loaded
            _comparison.value = null
            if (!isLunar && loaded is ReportState.Ready) {
                val previous = loadReport {
                    api.solarReturn(
                        ReturnApiRequest(
                            natal = entity.toNatalRequest(),
                            year = year - 1,
                            returnType = "Solar",
                            location = ForecastLocationStore.get(context),
                        ),
                    )
                }
                if (previous is ReportState.Ready) {
                    _comparison.value = compareSolars(previous.data, loaded.data)
                }
            }
        }
    }
}

data class SolarComparison(val changedThemes: Int, val aspectDelta: Int, val previousYear: Int)

private fun compareSolars(previous: JsonObject, current: JsonObject): SolarComparison {
    val keys = listOf("overlay", "tone", "focus", "mood", "lord")
    val oldTheme = previous.o("theme")
    val newTheme = current.o("theme")
    return SolarComparison(
        changedThemes = keys.count { oldTheme?.s(it) != newTheme?.s(it) },
        aspectDelta = current.a("aspects").size - previous.a("aspects").size,
        previousYear = previous.s("period_start")?.take(4)?.toIntOrNull() ?: 0,
    )
}

@Composable
fun ReturnScreen(viewModel: ReturnViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val title = stringResource(if (viewModel.isLunar) R.string.tools_lunar else R.string.tools_solar)
    val expanded = remember { mutableStateOf(setOf<String>()) }

    ReportScaffold(state, onRetry = viewModel::load) { data ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.shift(-1) }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                    Text(
                        title + " · " + (data.s("period_start")?.take(10) ?: periodLabel(viewModel)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.shift(1) }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
                }
            }
            item {
                RemoteChart.parse(data)?.let { chart ->
                    ChartWheel(
                        chart = chart,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
                    )
                }
            }

            // Тема периода — те же 5 карточек, что на сайте (overlay/tone/focus/mood/lord).
            val theme = data.o("theme")
            if (theme != null) {
                themeCard("⭐", R.string.ret_overlay, theme.s("overlay"))
                themeCard("🎯", R.string.ret_tone, theme.s("tone"))
                themeCard("🏠", R.string.ret_focus, theme.s("focus"))
                themeCard("🌙", R.string.ret_mood, theme.s("mood"))
                themeCard("👑", R.string.ret_lord, theme.s("lord"))
            } else {
                // Старый формат ответа: theme — строка.
                data.s("theme")?.let { t ->
                    item { Text(t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
            }

            // Период действия карты.
            data.s("period_start")?.let { periodRow(R.string.ret_period_start, it) }
            data.s("period_end")?.let { periodRow(R.string.ret_period_end, it) }

            comparison?.let { value ->
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.solar_compare_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.solar_compare_previous, value.previousYear))
                        Text(stringResource(R.string.solar_compare_themes, value.changedThemes))
                        Text(stringResource(R.string.solar_compare_aspects, value.aspectDelta))
                    }
                }
            }

            // Большая тройка возвращения.
            val big = data.o("big_three")
            if (big != null) {
                item { ReturnSectionTitle(stringResource(R.string.big_three)) }
                listOf(
                    "sun" to "Sun", "moon" to "Moon", "asc" to "Ascendant",
                ).forEach { (key, point) ->
                    val text = big.o(key)?.s("text") ?: return@forEach
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(
                                "${AstroLabels.pointGlyphs[point] ?: ""} ${AstroLabels.point(point)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item { ReturnSectionTitle(stringResource(R.string.planets)) }
            items(data.a("planets")) { p -> RemotePlanetRow(p) }

            // Дома возвращения.
            val houses = data.a("houses")
            if (houses.isNotEmpty()) {
                item { ReturnSectionTitle(stringResource(R.string.houses)) }
                items(houses) { h -> RemoteHouseRow(h) }
            }

            // Аспекты — по тапу раскрывается трактовка (interp приходит с сервера).
            val aspects = data.a("aspects")
            if (aspects.isNotEmpty()) {
                item { ReturnSectionTitle(stringResource(R.string.aspects)) }
                item {
                    Text(
                        stringResource(R.string.tap_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(aspects) { a -> RemoteAspectRow(a, expanded) }
            }
        }
    }
}

private fun periodLabel(vm: ReturnViewModel): String =
    if (vm.isLunar) "%02d.%d".format(vm.month, vm.year) else vm.year.toString()

/** "2026-03-21T05:33:00+03:00" → "21.03.2026 05:33". */
private fun fmtDateTime(iso: String): String {
    val date = iso.take(10).split("-")
    val time = iso.drop(11).take(5)
    return if (date.size == 3) "${date[2]}.${date[1]}.${date[0]} $time" else iso
}

private fun androidx.compose.foundation.lazy.LazyListScope.themeCard(emoji: String, titleRes: Int, text: String?) {
    if (text.isNullOrBlank()) return
    item {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                "$emoji ${stringResource(titleRes)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.periodRow(titleRes: Int, iso: String) {
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(fmtDateTime(iso), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReturnSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Строка дома из серверного отчёта. */
@Composable
fun RemoteHouseRow(h: JsonObject) {
    val num = h.i("house_num") ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.house_short, num), Modifier.weight(1f))
        val sign = h.s("sign") ?: ""
        Text("${AstroLabels.signGlyphs[sign] ?: ""} ${if (AstroLabels.isRu()) h.s("sign_ru") ?: "" else AstroLabels.sign(sign)}")
        Text("${h.i("deg") ?: 0}°${(h.i("min") ?: 0).toString().padStart(2, '0')}′")
    }
}

/** Строка аспекта из серверного отчёта; тап раскрывает трактовку interp. */
@Composable
fun RemoteAspectRow(a: JsonObject, expanded: MutableState<Set<String>>) {
    val p1 = a.s("p1") ?: return
    val p2 = a.s("p2") ?: return
    val kind = a.s("aspect") ?: return
    val interp = a.s("interp")
    val key = "raspect:$p1|$kind|$p2"
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !interp.isNullOrBlank()) {
                expanded.value = if (key in expanded.value) expanded.value - key else expanded.value + key
            }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(AstroLabels.pointGlyphs[p1] ?: a.s("p1_symbol") ?: p1, color = MaterialTheme.colorScheme.primary)
            Text(AstroLabels.aspectGlyphs[kind] ?: a.s("aspect_symbol") ?: "", color = MaterialTheme.colorScheme.secondary)
            Text(AstroLabels.pointGlyphs[p2] ?: a.s("p2_symbol") ?: p2, color = MaterialTheme.colorScheme.primary)
            Text(
                if (AstroLabels.isRu()) a.s("aspect_ru") ?: kind else AstroLabels.aspect(kind),
                Modifier.weight(1f),
            )
            a.d("orbit")?.let {
                Text(
                    "%.2f°".format(it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!interp.isNullOrBlank()) {
                Text(if (key in expanded.value) "⌄" else "›", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (key in expanded.value && !interp.isNullOrBlank()) {
            Text(interp, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Строка позиции из серверного отчёта — поля *_ru уже локализованы бэкендом. */
@Composable
fun RemotePlanetRow(p: JsonObject) {
    val name = p.s("name") ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(AstroLabels.pointGlyphs[name] ?: p.s("symbol") ?: "", color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(if (AstroLabels.isRu()) p.s("name_ru") ?: name else AstroLabels.point(name))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.b("retrograde")) {
                    Text("R", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                val sign = p.s("sign") ?: ""
                Text("${AstroLabels.signGlyphs[sign] ?: ""} ${if (AstroLabels.isRu()) p.s("sign_ru") ?: "" else AstroLabels.sign(sign)}")
                Text("${p.i("deg") ?: 0}°${(p.i("min") ?: 0).toString().padStart(2, '0')}′")
                p.i("house_num")?.let {
                    Text(stringResource(R.string.house_short, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
