package ru.astrosmap.app.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import ru.astrosmap.app.R
import ru.astrosmap.app.data.SavedMaterial
import ru.astrosmap.app.data.SavedMaterialDb
import ru.astrosmap.app.data.SavedMaterialPolicy

private const val LOCAL_OWNER = "local"

@Composable
fun SaveMaterialButton(
    sourceType: String,
    sourceId: String,
    title: String,
    body: String,
    premium: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Int?>(null) }
    TextButton(
        onClick = {
            scope.launch {
                val dao = SavedMaterialDb.get(context).dao()
                if (!SavedMaterialPolicy.canAdd(dao.count(LOCAL_OWNER), premium)) {
                    message = R.string.material_limit
                } else {
                    dao.upsert(
                        SavedMaterial(
                            sourceType = sourceType.take(32), sourceId = sourceId.take(100),
                            title = title.take(160), body = body.take(20_000),
                        ),
                    )
                    message = R.string.material_saved
                }
            }
        },
        enabled = body.isNotBlank(),
        modifier = modifier,
    ) { Text(stringResource(R.string.material_save)) }
    message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun SavedMaterialsScreen(viewModel: SavedMaterialsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val dao = remember { SavedMaterialDb.get(context).dao() }
    val all by remember { dao.observe(LOCAL_OWNER) }.collectAsState(initial = emptyList())
    val access by viewModel.access.collectAsState()
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    var editing by remember { mutableStateOf<SavedMaterial?>(null) }
    var deleting by remember { mutableStateOf<SavedMaterial?>(null) }
    val folders = remember(all) { all.map { it.folder.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }
    val visible = remember(all, query, folder) {
        all.filter { value ->
            (folder.isBlank() || value.folder == folder) &&
                SavedMaterialPolicy.matches(value, query)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ru.astrosmap.app.ui.theme.AppHeader(stringResource(R.string.materials_title))
        Text(stringResource(R.string.materials_privacy), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(if (access.pdfAllowed) R.string.material_pdf_available else R.string.material_pdf_locked),
            style = MaterialTheme.typography.bodySmall,
            color = if (access.pdfAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(100) },
            label = { Text(stringResource(R.string.material_search)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = folder.isBlank(), onClick = { folder = "" }, label = { Text(stringResource(R.string.material_all)) })
            folders.take(4).forEach { name ->
                FilterChip(selected = folder == name, onClick = { folder = name }, label = { Text(name) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visible.isEmpty()) item { Text(stringResource(R.string.material_empty)) }
            items(visible, key = { it.id }) { value ->
                val expanded = value.id in expandedIds
                ru.astrosmap.app.ui.theme.AstroPanel {
                    Text(value.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        value.body,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (value.body.isNotBlank()) {
                        TextButton(onClick = {
                            expandedIds = if (expanded) expandedIds - value.id else expandedIds + value.id
                        }) {
                            Text(stringResource(if (expanded) R.string.material_collapse else R.string.material_expand))
                        }
                    }
                    if (value.note.isNotBlank()) Text(value.note, color = MaterialTheme.colorScheme.secondary)
                    if (value.tags.isNotBlank()) Text("# " + value.tags, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { editing = value }) { Text(stringResource(R.string.material_edit)) }
                        TextButton(onClick = { deleting = value }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }

    editing?.let { current -> MaterialEditor(current, access.pdfAllowed, onClose = { editing = null }) { updated ->
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { dao.upsert(updated) }
        editing = null
    } }
    deleting?.let { current ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                Button(onClick = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { dao.delete(current.id, LOCAL_OWNER) }
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MaterialEditor(value: SavedMaterial, pdfAllowed: Boolean, onClose: () -> Unit, onSave: (SavedMaterial) -> Unit) {
    var note by remember(value.id) { mutableStateOf(value.note) }
    var tags by remember(value.id) { mutableStateOf(value.tags) }
    var folder by remember(value.id) { mutableStateOf(value.folder) }
    var pdf by remember(value.id) { mutableStateOf(value.includeInPdf) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(value.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(note, { note = it.take(2_000) }, label = { Text(stringResource(R.string.material_note)) })
                OutlinedTextField(tags, { tags = it.take(200) }, label = { Text(stringResource(R.string.material_tags)) })
                OutlinedTextField(folder, { folder = it.take(80) }, label = { Text(stringResource(R.string.material_folder)) })
                Row {
                    Checkbox(pdf && pdfAllowed, { pdf = it }, enabled = pdfAllowed)
                    Text(stringResource(if (pdfAllowed) R.string.material_pdf else R.string.material_pdf_locked))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(value.copy(note = note.trim(), tags = tags.trim(), folder = folder.trim(), includeInPdf = pdf && pdfAllowed, updatedAt = System.currentTimeMillis())) }) {
                Text(stringResource(R.string.journal_save))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
