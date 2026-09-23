package ru.astrosmap.app.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Форма данных рождения: имя, дата и время (системные пикеры), город из офлайн-базы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartFormScreen(
    onCalculated: () -> Unit,
    viewModel: ChartFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.maybeConsumeEdit() }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    if (showDate) {
        val initial = runCatching {
            LocalDate.of(viewModel.year.toInt(), viewModel.month.toInt(), viewModel.day.toInt())
        }.getOrDefault(LocalDate.of(1990, 1, 1))
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.day = d.dayOfMonth.toString()
                        viewModel.month = d.monthValue.toString()
                        viewModel.year = d.year.toString()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state, showModeToggle = true) }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = viewModel.hour.toIntOrNull() ?: 12,
            initialMinute = viewModel.minute.toIntOrNull() ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hour = state.hour.toString()
                    viewModel.minute = state.minute.toString()
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.app_tagline))

        AstroPanel {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it.take(80) },
                label = { Text(stringResource(R.string.form_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(
                    value = dateText(viewModel),
                    label = stringResource(R.string.form_date),
                    onClick = { showDate = true },
                    modifier = Modifier.weight(1.4f),
                )
                PickerField(
                    value = "%02d:%02d".format(
                        viewModel.hour.toIntOrNull() ?: 12,
                        viewModel.minute.toIntOrNull() ?: 0,
                    ),
                    label = stringResource(R.string.form_time),
                    onClick = { showTime = true },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = viewModel.cityQuery,
                onValueChange = viewModel::onCityQuery,
                label = { Text(stringResource(R.string.form_city)) },
                singleLine = true,
                isError = viewModel.errorRes != null && viewModel.selectedCity == null,
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.suggestions.forEach { city ->
                Surface(
                    onClick = { viewModel.pickCity(city) },
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        city.label(AstroLabels.isRu()),
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            viewModel.errorRes?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { if (viewModel.calculate()) onCalculated() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.form_calculate))
            }
        }

        // Коммерческая витрина сайта доступна только сборкам, которым разрешены
        // внешние переходы. В Google Play она скрыта целиком, поскольку главная
        // страница сайта содержит путь к покупке цифровых функций.
        if (ru.astrosmap.app.BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) AstroPanel {
            val context = androidx.compose.ui.platform.LocalContext.current
            Surface(
                onClick = { ru.astrosmap.app.ui.openSite(context) },
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🌐")
                    Text(stringResource(R.string.site_banner), Modifier.weight(1f))
                    Text("›", color = MaterialTheme.colorScheme.primary)
                }
            }
            // Оформление подписки идёт на сайте — диплинк открывает окно «Премиум».
            // В googleplay-сборке скрыто: увод на внешнюю оплату там запрещён.
            if (ru.astrosmap.app.BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) {
                Button(
                    onClick = {
                        viewModel.trackPremiumTap()
                        ru.astrosmap.app.ui.openSite(context, "https://astrosmap.ru/#premium")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.premium_cta))
                }
            }
        }
    }
}

private fun dateText(vm: ChartFormViewModel): String {
    val d = vm.day.toIntOrNull()
    val m = vm.month.toIntOrNull()
    val y = vm.year.toIntOrNull()
    return if (d != null && m != null && y != null) "%02d.%02d.%d".format(d, m, y) else ""
}

/** Поле, открывающее диалог по тапу (само поле не редактируется). */
@Composable
private fun PickerField(value: String, label: String, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
    }
}
