package ru.astrosmap.app.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.R

@Composable
internal fun FeedbackButton(viewModel: FeedbackViewModel = hiltViewModel()) {
    var open by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(ru.astrosmap.app.data.CrashDiagnostics.isEnabled()) }
    var settingsError by remember { mutableStateOf(false) }
    Row {
        Switch(diagnostics, { value ->
            settingsError = !ru.astrosmap.app.data.CrashDiagnostics.setEnabled(value)
            diagnostics = ru.astrosmap.app.data.CrashDiagnostics.isEnabled()
        })
        Text(stringResource(R.string.crash_opt_in))
    }
    Text(stringResource(R.string.crash_explanation), style = MaterialTheme.typography.bodySmall)
    if (settingsError) Text(stringResource(R.string.crash_settings_error), color = MaterialTheme.colorScheme.error)
    val version = "AstroSMap ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.STORE_ID}"
    TextButton(onClick = { viewModel.reset(); consent = false; open = true }) { Text(stringResource(R.string.feedback_title)) }
    if (open) AlertDialog(
        onDismissRequest = { if (!viewModel.busy) open = false },
        title = { Text(stringResource(R.string.feedback_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(version)
            Text(stringResource(R.string.report_privacy))
            OutlinedTextField(description, { description = it.take(2000) },
                enabled = !viewModel.busy && viewModel.receipt == null,
                label = { Text(stringResource(R.string.feedback_description)) })
            Row {
                Checkbox(consent, { consent = it }, enabled = !viewModel.busy && viewModel.receipt == null)
                Text(stringResource(R.string.report_consent))
            }
            if (viewModel.busy) CircularProgressIndicator()
            if (viewModel.failed) Text(stringResource(R.string.report_failed), color = MaterialTheme.colorScheme.error)
            viewModel.receipt?.let { Text(stringResource(R.string.report_received, it)) }
        } },
        confirmButton = { TextButton(
            enabled = description.trim().length >= 5 && consent && !viewModel.busy && viewModel.receipt == null,
            onClick = { viewModel.send(description, consent) }
        ) { Text(stringResource(R.string.report_send)) } },
        dismissButton = { TextButton(enabled = !viewModel.busy, onClick = {
            if (viewModel.receipt != null) description = ""
            open = false
        }) { Text(stringResource(R.string.password_close)) } },
    )
}
