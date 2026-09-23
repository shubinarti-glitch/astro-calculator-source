package ru.astrosmap.app.ui.tarot

import ru.astrosmap.app.ui.AstroLabels

/**
 * Колода Райдера—Уэйта, 78 карт. Только прямое положение — одна трактовка на карту.
 *
 * id совпадает с именем файла в assets/tarot/<id>.webp. Тексты авторские, двуязычные:
 * meaning — суть аркана (с оттенком роста/тени), advice — как применить в течение дня.
 * name/meaning/advice выбираются по языку приложения (AstroLabels.isRu()).
 */
data class TarotCard(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val meaningRu: String,
    val meaningEn: String,
    val adviceRu: String,
    val adviceEn: String,
) {
    val name: String get() = if (AstroLabels.isRu()) nameRu else nameEn
    val meaning: String get() = if (AstroLabels.isRu()) meaningRu else meaningEn
    val advice: String get() = if (AstroLabels.isRu()) adviceRu else adviceEn
}

object TarotDeck {

    val cards: List<TarotCard> = ru.astrosmap.app.editorial.AndroidEditorial.cards

    init {
        require(cards.size == 78) { "В колоде должно быть 78 карт, а не ${cards.size}" }
    }

    fun byId(id: String): TarotCard? = cards.firstOrNull { it.id == id }

    /** n различных случайных карт — для выбора «трёх рубашек» и для раскладов. */
    fun draw(n: Int): List<TarotCard> = cards.shuffled().take(n)

    /**
     * Старшинство карты по градации колоды — им расклад «да / нет» решает исход.
     *
     * Любой старший аркан весомее любого младшего; внутри старших — по номеру (Мир 21
     * старше Шута 0); внутри младших — по достоинству (Король выше Туза), масть —
     * лишь тай-брейк в порядке колоды. Ранги уникальны, поэтому ничьей не бывает.
     */
    fun rank(id: String): Int {
        majorNumber(id)?.let { return 1000 + it }
        val value = id.takeLast(2).toIntOrNull() ?: 0
        val suit = when {
            id.startsWith("wands") -> 0
            id.startsWith("cups") -> 1
            id.startsWith("swords") -> 2
            else -> 3
        }
        return value * 10 + suit
    }

    /** Номер старшего аркана из id: "major_19_sun" → 19; у младших номера нет. */
    fun majorNumber(id: String): Int? =
        id.removePrefix("major_").takeIf { it != id }?.take(2)?.toIntOrNull()
}
