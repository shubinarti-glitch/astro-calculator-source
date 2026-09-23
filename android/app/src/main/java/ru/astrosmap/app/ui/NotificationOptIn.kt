package ru.astrosmap.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import ru.astrosmap.app.R
import ru.astrosmap.app.data.DailyNotify

/** Однократное понятное приглашение вместо спрятанного переключателя в кабинете. */
@Composable
fun NotificationOptInPrompt() {
    val context = LocalContext.current
    var showOffer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            DailyNotify.setEnabled(context, true)
        } else {
            showSettings = true
        }
    }

    LaunchedEffect(Unit) {
        if (DailyNotify.shouldShowPrompt(context)) {
            delay(1_500)
            showOffer = true
        }
    }

    fun dismissOffer() {
        DailyNotify.markPromptShown(context)
        showOffer = false
    }

    fun enable() {
        DailyNotify.markPromptShown(context)
        showOffer = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DailyNotify.setEnabled(context, true)
        }
    }

    if (showOffer) {
        AlertDialog(
            onDismissRequest = ::dismissOffer,
            title = { Text(stringResource(R.string.notify_offer_title)) },
            text = { Text(stringResource(R.string.notify_offer_text)) },
            confirmButton = {
                TextButton(onClick = ::enable) {
                    Text(stringResource(R.string.notify_offer_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismissOffer) {
                    Text(stringResource(R.string.notify_offer_later))
                }
            },
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.notify_settings_title)) },
            text = { Text(stringResource(R.string.notify_denied)) },
            confirmButton = {
                TextButton(onClick = {
                    showSettings = false
                    // Расписание уже готово: если пользователь разрешит уведомления в настройках,
                    // напоминание начнёт работать без второго скрытого переключателя.
                    DailyNotify.setEnabled(context, true)
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                }) { Text(stringResource(R.string.notify_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(stringResource(R.string.notify_offer_later))
                }
            },
        )
    }
}
