package ru.astrosmap.app.ui.saved

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCardTest {
    @Test
    fun `single name uses one initial`() {
        assertEquals("А", chartInitials("Анна"))
    }

    @Test
    fun `two names use two initials`() {
        assertEquals("АП", chartInitials("Анна Петрова"))
    }

    @Test
    fun `blank name uses fallback`() {
        assertEquals("?", chartInitials("   "))
    }
}
