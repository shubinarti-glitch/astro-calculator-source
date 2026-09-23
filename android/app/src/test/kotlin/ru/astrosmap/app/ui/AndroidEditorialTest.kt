package ru.astrosmap.app.ui

import java.util.Locale
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import ru.astrosmap.app.ui.tarot.TarotDeck
import ru.astrosmap.app.ui.tools.LunarTexts

/** Plain JVM: first access requires neither Android Context nor an Activity/network. */
class AndroidEditorialTest {
    @Test fun offlineSynchronousDataAndStableSavedIds() {
        val cards = TarotDeck.cards
        assertEquals(78, cards.size)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(cards.joinToString("\n") { it.id }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals("25e07797a22b4977a67a24113c22c12d3a22aedc85636e605a4d51e49c8b5c53", hash)
        cards.forEach { card ->
            assertSame(card, TarotDeck.byId(card.id))
            assertTrue(listOf(card.nameRu, card.nameEn, card.meaningRu, card.meaningEn,
                card.adviceRu, card.adviceEn).all { it.isNotBlank() })
        }
        assertNull(TarotDeck.byId("unknown"))
        assertEquals(78, cards.map { TarotDeck.rank(it.id) }.distinct().size)
        val draw = TarotDeck.draw(3)
        assertEquals(3, draw.distinct().size)
        assertTrue(draw.all { it in cards })
    }

    @Test fun languageSwitchAfterInitializationAndUnknownFallbacks() {
        val previous = Locale.getDefault()
        try {
            val cards = TarotDeck.cards
            val phases = LunarTexts.phaseEmoji.keys.toList()
            val signs = listOf("Ari", "Tau", "Gem", "Can", "Leo", "Vir", "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis")
            Locale.setDefault(Locale.forLanguageTag("ru"))
            val ruAdvice = phases.map { LunarTexts.phaseAdvice(it) }
            val ruMoods = signs.map { LunarTexts.moonMood(it) }
            val ruNames = phases.map { LunarTexts.phaseName(it) }
            cards.forEach {
                assertEquals(it.nameRu, it.name)
                assertEquals(it.meaningRu, it.meaning)
                assertEquals(it.adviceRu, it.advice)
            }
            Locale.setDefault(Locale.ENGLISH)
            cards.forEach {
                assertEquals(it.nameEn, it.name)
                assertEquals(it.meaningEn, it.meaning)
                assertEquals(it.adviceEn, it.advice)
            }
            phases.forEachIndexed { i, key ->
                assertTrue(ruAdvice[i].isNotBlank())
                assertTrue(LunarTexts.phaseAdvice(key).isNotBlank())
                assertNotEquals(ruAdvice[i], LunarTexts.phaseAdvice(key))
                assertNotEquals(ruNames[i], LunarTexts.phaseName(key))
                assertEquals(key, LunarTexts.phaseName(key))
            }
            signs.forEachIndexed { i, key ->
                assertTrue(ruMoods[i].isNotBlank())
                assertTrue(LunarTexts.moonMood(key).isNotBlank())
                assertNotEquals(ruMoods[i], LunarTexts.moonMood(key))
            }
            Locale.setDefault(Locale.forLanguageTag("ru"))
            assertEquals(ruAdvice, phases.map { LunarTexts.phaseAdvice(it) })
            assertEquals(ruMoods, signs.map { LunarTexts.moonMood(it) })
            assertEquals("", LunarTexts.phaseAdvice("unknown"))
            assertEquals("", LunarTexts.moonMood("unknown"))
            assertEquals("unknown", LunarTexts.phaseName("unknown"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
