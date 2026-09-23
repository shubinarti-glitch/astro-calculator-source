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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.SynastryApiRequest
import ru.astrosmap.app.data.api.toNatalRequest
import ru.astrosmap.app.data.access.AccessState
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.ui.theme.GoodColor
import ru.astrosmap.app.ui.saved.SaveMaterialButton
import javax.inject.Inject

/** Синастрия двух сохранённых карт — премиум-техника сайта. */
@HiltViewModel
class SynastryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {

    private val idA: Long = savedStateHandle.get<String>("a")?.toLongOrNull() ?: 0L
    private val idB: Long = savedStateHandle.get<String>("b")?.toLongOrNull() ?: 0L

    var title = ""
        private set

    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state
    private val _access = MutableStateFlow(AccessState())
    val access: StateFlow<AccessState> = _access
    private val _relationship = MutableStateFlow(RelationshipType.PARTNERS)
    val relationship: StateFlow<RelationshipType> = _relationship
    private val _history = MutableStateFlow<List<SynastrySnapshot>>(emptyList())
    val history: StateFlow<List<SynastrySnapshot>> = _history
    private var currentData: JsonObject? = null

    init {
        load()
    }

    fun load() {
        _state.value = ReportState.Loading
        viewModelScope.launch {
            val a = dao.byId(idA) ?: return@launch
            val b = dao.byId(idB) ?: return@launch
            title = "${a.name} + ${b.name}"
            _access.value = runCatching { api.me().accessState() }.getOrDefault(AccessState())
            val request = SynastryApiRequest(a.toNatalRequest(), b.toNatalRequest())
            val loaded = loadReport {
                if (_access.value.premium) api.synastry(request) else api.synastryPreview(request)
            }
            _state.value = loaded
            if (loaded is ReportState.Ready) {
                currentData = loaded.data
                saveCurrent()
            }
        }
    }

    fun selectRelationship(value: RelationshipType) {
        _relationship.value = value
        saveCurrent()
    }

    private fun saveCurrent() {
        if (!_access.value.premium) return
        val data = currentData ?: return
        SynastryHistory.save(context, SynastrySnapshot(idA, idB, _relationship.value.name, title, System.currentTimeMillis(), data.toString()))
        _history.value = SynastryHistory.list(context)
    }

    fun openHistory(record: SynastrySnapshot) {
        val data = SynastryHistory.payload(record) ?: return
        _relationship.value = runCatching { RelationshipType.valueOf(record.relationship) }.getOrDefault(RelationshipType.PARTNERS)
        title = record.title
        currentData = data
        _state.value = ReportState.Ready(data)
    }
}

@Composable
fun SynastryScreen(viewModel: SynastryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val access by viewModel.access.collectAsState()
    val relationship by viewModel.relationship.collectAsState()
    val history by viewModel.history.collectAsState()

    ReportScaffold(state, onRetry = viewModel::load) { data ->
        val couple = data.o("couple")
        val saveSpheres = (couple?.a("spheres").orEmpty().ifEmpty { data.a("spheres") })
            .filter { relationship.showsAttraction || it.s("key") != "passion" }
        val materialBody = if (relationship.showsRawInterpretations) {
            listOfNotNull(couple?.s("verdict")) + saveSpheres.mapNotNull { it.s("text") ?: it.s("advice") }
        } else {
            saveSpheres.map { stringResource(safeToneText(it.s("tone"))) }
        }.joinToString("\n\n")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    RelationshipType.entries.forEach { type ->
                        FilterChip(
                            selected = relationship == type,
                            onClick = { viewModel.selectRelationship(type) },
                            label = { Text(stringResource(relationshipLabel(type))) },
                            modifier = Modifier.padding(horizontal = 3.dp),
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.tools_synastry) + " · " + viewModel.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (materialBody.isNotBlank()) item {
                SaveMaterialButton(
                    sourceType = "synastry", sourceId = viewModel.title,
                    title = stringResource(R.string.tools_synastry) + " - " + viewModel.title,
                    body = materialBody, premium = access.premium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            data.o("score")?.let { score ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            stringResource(
                                R.string.syn_score,
                                score.i("value") ?: 0,
                                score.s("description_ru").orEmpty(),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = GoodColor,
                        )
                    }
                }
            }
            if (relationship.showsRawInterpretations) couple?.s("verdict")?.let {
                item {
                    Text(
                        it,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { ToolSection(stringResource(R.string.syn_spheres)) }
            val spheres = (couple?.a("spheres").orEmpty().ifEmpty { data.a("spheres") })
                .filter { relationship.showsAttraction || it.s("key") != "passion" }
            items(spheres) { s ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        safeSphereLabel(s.s("key"), relationship),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (relationship.showsRawInterpretations) {
                        (s.s("text") ?: s.s("advice"))?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        Text(stringResource(safeToneText(s.s("tone"))), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (relationship.showsRawInterpretations && access.premium) {
                item { ToolSection(stringResource(R.string.syn_strengths)) }
                items(couple?.a("strengths").orEmpty()) { RemoteAspectRow(it) }
                item { ToolSection(stringResource(R.string.syn_challenges)) }
                items(couple?.a("challenges").orEmpty()) { RemoteAspectRow(it) }
                item { ToolSection(stringResource(R.string.aspects)) }
                items(data.a("aspects")) { RemoteAspectRow(it) }
            }
            if (!access.premium) item {
                Text(stringResource(R.string.syn_free_preview), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (access.premium) {
                item { ToolSection(stringResource(R.string.syn_history)) }
                items(history.take(20), key = { it.createdAt }) { record ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.openHistory(record) },
                        label = { Text(record.title + " · " + stringResource(relationshipLabel(RelationshipType.valueOf(record.relationship)))) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
            if (access.hasEntitlement(Entitlement.PROFESSIONAL_TOOLS)) item {
                Text(stringResource(R.string.syn_professional_future), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

private fun relationshipLabel(type: RelationshipType): Int = when (type) {
    RelationshipType.PARTNERS -> R.string.syn_type_partners
    RelationshipType.FAMILY -> R.string.syn_type_family
    RelationshipType.FRIENDS -> R.string.syn_type_friends
    RelationshipType.PARENT_CHILD -> R.string.syn_type_parent_child
}

@Composable
private fun safeSphereLabel(key: String?, type: RelationshipType): String = when (key) {
    "emotional" -> stringResource(R.string.syn_domain_emotions)
    "communication" -> stringResource(R.string.syn_domain_communication)
    "stability" -> stringResource(R.string.syn_domain_daily_life)
    "passion" -> stringResource(if (type == RelationshipType.PARTNERS) R.string.syn_domain_attraction else R.string.syn_domain_growth)
    else -> stringResource(R.string.syn_domain_growth)
}

private fun safeToneText(tone: String?): Int = when (tone) {
    "good" -> R.string.syn_tone_good
    "challenging" -> R.string.syn_tone_challenging
    "mixed" -> R.string.syn_tone_mixed
    else -> R.string.syn_tone_quiet
}
