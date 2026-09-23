package ru.astrosmap.app.ui.account

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.R
import androidx.core.content.ContextCompat
import ru.astrosmap.app.data.DailyNotify
import ru.astrosmap.app.data.api.MeResponse
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.LangPref
import ru.astrosmap.app.ui.PRIVACY_URL
import ru.astrosmap.app.ui.TERMS_URL
import ru.astrosmap.app.ui.openSite
import ru.astrosmap.app.ui.theme.GoodColor

/** Кабинет: вход/регистрация, а после входа — профиль и статус подписки. */
@Composable
fun AccountScreen(onMaterials: () -> Unit = {}, viewModel: AccountViewModel = hiltViewModel()) {
    when (val s = viewModel.state) {
        AccountState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AccountState.LoggedOut -> AuthForm(viewModel, onMaterials)
        AccountState.Offline -> OfflineNote(viewModel)
        is AccountState.LoggedIn -> Profile(s.me, viewModel, onMaterials)
    }
}

@Composable
private fun AuthForm(viewModel: AccountViewModel, onMaterials: () -> Unit) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ru.astrosmap.app.ui.theme.AppHeader(stringResource(R.string.section_account))
        ru.astrosmap.app.ui.theme.AstroPanel {
        Text(
            stringResource(if (registerMode) R.string.auth_register else R.string.auth_login),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.take(50) }, // предел сервера: 2..50
            label = { Text(stringResource(if (registerMode) R.string.auth_username else R.string.auth_username_or_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (registerMode) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(120) },
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(200) }, // предел сервера: 8..200
            label = { Text(stringResource(R.string.auth_password)) },
            supportingText = if (registerMode) {
                { Text(stringResource(R.string.auth_pwd_hint)) }
            } else null,
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painterResource(if (showPassword) R.drawable.ic_eye_off else R.drawable.ic_eye),
                        contentDescription = stringResource(R.string.pwd_toggle),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (registerMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = privacyAccepted, onCheckedChange = { privacyAccepted = it })
                Text(
                    stringResource(R.string.consent_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { openSite(context, PRIVACY_URL) }) {
                Text(stringResource(R.string.acc_privacy), style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
                Text(
                    stringResource(R.string.consent_terms),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { openSite(context, TERMS_URL) }) {
                Text(stringResource(R.string.acc_terms), style = MaterialTheme.typography.labelMedium)
            }
        }

        viewModel.errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        viewModel.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (registerMode) viewModel.register(username, email, password)
                else viewModel.login(username, password)
            },
            // Пределы те же, что у сервера, иначе форма отправит заведомо невалидное и вернётся 422.
            // На входе не ограничиваем: у старых аккаунтов пароль мог быть короче 8.
            enabled = !viewModel.busy && username.isNotBlank() && password.isNotBlank() &&
                (!registerMode || (email.isNotBlank() && privacyAccepted && termsAccepted &&
                    username.trim().length >= 2 && password.length >= 8)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (registerMode) R.string.auth_do_register else R.string.auth_do_login))
        }
        RecoveryButton(viewModel, if (registerMode) email else username.takeIf { '@' in it }.orEmpty())
        TextButton(onClick = { registerMode = !registerMode }) {
            Text(stringResource(if (registerMode) R.string.auth_have_account else R.string.auth_no_account))
        }
        }
        PremiumInfoPanel()
        OutlinedButton(onClick = onMaterials, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.materials_title))
        }
        LegalPanel()
    }
}

@Composable
private fun Profile(me: MeResponse, viewModel: AccountViewModel, onMaterials: () -> Unit) {
    val chartsCount by viewModel.chartsCount.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ru.astrosmap.app.ui.theme.AppHeader(stringResource(R.string.section_account))
        ru.astrosmap.app.ui.theme.AstroPanel {
        Text(me.username, style = MaterialTheme.typography.headlineSmall)
        me.email?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!me.emailVerified) {
                    Text(
                        stringResource(R.string.email_unverified),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (me.premium) {
            Text(
                stringResource(R.string.premium_active, me.premiumUntilDate() ?: ""),
                color = GoodColor,
            )
        } else {
            Text(stringResource(R.string.premium_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) {
                Button(
                    onClick = { openSite(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.premium_buy)) }
            }
        }
        Text(
            stringResource(R.string.acc_charts_count, chartsCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RecoveryButton(viewModel, me.email.orEmpty(), true)
        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_logout))
        }
        TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
        }
        }
        PremiumInfoPanel()
        OutlinedButton(onClick = onMaterials, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.materials_title))
        }
        LegalPanel()
    }
    if (showDeleteDialog) {
        DeleteAccountDialog(
            busy = viewModel.busy,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteAccount(it)
            },
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_account_warning))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(200) },
                    label = { Text(stringResource(R.string.auth_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank() && !busy,
            ) { Text(stringResource(R.string.delete_account_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Памятка: что даёт подписка «Премиум». В googleplay-сборке скрыта (без увода на оплату). */
@Composable
private fun PremiumInfoPanel() {
    if (!BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) return
    ru.astrosmap.app.ui.theme.AstroPanel {
        Text(
            "✦ " + stringResource(R.string.premium_info_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        listOf(
            R.string.premium_info_1,
            R.string.premium_info_2,
            R.string.premium_info_3,
            R.string.premium_info_4,
        ).forEach {
            Text("•  " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Юр-блок кабинета: язык, сайт, словарь, политика, соглашение, 18+, версия приложения. */
@Composable
private fun LegalPanel() {
    val context = LocalContext.current
    ru.astrosmap.app.ui.theme.AstroPanel {
        FeedbackButton()
        ru.astrosmap.app.ui.assistant.AssistantSettings(context)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LanguageRow(context)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DailyNotifyRow(context)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        WidgetPrivacyRow(context)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) {
            TextButton(onClick = { openSite(context) }) { Text(stringResource(R.string.acc_site)) }
            TextButton(onClick = { openSite(context, "https://astrosmap.ru/#glossary") }) {
                Text(stringResource(R.string.acc_glossary))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        TextButton(onClick = { openSite(context, PRIVACY_URL) }) { Text(stringResource(R.string.acc_privacy)) }
        TextButton(onClick = { openSite(context, TERMS_URL) }) { Text(stringResource(R.string.acc_terms)) }
        Text(
            stringResource(R.string.acc_age),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.acc_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WidgetPrivacyRow(context: android.content.Context) {
    var hidden by rememberSaveable { mutableStateOf(ru.astrosmap.app.widget.WidgetPrefs.hidePersonal(context)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.widget_hide_personal), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = hidden,
            onCheckedChange = {
                hidden = it
                ru.astrosmap.app.widget.WidgetPrefs.setHidePersonal(context, it)
            },
        )
    }
}

/**
 * Ежедневное напоминание: выключатель + время. Разрешение на уведомления
 * спрашиваем только в момент включения — не при первом запуске.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyNotifyRow(context: android.content.Context) {
    val premium = DailyNotify.cachedPremium(context)
    var enabled by rememberSaveable { mutableStateOf(DailyNotify.isEnabled(context)) }
    var hour by rememberSaveable { mutableIntStateOf(DailyNotify.hour(context)) }
    var minute by rememberSaveable { mutableIntStateOf(DailyNotify.minute(context)) }
    var showPicker by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) {
            enabled = true
            DailyNotify.setEnabled(context, true)
        }
    }

    fun turnOn() {
        // На Android 13+ без разрешения уведомление просто не покажется — спрашиваем явно.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enabled = true
            DailyNotify.setEnabled(context, true)
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.notify_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    DailyNotify.setTime(context, hour, minute)
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🔔  " + stringResource(R.string.notify_setting), Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                denied = false
                if (on) turnOn() else {
                    enabled = false
                    DailyNotify.setEnabled(context, false)
                }
            },
        )
    }
    if (enabled) {
        TextButton(onClick = { showPicker = true }) {
            Text(stringResource(R.string.notify_time) + ": %02d:%02d".format(hour, minute))
        }
        Text(
            stringResource(R.string.notify_categories),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        listOf(
            DailyNotify.CATEGORY_FORECAST to R.string.notify_cat_forecast,
            DailyNotify.CATEGORY_MOON to R.string.notify_cat_moon,
            DailyNotify.CATEGORY_TRANSIT to R.string.notify_cat_transit,
            DailyNotify.CATEGORY_EVENTS to R.string.notify_cat_events,
            DailyNotify.CATEGORY_TAROT to R.string.notify_cat_tarot,
        ).forEach { (key, label) ->
            var checked by rememberSaveable(key) { mutableStateOf(DailyNotify.category(context, key)) }
            val available = key != DailyNotify.CATEGORY_TRANSIT || premium
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked && available,
                    enabled = available,
                    onCheckedChange = {
                        checked = it
                        DailyNotify.setCategory(context, key, it)
                    },
                )
                Text(
                    stringResource(label) + if (!available) " · Premium" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Text(
        stringResource(if (denied) R.string.notify_denied else R.string.notify_hint),
        style = MaterialTheme.typography.bodySmall,
        color = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Переключатель языка интерфейса. Меняет локаль и пересоздаёт экран. */
@Composable
private fun LanguageRow(context: android.content.Context) {
    val isRu = AstroLabels.isRu()
    fun switch(lang: String) {
        LangPref.set(context, lang)
        (context as? android.app.Activity)?.recreate()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🌐  " + stringResource(R.string.acc_language), Modifier.weight(1f))
        TextButton(onClick = { switch("ru") }) {
            Text("RU", fontWeight = if (isRu) FontWeight.Bold else FontWeight.Normal,
                color = if (isRu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { switch("en") }) {
            Text("EN", fontWeight = if (!isRu) FontWeight.Bold else FontWeight.Normal,
                color = if (!isRu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OfflineNote(viewModel: AccountViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.net_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
        TextButton(onClick = viewModel::logout) { Text(stringResource(R.string.auth_logout)) }
    }
}
