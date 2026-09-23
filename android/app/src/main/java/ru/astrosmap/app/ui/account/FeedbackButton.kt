package ru.astrosmap.app.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.R

@Composable
internal fun FeedbackButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val version = "AstroSMap ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.STORE_ID}"
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.feedback_title)) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.feedback_title)) },
        text = { Column {
            Text(version)
            Text(stringResource(R.string.feedback_help))
            OutlinedTextField(description, { description = it.take(2000) },
                label = { Text(stringResource(R.string.feedback_description)) })
        } },
        confirmButton = { TextButton(enabled = description.isNotBlank(), onClick = {
            clipboard.setText(AnnotatedString("$version\n\n$description"))
            open = false
        }) { Text(stringResource(R.string.feedback_copy)) } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.password_close)) } },
    )
}
