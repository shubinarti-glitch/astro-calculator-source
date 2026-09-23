package ru.astrosmap.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.astrosmap.app.R

enum class InterpretationMode(@StringRes val titleRes: Int) {
    BRIEF(R.string.interpretation_brief),
    DETAILED(R.string.interpretation_detailed),
    TECHNICAL(R.string.interpretation_technical),
}

/** Единый переключатель для натальной карты и прогнозных экранов. */
@Composable
fun InterpretationModeSelector(
    selected: InterpretationMode,
    onSelect: (InterpretationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InterpretationMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(mode.titleRes)) },
            )
        }
    }
}
