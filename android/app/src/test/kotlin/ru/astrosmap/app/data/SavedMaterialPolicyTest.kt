package ru.astrosmap.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMaterialPolicyTest {
    private val value = SavedMaterial(
        sourceType = "forecast", title = "Important transit", body = "Communication improves",
        note = "Discuss this later", tags = "work, mercury", folder = "August",
    )

    @Test fun freeLimitIsTenAndPremiumIsUnlimited() {
        assertTrue(SavedMaterialPolicy.canAdd(9, false))
        assertFalse(SavedMaterialPolicy.canAdd(10, false))
        assertTrue(SavedMaterialPolicy.canAdd(100, true))
    }

    @Test fun searchCoversContentNotesTagsAndFolders() {
        assertTrue(SavedMaterialPolicy.matches(value, "communication"))
        assertTrue(SavedMaterialPolicy.matches(value, "MERCURY"))
        assertTrue(SavedMaterialPolicy.matches(value, "august"))
        assertFalse(SavedMaterialPolicy.matches(value, "venus"))
    }
}
