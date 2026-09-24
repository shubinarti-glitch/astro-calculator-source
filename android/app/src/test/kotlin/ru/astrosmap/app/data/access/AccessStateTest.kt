package ru.astrosmap.app.data.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.astrosmap.app.data.api.MeResponse

class AccessStateTest {
    @Test
    fun inactivePremiumPlanDoesNotOpenPremiumRights() {
        val state = MeResponse(
            username = "free",
            premium = false,
            plan = "premium",
            entitlements = emptyList(),
        ).accessState()

        assertFalse(state.premium)
        assertFalse(state.hasEntitlement(Entitlement.PDF_EXPORT))
    }

    @Test
    fun explicitServerListIsAuthoritativeForActivePremium() {
        val state = MeResponse(
            username = "limited",
            premium = true,
            plan = "premium",
            entitlements = listOf("full_calendar"),
        ).accessState()

        assertTrue(state.hasEntitlement(Entitlement.FULL_CALENDAR))
        assertFalse(state.hasEntitlement(Entitlement.PDF_EXPORT))
    }

    @Test
    fun missingEntitlementsKeepsCompatibilityWithLegacyServer() {
        val state = MeResponse(
            username = "legacy",
            premium = true,
            entitlements = null,
        ).accessState()

        assertTrue(state.hasEntitlement(Entitlement.PDF_EXPORT))
        assertFalse(state.hasEntitlement(Entitlement.PROFESSIONAL_TOOLS))
    }

    @Test
    fun freeUserMayReceiveOnlyExplicitPromotionalRight() {
        val state = MeResponse(
            username = "promo",
            premium = false,
            plan = "free",
            entitlements = listOf("journal_history"),
        ).accessState()

        assertTrue(state.hasEntitlement(Entitlement.JOURNAL_HISTORY))
        assertFalse(state.hasEntitlement(Entitlement.FULL_CALENDAR))
    }
}
