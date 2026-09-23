package ru.astrosmap.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartEntity

/**
 * Компактный выбор карты: «Карта: Имя ▾».
 *
 * Списком радиокнопок это занимало экран целиком — при десятке сохранённых карт
 * всё остальное уезжало вниз.
 */
@Composable
fun ChartPicker(
    charts: List<ChartEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    primaryId: Long = selectedId,
) {
    var open by remember { mutableStateOf(false) }
    val current = charts.firstOrNull { it.id == selectedId } ?: charts.firstOrNull() ?: return

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.picker_chart),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { open = true }) {
            Text("${current.name} ▾", style = MaterialTheme.typography.labelLarge)
        }
        if (current.id == primaryId) {
            Text(
                stringResource(R.string.chart_this_is_me),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            charts.forEach { chart ->
                DropdownMenuItem(
                    leadingIcon = {
                        if (chart.id == primaryId) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    text = {
                        Text(
                            "${chart.name} · ${chart.day}.${chart.month}.${chart.year}",
                            color = if (chart.id == current.id) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(chart.id)
                    },
                )
            }
        }
    }
}
