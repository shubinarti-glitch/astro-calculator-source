package ru.astrosmap.app.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import ru.astrosmap.app.R

@Composable
internal fun RecoveryButton(vm: AccountViewModel, initialEmail: String, profile: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    TextButton(enabled = !vm.busy, onClick = {
        email = initialEmail
        vm.resetRecovery()
        open = true
    }) { Text(stringResource(if (profile) R.string.password_reset else R.string.password_forgot)) }
    if (open) AlertDialog(
        onDismissRequest = { if (!vm.busy) open = false },
        title = { Text(stringResource(R.string.password_reset)) },
        text = {
            Column {
                if (vm.recoverySent) Text(stringResource(R.string.password_sent)) else {
                    Text(stringResource(R.string.password_help))
                    OutlinedTextField(email, { email = it.take(120) },
                        enabled = !vm.busy, singleLine = true,
                        label = { Text(stringResource(R.string.auth_email)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    vm.errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    vm.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !vm.busy && (vm.recoverySent || android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()),
                onClick = { if (vm.recoverySent) open = false else vm.recoverPassword(email) }) {
                Text(stringResource(if (vm.recoverySent) R.string.password_close else R.string.password_send))
            }
        },
        dismissButton = {
            if (!vm.recoverySent) TextButton(enabled = !vm.busy, onClick = { open = false }) {
                Text(stringResource(R.string.password_close))
            }
        },
    )
}
