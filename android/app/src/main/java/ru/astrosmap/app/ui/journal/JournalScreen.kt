package ru.astrosmap.app.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.JournalDao
import ru.astrosmap.app.data.JournalEntry
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.PrimaryChart
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.data.access.hasEntitlement
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.astrosmap.app.ui.today.DayDomain
import ru.astrosmap.app.ui.today.DayInsightCalculator
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val dao: JournalDao,
    private val chartDao: ChartDao,
    private val engine: AstroEngine,
    private val api: AstroApi,
    analytics: ru.astrosmap.app.data.Analytics,
) : ViewModel() {
    var selectedDate by mutableStateOf(LocalDate.now())
        private set
    var mood by mutableIntStateOf(3)
    var energy by mutableIntStateOf(3)
    var relationships by mutableIntStateOf(3)
    var work by mutableIntStateOf(3)
    var wellbeing by mutableIntStateOf(3)
    var event by mutableStateOf("")
    var gratitude by mutableStateOf("")
    var note by mutableStateOf("")
    var tags by mutableStateOf("")
    var entries by mutableStateOf<List<JournalEntry>>(emptyList())
        private set
    var fullHistory by mutableStateOf(false)
        private set
    var analysis by mutableStateOf<JournalAnalytics?>(null)
        private set
    var transitMatch by mutableStateOf<TransitJournalMatch?>(null)
        private set
    var analysisLoading by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    private var ownerKey = "guest"

    init {
        analytics.track("journal_opened")
        viewModelScope.launch {
            val me = runCatching { api.me() }.getOrNull()
            val prefs = context.getSharedPreferences("journal_owner", android.content.Context.MODE_PRIVATE)
            ownerKey = me?.username?.let(::ownerHash)
                ?.also { prefs.edit().putString("last_owner", it).apply() }
                ?: prefs.getString("last_owner", null)
                ?: "guest"
            fullHistory = me?.hasEntitlement(Entitlement.JOURNAL_HISTORY) ?: false
            refresh()
            load(LocalDate.now())
        }
    }

    fun load(date: LocalDate) {
        viewModelScope.launch {
            selectedDate = date
            apply(dao.byDate(ownerKey, date.toEpochDay()))
        }
    }

    fun save() {
        viewModelScope.launch {
            dao.upsert(
                JournalEntry(
                    ownerKey = ownerKey,
                    epochDay = selectedDate.toEpochDay(),
                    mood = mood,
                    energy = energy,
                    relationships = relationships,
                    work = work,
                    wellbeing = wellbeing,
                    event = event.trim(),
                    gratitude = gratitude.trim(),
                    note = note.trim(),
                    tags = tags.trim(),
                ),
            )
            saved = true
            refresh()
        }
    }

    fun delete() {
        viewModelScope.launch {
            dao.delete(ownerKey, selectedDate.toEpochDay())
            apply(null)
            refresh()
        }
    }

    private suspend fun refresh() {
        entries = if (fullHistory) {
            dao.all(ownerKey)
        } else {
            dao.since(ownerKey, LocalDate.now().minusDays(29).toEpochDay())
        }
        analysis = if (fullHistory) JournalAnalyticsCalculator.calculate(entries) else null
        if (fullHistory && analysis != null) calculateTransitMatch() else transitMatch = null
    }

    private suspend fun calculateTransitMatch() {
        analysisLoading = true
        try {
            val charts = chartDao.allOnce().filter { !it.pendingDelete }
            val primary = PrimaryChart.resolve(context, charts) ?: run {
                transitMatch = null
                return
            }
            val natal = primary.toBirthInput()
            val sample = entries.sortedByDescending { it.epochDay }.take(30)
            val predicted = withContext(Dispatchers.Default) {
                sample.associate { entry ->
                    val date = LocalDate.ofEpochDay(entry.epochDay)
                    val input = BirthInput(date.year, date.monthValue, date.dayOfMonth, 12, 0, natal.lat, natal.lng, natal.tzId)
                    val scores = DayInsightCalculator.calculate(engine.transit(natal, input).aspects)
                        .indicators.associate { it.domain to it.score }
                    entry.epochDay to mapOf(
                        JournalMetric.MOOD to (scores[DayDomain.EMOTIONS] ?: 50),
                        JournalMetric.ENERGY to (scores[DayDomain.ENERGY] ?: 50),
                        JournalMetric.RELATIONSHIPS to (scores[DayDomain.RELATIONSHIPS] ?: 50),
                        JournalMetric.WORK to (scores[DayDomain.PRODUCTIVITY] ?: 50),
                        JournalMetric.WELLBEING to (((scores[DayDomain.ENERGY] ?: 50) + (scores[DayDomain.EMOTIONS] ?: 50)) / 2),
                    )
                }
            }
            transitMatch = JournalAnalyticsCalculator.transitMatch(sample, predicted)
        } finally {
            analysisLoading = false
        }
    }

    private fun apply(entry: JournalEntry?) {
        mood = entry?.mood ?: 3
        energy = entry?.energy ?: 3
        relationships = entry?.relationships ?: 3
        work = entry?.work ?: 3
        wellbeing = entry?.wellbeing ?: 3
        event = entry?.event.orEmpty()
        gratitude = entry?.gratitude.orEmpty()
        note = entry?.note.orEmpty()
        tags = entry?.tags.orEmpty()
        saved = false
    }
}

@Composable
fun JournalScreen(viewModel: JournalViewModel = hiltViewModel()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.journal_title))
        if (!viewModel.fullHistory) {
            Text(
                stringResource(R.string.journal_free_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AstroPanel {
            Text(formatDate(viewModel.selectedDate), style = MaterialTheme.typography.titleMedium)
            RatingRow(stringResource(R.string.journal_mood), viewModel.mood) { viewModel.mood = it }
            RatingRow(stringResource(R.string.journal_energy), viewModel.energy) { viewModel.energy = it }
            RatingRow(stringResource(R.string.journal_relationships), viewModel.relationships) {
                viewModel.relationships = it
            }
            RatingRow(stringResource(R.string.journal_work), viewModel.work) { viewModel.work = it }
            RatingRow(stringResource(R.string.journal_wellbeing), viewModel.wellbeing) { viewModel.wellbeing = it }
            JournalField(stringResource(R.string.journal_event), viewModel.event) { viewModel.event = it }
            JournalField(stringResource(R.string.journal_gratitude), viewModel.gratitude) { viewModel.gratitude = it }
            JournalField(stringResource(R.string.journal_note), viewModel.note, minLines = 3) { viewModel.note = it }
            JournalField(stringResource(R.string.journal_tags), viewModel.tags) { viewModel.tags = it }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (viewModel.saved) R.string.journal_saved else R.string.journal_save))
            }
            if (viewModel.entries.any { it.epochDay == viewModel.selectedDate.toEpochDay() }) {
                OutlinedButton(onClick = viewModel::delete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.journal_delete))
                }
            }
        }
        if (viewModel.fullHistory) {
            JournalAnalyticsPanel(
                entries = viewModel.entries,
                analysis = viewModel.analysis,
                transitMatch = viewModel.transitMatch,
                loading = viewModel.analysisLoading,
            )
        }
        AstroPanel {
            Text(stringResource(R.string.journal_history), style = MaterialTheme.typography.titleMedium)
            if (viewModel.entries.isEmpty()) {
                Text(stringResource(R.string.journal_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            viewModel.entries.forEach { entry ->
                val date = LocalDate.ofEpochDay(entry.epochDay)
                Column(
                    Modifier.fillMaxWidth().clickable { viewModel.load(date) }.padding(vertical = 8.dp),
                ) {
                    Text(formatDate(date), color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.journal_summary, entry.mood, entry.energy, entry.work),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (entry.event.isNotBlank()) Text(entry.event, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun JournalAnalyticsPanel(
    entries: List<JournalEntry>,
    analysis: JournalAnalytics?,
    transitMatch: TransitJournalMatch?,
    loading: Boolean,
) {
    AstroPanel {
        Text(stringResource(R.string.journal_analytics_title), style = MaterialTheme.typography.titleMedium)
        if (analysis == null) {
            Text(
                stringResource(R.string.journal_analytics_need, JournalAnalyticsCalculator.MIN_SAMPLE),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AstroPanel
        }
        Text(
            stringResource(R.string.journal_analytics_sample, analysis.sampleSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        JournalTrendChart(entries.sortedBy { it.epochDay }.takeLast(30))
        analysis.averages.forEach { average ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(metricName(average.metric))
                Text("%.1f / 5".format(average.value), color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            stringResource(
                R.string.journal_pattern_summary,
                metricName(analysis.strongest.metric),
                metricName(analysis.sensitive.metric),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        analysis.improving?.let {
            Text(stringResource(R.string.journal_pattern_improving, metricName(it)), color = MaterialTheme.colorScheme.secondary)
        }
        when {
            loading -> Text(stringResource(R.string.journal_transit_loading), style = MaterialTheme.typography.bodySmall)
            transitMatch == null -> Text(stringResource(R.string.journal_transit_no_chart), style = MaterialTheme.typography.bodySmall)
            transitMatch.compared < 5 -> Text(stringResource(R.string.journal_transit_need), style = MaterialTheme.typography.bodySmall)
            else -> Text(
                stringResource(R.string.journal_transit_match, transitMatch.percent, transitMatch.compared),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.journal_transit_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JournalTrendChart(entries: List<JournalEntry>) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
        if (entries.size < 2) return@Canvas
        listOf(JournalMetric.MOOD, JournalMetric.ENERGY, JournalMetric.WORK).forEachIndexed { index, metric ->
            val path = Path()
            entries.forEachIndexed { pointIndex, entry ->
                val x = pointIndex * size.width / (entries.size - 1)
                val y = size.height - ((entry.value(metric) - 1) / 4f * size.height)
                if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors[index], style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text(stringResource(R.string.journal_chart_mood), style = MaterialTheme.typography.labelSmall, color = colors[0])
        Text(stringResource(R.string.journal_chart_energy), style = MaterialTheme.typography.labelSmall, color = colors[1])
        Text(stringResource(R.string.journal_chart_work), style = MaterialTheme.typography.labelSmall, color = colors[2])
    }
}

@Composable
private fun metricName(metric: JournalMetric): String = stringResource(
    when (metric) {
        JournalMetric.MOOD -> R.string.journal_mood
        JournalMetric.ENERGY -> R.string.journal_energy
        JournalMetric.RELATIONSHIPS -> R.string.journal_relationships
        JournalMetric.WORK -> R.string.journal_work
        JournalMetric.WELLBEING -> R.string.journal_wellbeing
    },
)

@Composable
private fun RatingRow(label: String, value: Int, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { score ->
                FilterChip(
                    selected = value == score,
                    onClick = { onValue(score) },
                    label = { Text(score.toString()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun JournalField(label: String, value: String, minLines: Int = 1, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(2000)) },
        label = { Text(label) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

private fun ownerHash(username: String): String = MessageDigest.getInstance("SHA-256")
    .digest(username.trim().lowercase().toByteArray())
    .joinToString("") { "%02x".format(it) }
