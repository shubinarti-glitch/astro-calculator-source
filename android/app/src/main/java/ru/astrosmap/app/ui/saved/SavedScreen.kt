package ru.astrosmap.app.ui.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.data.PrimaryChart
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.ui.openSite
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(
    dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {
    val query = MutableStateFlow("")
    val charts = query.flatMapLatest { dao.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Мягкий баннер: был премиум и вышел. Без блокировки карт (расчёты офлайн, свои).
    var premiumExpired by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            premiumExpired = runCatching { api.me().premiumExpired() }.getOrDefault(false)
        }
    }
}

/** Список сохранённых карт с локальным поиском по имени и городу. */
@Composable
fun SavedScreen(
    onOpen: (Long) -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val charts by viewModel.charts.collectAsState()
    val context = LocalContext.current
    var primaryId by remember { mutableStateOf(PrimaryChart.get(context)) }

    LaunchedEffect(charts) {
        primaryId = PrimaryChart.resolve(context, charts)?.id ?: 0L
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (viewModel.premiumExpired) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.premium_expired_banner),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ru.astrosmap.app.BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) {
                        TextButton(
                            onClick = { openSite(context, "https://astrosmap.ru/#premium") },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.premium_renew)) }
                    }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (charts.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.saved_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
                items(charts, key = ChartEntity::id) { chart ->
                    ChartRow(
                        chart = chart,
                        isPrimary = chart.id == primaryId,
                        onMakePrimary = {
                            PrimaryChart.set(context, chart.id)
                            primaryId = chart.id
                        },
                        onClick = { onOpen(chart.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartRow(
    chart: ChartEntity,
    isPrimary: Boolean,
    onMakePrimary: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(chartInitials(chart.name), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(chart.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${chart.day.toString().padStart(2, '0')}.${chart.month.toString().padStart(2, '0')}.${chart.year} " +
                        "${chart.hour.toString().padStart(2, '0')}:${chart.minute.toString().padStart(2, '0')} · ${chart.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onMakePrimary, enabled = !isPrimary) {
                Text(
                    stringResource(
                        if (isPrimary) R.string.chart_this_is_me else R.string.chart_make_primary,
                    ),
                )
            }
        }
    }
}

internal fun chartInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return words.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
}

@Composable
private fun LegacyChartRow(chart: ChartEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(chart.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${chart.day.toString().padStart(2, '0')}.${chart.month.toString().padStart(2, '0')}.${chart.year} " +
                    "${chart.hour.toString().padStart(2, '0')}:${chart.minute.toString().padStart(2, '0')} · ${chart.city}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
