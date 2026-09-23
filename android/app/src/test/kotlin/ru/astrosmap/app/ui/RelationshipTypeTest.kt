package ru.astrosmap.app.ui.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipTypeTest {
    @Test fun romanticContentIsLimitedToPartners() {
        assertTrue(RelationshipType.PARTNERS.showsAttraction)
        assertTrue(RelationshipType.PARTNERS.showsRawInterpretations)
        RelationshipType.entries.filterNot { it == RelationshipType.PARTNERS }.forEach {
            assertFalse(it.showsAttraction)
            assertFalse(it.showsRawInterpretations)
        }
    }
}
