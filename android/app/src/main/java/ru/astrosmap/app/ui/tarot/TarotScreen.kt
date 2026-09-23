package ru.astrosmap.app.ui.tarot

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import ru.astrosmap.app.R
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.ui.openSite
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import ru.astrosmap.app.ui.saved.SaveMaterialButton
import javax.inject.Inject

/** Живой таролог — контакт для консультации (тот же, что у астролога на сайте). */
private const val TAROLOGIST_TG = "https://t.me/Astrosmap"

/**
 * Расклады. `positions` задают и смысл позиций, и число карт.
 *
 * YES_NO — по карте на «да» и на «нет» из полной колоды: выигрывает позиция, где карта
 * старше по градации колоды. Исход определён самими картами, а не случайной меткой.
 */
private enum class Spread(val titleRes: Int, val introRes: Int, val positions: List<Int>) {
    SITUATION(R.string.tarot_spread_situation, R.string.tarot_intro_situation, listOf(
        R.string.tarot_pos_essence, R.string.tarot_pos_obstacle, R.string.tarot_pos_advice)),
    MFA(R.string.tarot_spread_mfa, R.string.tarot_intro_mfa, listOf(
        R.string.tarot_pos_thoughts, R.string.tarot_pos_feelings, R.string.tarot_pos_actions)),
    YES_NO(R.string.tarot_spread_yesno, R.string.tarot_intro_yesno, listOf(
        R.string.tarot_pos_yes, R.string.tarot_pos_no)),
}

@HiltViewModel
class TarotViewModel @Inject constructor(private val api: AstroApi) : ViewModel() {
    var premium by mutableStateOf(false)
        private set

    init {
        // Премиум определяет частоту раскладов. Офлайн/без входа — считаем бесплатным.
        viewModelScope.launch { premium = runCatching { api.me().premium }.getOrDefault(false) }
    }
}

@Composable
fun TarotScreen(viewModel: TarotViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var spread by remember { mutableStateOf<Spread?>(null) }
    var pendingSpread by remember { mutableStateOf<Spread?>(null) }
    var cards by remember { mutableStateOf<List<TarotCard>>(emptyList()) }
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var showArchive by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.section_tarot))

        // Предупреждение по ФЗ + честная ссылка на живого таролога.
        AstroPanel {
            Text(
                stringResource(R.string.tarot_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { openSite(context, TAROLOGIST_TG) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tarot_live_reader)) }
        }

        if (showArchive) {
            TarotArchive(onBack = { showArchive = false })
        } else if (spread == null) {
            AstroPanel {
                if (pendingSpread == null) {
                Text(
                    stringResource(R.string.tarot_choose_spread),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(
                    onClick = { showArchive = true },
                    enabled = viewModel.premium,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tarot_archive)) }
                if (!viewModel.premium) {
                    Text(
                        stringResource(R.string.tarot_archive_premium),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // У каждого расклада свой недельный лимит — считаем по отдельности.
                Spread.entries.forEach { s ->
                    val cooldown = TarotStorage.spreadCooldownDays(context, s.name, viewModel.premium)
                    val saved = TarotStorage.savedSpread(context, s.name)
                    val savedCards = saved?.cardIds?.mapNotNull(TarotDeck::byId).orEmpty()
                    val canOpenCurrent = cooldown > 0 && savedCards.size == s.positions.size
                    Button(
                        onClick = {
                            if (canOpenCurrent) {
                                spread = s
                                cards = savedCards
                                revealed = saved!!.revealed.filter { it in savedCards.indices }.toSet()
                            } else {
                                pendingSpread = s
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                canOpenCurrent -> stringResource(R.string.tarot_open_current, stringResource(s.titleRes))
                                saved != null -> stringResource(R.string.tarot_make_another, stringResource(s.titleRes))
                                else -> stringResource(s.titleRes)
                            },
                        )
                    }
                }
                val anyLocked = Spread.entries.any {
                    TarotStorage.spreadCooldownDays(context, it.name, viewModel.premium) > 0
                }
                if (anyLocked && !viewModel.premium && ru.astrosmap.app.BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) {
                    Button(onClick = { openSite(context, "https://astrosmap.ru/#premium") },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.premium_buy))
                    }
                }
                } else {
                    val selected = pendingSpread!!
                    Text(
                        stringResource(R.string.tarot_prepare_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(selected.titleRes), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(selected.introRes), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.tarot_prepare_common),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            val drawn = TarotDeck.draw(selected.positions.size)
                            cards = drawn
                            revealed = emptySet()
                            TarotStorage.saveSpread(context, selected.name, drawn, emptySet())
                            TarotStorage.markSpreadDone(context, selected.name)
                            spread = selected
                            pendingSpread = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.tarot_start_spread)) }
                    OutlinedButton(
                        onClick = { pendingSpread = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.tarot_back_to_spreads)) }
                }
            }
        } else {
            AstroPanel {
                Text(
                    stringResource(spread!!.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.tarot_tap_to_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (spread == Spread.YES_NO) {
                    Text(
                        stringResource(R.string.tarot_yesno_rule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                cards.forEachIndexed { i, card ->
                    SpreadRow(
                        position = stringResource(spread!!.positions[i]),
                        card = card,
                        open = i in revealed,
                        onOpen = {
                            val updated = revealed + i
                            revealed = updated
                            TarotStorage.saveSpread(context, spread!!.name, cards, updated)
                            if (updated.size == cards.size) {
                                TarotStorage.saveCompletedSpread(context, spread!!.name, cards)
                            }
                        },
                    )
                }
                // Вердикт «да / нет» — только когда обе карты открыты.
                if (spread == Spread.YES_NO && revealed.size == cards.size && cards.size == 2) {
                    YesNoVerdict(yes = cards[0], no = cards[1])
                }
                if (revealed.size == cards.size) {
                    Text(
                        stringResource(R.string.tarot_reflection),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        stringResource(R.string.tarot_reflection_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val cardsBody = cards.mapIndexed { index, card ->
                        "${stringResource(spread!!.positions[index])}: ${card.name}\n${card.meaning}\n${card.advice}"
                    }.joinToString("\n\n")
                    val savedBody = if (spread == Spread.YES_NO && cards.size == 2) {
                        val isYes = TarotDeck.rank(cards[0].id) > TarotDeck.rank(cards[1].id)
                        val winner = if (isYes) cards[0] else cards[1]
                        val answer = stringResource(
                            if (isYes) R.string.tarot_answer_yes else R.string.tarot_answer_no,
                        )
                        val why = stringResource(
                            R.string.tarot_yesno_why,
                            winner.name,
                            stringResource(if (isYes) R.string.tarot_pos_yes else R.string.tarot_pos_no),
                        )
                        "$answer\n$why\n${winner.advice}\n\n" +
                            "${stringResource(R.string.tarot_reflection)}\n" +
                            "${stringResource(R.string.tarot_reflection_text)}\n\n$cardsBody"
                    } else {
                        cardsBody
                    }
                    SaveMaterialButton(
                        sourceType = "tarot", sourceId = spread!!.name,
                        title = stringResource(spread!!.titleRes),
                        body = savedBody,
                        premium = viewModel.premium,
                    )
                }
                OutlinedButton(
                    onClick = { spread = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tarot_done)) }
            }
        }
    }
}

/**
 * Итог расклада «да / нет»: сравниваем старшинство карт на двух позициях
 * по градации всей колоды. Побеждает старшая — она и даёт ответ.
 * Ничьей не бывает: карты в раскладе всегда разные, а ранги уникальны.
 */
@Composable
private fun TarotArchive(onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableStateOf(0) }
    val history = remember(revision) { TarotStorage.history(context) }
    val dayCards = remember(revision) { TarotStorage.dayArchive(context) }
    val frequency = remember(revision) { TarotStorage.cardFrequency(context).take(10) }
    val locale = context.resources.configuration.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }

    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tarot_back_to_spreads))
    }
    Text(stringResource(R.string.tarot_day_archive_title),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    if (dayCards.isEmpty()) {
        Text(stringResource(R.string.tarot_archive_empty))
    } else {
        AstroPanel {
            dayCards.take(14).forEach { item ->
                val card = TarotDeck.byId(item.cardId) ?: return@forEach
                Text("${LocalDate.ofEpochDay(item.epochDay).format(dateFormatter)} — ${card.name}")
            }
        }
    }

    Text(stringResource(R.string.tarot_history_title),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    if (history.isEmpty()) {
        Text(stringResource(R.string.tarot_archive_empty))
    } else {
        history.take(30).forEach { record ->
            val spread = Spread.entries.firstOrNull { it.name == record.spreadKey }
            val cardNames = record.cardIds.mapNotNull(TarotDeck::byId).joinToString(" · ") { it.name }
            var note by remember(record.id, revision) { mutableStateOf(record.note) }
            AstroPanel {
                Text(spread?.let { stringResource(it.titleRes) } ?: record.spreadKey,
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Text(LocalDate.ofEpochDay(record.epochDay).format(dateFormatter))
                Text(cardNames, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text(stringResource(R.string.tarot_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { TarotStorage.updateNote(context, record.id, note); revision++ },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tarot_note_save)) }
            }
        }
    }

    Text(stringResource(R.string.tarot_statistics_title),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    if (frequency.isEmpty()) {
        Text(stringResource(R.string.tarot_archive_empty))
    } else {
        AstroPanel {
            frequency.forEach { (cardId, count) ->
                val card = TarotDeck.byId(cardId) ?: return@forEach
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(card.name, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.tarot_times, count))
                }
            }
        }
    }
}

@Composable
private fun YesNoVerdict(yes: TarotCard, no: TarotCard) {
    val isYes = TarotDeck.rank(yes.id) > TarotDeck.rank(no.id)
    val winner = if (isYes) yes else no

    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (isYes) R.string.tarot_answer_yes else R.string.tarot_answer_no),
            style = MaterialTheme.typography.headlineMedium,
            color = if (isYes) ru.astrosmap.app.ui.theme.GoodColor else MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(
                R.string.tarot_yesno_why,
                winner.name,
                stringResource(if (isYes) R.string.tarot_pos_yes else R.string.tarot_pos_no),
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            winner.advice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SpreadRow(position: String, card: TarotCard, open: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val face = rememberTarotFace(card.id)
        if (open && face != null) {
            Image(
                bitmap = face,
                contentDescription = card.name,
                modifier = Modifier.width(74.dp).aspectRatio(0.58f).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            CardBack(
                Modifier
                    .width(74.dp)
                    .aspectRatio(0.58f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpen,
                    ),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                position,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (open) {
                Text(card.name, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(card.meaning, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.tarot_face_down),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
