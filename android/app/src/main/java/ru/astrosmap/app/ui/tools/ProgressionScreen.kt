package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.ProgressionApiRequest
import ru.astrosmap.app.data.api.TransitDateDto
import ru.astrosmap.app.data.api.toNatalRequest
import java.time.LocalDate
import javax.inject.Inject

/** Прогрессии + дирекции на сегодня (бесплатная онлайн-техника сайта). */
@HiltViewModel
class ProgressionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = ReportState.Loading
        viewModelScope.launch {
            val entity = dao.byId(chartId) ?: return@launch
            val today = LocalDate.now()
            _state.value = loadReport {
                api.progression(
                    ProgressionApiRequest(
                        natal = entity.toNatalRequest(),
                        targetDate = TransitDateDto(today.year, today.monthValue, today.dayOfMonth),
                    ),
                )
            }
        }
    }
}

@Composable
fun ProgressionScreen(viewModel: ProgressionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    ReportScaffold(state, onRetry = viewModel::load) { data ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.tools_progression),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            // Главное: прогрессивные Луна и Солнце.
            val highlights = data.o("highlights")
            for (key in listOf("prog_moon", "prog_sun")) {
                val h = highlights?.o(key) ?: continue
                val text = h.s("text") ?: continue
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            (if (key == "prog_moon") "☽ " else "☉ ") + (h.s("sign_ru").orEmpty()),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { ToolSection(stringResource(R.string.prog_positions)) }
            items(data.a("prog_planets")) { p -> RemotePlanetRow(p) }
            item { ToolSection(stringResource(R.string.prog_aspects)) }
            items(data.a("aspects")) { a -> RemoteAspectRow(a, movingFirst = true) }
        }
    }
}

@Composable
fun ToolSection(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Аспект из серверного отчёта: символы + русские подписи + трактовка по тапу. */
@Composable
fun RemoteAspectRow(
    a: kotlinx.serialization.json.JsonObject,
    movingFirst: Boolean = false,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val interp = a.s("interp") ?: a.s("text")
    val first = if (movingFirst) "p2" else "p1"
    val second = if (movingFirst) "p1" else "p2"
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = interp != null) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(a.s("${first}_symbol") ?: "", color = MaterialTheme.colorScheme.primary)
            Text(a.s("aspect_symbol") ?: "", color = MaterialTheme.colorScheme.secondary)
            Text(a.s("${second}_symbol") ?: "", color = MaterialTheme.colorScheme.primary)
            Text(
                "${a.s("${first}_ru") ?: a.s(first)} · ${a.s("aspect_ru") ?: ""} · ${a.s("${second}_ru") ?: a.s(second)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            (a.s("orbit") ?: a.d("orbit")?.toString())?.let {
                Text("$it°", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (expanded && interp != null) {
            Text(interp, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
