package ru.astrosmap.app.ui.tarot

import android.content.Context
import android.net.Uri
import java.time.LocalDate

/**
 * Локальное состояние Таро: карта дня (одна в сутки) и лимит раскладов.
 *
 * ponytail: SharedPreferences, как язык и напоминание — своей таблицы Room не заводим.
 * Лимиты — на устройстве: премиум проверяется флагом, без обращения к серверу.
 */
object TarotStorage {

    data class SavedSpread(
        val spreadKey: String,
        val cardIds: List<String>,
        val revealed: Set<Int>,
    )

    data class SpreadRecord(
        val id: Long,
        val epochDay: Long,
        val spreadKey: String,
        val cardIds: List<String>,
        val note: String,
    )

    data class DayCardRecord(val epochDay: Long, val cardId: String)

    private const val PREFS = "settings"
    private const val KEY_SPREAD_TYPE = "tarot_saved_spread_type"
    private const val KEY_SPREAD_CARDS = "tarot_saved_spread_cards"
    private const val KEY_SPREAD_REVEALED = "tarot_saved_spread_revealed"
    private const val KEY_HISTORY = "tarot_history_v1"
    private const val KEY_DAY_ARCHIVE = "tarot_day_archive_v1"
    private const val KEY_DAY_CARD = "tarot_day_card"     // id карты дня
    private const val KEY_DAY_DATE = "tarot_day_date"     // дата (epochDay), когда вытянули
    // Лимит считается ОТДЕЛЬНО для каждого расклада: ключ — "tarot_spread_<тип>".

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toEpochDay()

    /** Карта дня, если уже вытянута сегодня; иначе null (нужно тянуть). */
    fun todayCard(context: Context): TarotCard? {
        val p = prefs(context)
        if (p.getLong(KEY_DAY_DATE, -1) != today()) return null
        return p.getString(KEY_DAY_CARD, null)?.let { TarotDeck.byId(it) }
    }

    fun saveDayCard(context: Context, card: TarotCard) {
        val p = prefs(context)
        val archive = dayArchive(context)
            .filterNot { it.epochDay == today() }
            .plus(DayCardRecord(today(), card.id))
            .sortedByDescending { it.epochDay }
            .take(90)
        p.edit()
            .putString(KEY_DAY_CARD, card.id)
            .putLong(KEY_DAY_DATE, today())
            .putString(KEY_DAY_ARCHIVE, archive.joinToString(";") { "${it.epochDay}:${it.cardId}" })
            .apply()
    }

    fun dayArchive(context: Context): List<DayCardRecord> = prefs(context)
        .getString(KEY_DAY_ARCHIVE, "")
        .orEmpty()
        .split(';')
        .mapNotNull { raw ->
            val parts = raw.split(':', limit = 2)
            val day = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val cardId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DayCardRecord(day, cardId)
        }
        .sortedByDescending { it.epochDay }

    /**
     * Дней до следующего использования КОНКРЕТНОГО расклада (0 — можно сейчас).
     * Бесплатно — раз в 7 дней, премиум — раз в день. Каждый расклад считается отдельно.
     */
    fun spreadCooldownDays(context: Context, spreadKey: String, premium: Boolean): Int {
        val last = prefs(context).getLong("tarot_spread_$spreadKey", -8)
        if (last < 0) return 0
        val passed = (today() - last).toInt()
        val period = if (premium) 1 else 7
        return (period - passed).coerceAtLeast(0)
    }

    fun markSpreadDone(context: Context, spreadKey: String) {
        prefs(context).edit().putLong("tarot_spread_$spreadKey", today()).apply()
    }

    /** Последний расклад хранится до создания следующего; возврат к нему лимит не расходует. */
    fun saveSpread(
        context: Context,
        spreadKey: String,
        cards: List<TarotCard>,
        revealed: Set<Int>,
    ) {
        prefs(context).edit()
            .putString("${KEY_SPREAD_TYPE}_$spreadKey", spreadKey)
            .putString("${KEY_SPREAD_CARDS}_$spreadKey", cards.joinToString(",") { it.id })
            .putString("${KEY_SPREAD_REVEALED}_$spreadKey", revealed.sorted().joinToString(","))
            .apply()
    }

    fun savedSpread(context: Context, requestedKey: String): SavedSpread? {
        val p = prefs(context)
        var spreadKey = p.getString("${KEY_SPREAD_TYPE}_$requestedKey", null)
        var cardsRaw = p.getString("${KEY_SPREAD_CARDS}_$requestedKey", null)
        var revealedRaw = p.getString("${KEY_SPREAD_REVEALED}_$requestedKey", "")

        // One-time compatibility with the former single "last spread" storage.
        if (spreadKey == null && p.getString(KEY_SPREAD_TYPE, null) == requestedKey) {
            spreadKey = requestedKey
            cardsRaw = p.getString(KEY_SPREAD_CARDS, null)
            revealedRaw = p.getString(KEY_SPREAD_REVEALED, "")
            val cardsForMigration = cardsRaw.orEmpty().split(',').filter { it.isNotBlank() }
            val revealedForMigration = revealedRaw.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
            if (cardsForMigration.isNotEmpty()) {
                saveSpread(context, requestedKey, cardsForMigration.mapNotNull(TarotDeck::byId), revealedForMigration)
            }
        }

        val resolvedKey = spreadKey?.takeIf { it.isNotBlank() } ?: return null
        val cardIds = cardsRaw
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val revealed = revealedRaw
            .orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .toSet()
        return SavedSpread(resolvedKey, cardIds, revealed)
    }

    fun saveCompletedSpread(context: Context, spreadKey: String, cards: List<TarotCard>) {
        if (cards.isEmpty()) return
        val existing = history(context)
        val cardIds = cards.map(TarotCard::id)
        val duplicate = existing.firstOrNull()?.let {
            it.epochDay == today() && it.spreadKey == spreadKey && it.cardIds == cardIds
        } == true
        if (duplicate) return
        writeHistory(
            context,
            listOf(SpreadRecord(System.currentTimeMillis(), today(), spreadKey, cardIds, "")) + existing,
        )
    }

    fun history(context: Context): List<SpreadRecord> = prefs(context)
        .getString(KEY_HISTORY, "")
        .orEmpty()
        .lineSequence()
        .mapNotNull(::decodeRecord)
        .sortedByDescending { it.id }
        .toList()

    fun updateNote(context: Context, recordId: Long, note: String) {
        writeHistory(context, history(context).map {
            if (it.id == recordId) it.copy(note = note.trim().take(1000)) else it
        })
    }

    fun cardFrequency(context: Context): List<Pair<String, Int>> {
        val ids = history(context).flatMap(SpreadRecord::cardIds) +
            dayArchive(context).map(DayCardRecord::cardId)
        return ids.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }

    private fun writeHistory(context: Context, records: List<SpreadRecord>) {
        val encoded = records.sortedByDescending { it.id }.take(100)
            .joinToString("\n", transform = ::encodeRecord)
        prefs(context).edit().putString(KEY_HISTORY, encoded).apply()
    }

    private fun encodeRecord(record: SpreadRecord): String = listOf(
        record.id.toString(),
        record.epochDay.toString(),
        record.spreadKey,
        record.cardIds.joinToString(","),
        Uri.encode(record.note),
    ).joinToString("|")

    private fun decodeRecord(raw: String): SpreadRecord? {
        val parts = raw.split('|', limit = 5)
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val day = parts.getOrNull(1)?.toLongOrNull() ?: return null
        val spread = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return null
        val cards = parts.getOrNull(3)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (cards.isEmpty()) return null
        return SpreadRecord(id, day, spread, cards, Uri.decode(parts.getOrNull(4).orEmpty()))
    }
}
