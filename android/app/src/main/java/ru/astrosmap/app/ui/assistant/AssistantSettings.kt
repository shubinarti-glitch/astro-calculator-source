package ru.astrosmap.app.ui.assistant

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.astrosmap.app.R

@Composable
fun AssistantSettings(context: Context) {
    var selected by remember { mutableStateOf(AssistantPrefs.character(context)) }
    var enabled by remember { mutableStateOf(AssistantPrefs.enabled(context)) }
    var animations by remember { mutableStateOf(AssistantPrefs.animations(context)) }
    var hints by remember { mutableStateOf(AssistantPrefs.hints(context)) }
    var size by remember { mutableStateOf(AssistantPrefs.size(context)) }

    Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.assistant_choose), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        AssistantCharacter.entries.forEach { character ->
            val active = selected == character
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(character.imageRes),
                    contentDescription = stringResource(character.nameRes),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(82.dp).clip(CircleShape)
                        .border(BorderStroke(if (active) 3.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                        .clickable { selected = character; AssistantPrefs.setCharacter(context, character) },
                )
                Text(stringResource(character.nameRes), style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(stringResource(R.string.assistant_size))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(64, 88, 104).forEach { value ->
            androidx.compose.material3.FilterChip(
                selected = size == value,
                onClick = { size = value; AssistantPrefs.setSize(context, value) },
                label = { Text(stringResource(when (value) {
                    64 -> R.string.assistant_size_small
                    88 -> R.string.assistant_size_medium
                    else -> R.string.assistant_size_large
                })) },
            )
        }
    }
    AssistantSwitch(stringResource(R.string.assistant_show), enabled) {
        enabled = it; AssistantPrefs.setEnabled(context, it)
    }
    AssistantSwitch(stringResource(R.string.assistant_animations), animations, enabled) {
        animations = it; AssistantPrefs.setAnimations(context, it)
    }
    AssistantSwitch(stringResource(R.string.assistant_hints), hints, enabled) {
        hints = it; AssistantPrefs.setHints(context, it)
    }
}

@Composable
private fun AssistantSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked && enabled, enabled = enabled, onCheckedChange = onChange)
    }
}
