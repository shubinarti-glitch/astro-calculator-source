package ru.astrosmap.app.data.access

import ru.astrosmap.app.data.api.MeResponse

/** Тариф аккаунта. Неизвестное серверное значение безопасно трактуется как FREE. */
enum class SubscriptionPlan(val wireName: String) {
    FREE("free"),
    PREMIUM("premium"),
    PROFESSIONAL("professional");

    companion object {
        fun fromWire(value: String?, legacyPremium: Boolean = false): SubscriptionPlan =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
                ?: if (legacyPremium) PREMIUM else FREE
    }
}

/** Отдельные права позволяют развивать тарифы без привязки UI к одному premium-флагу. */
enum class Entitlement(val wireName: String) {
    CHARTS_UNLIMITED("charts_unlimited"),
    ADVANCED_FORECASTS("advanced_forecasts"),
    FULL_CALENDAR("full_calendar"),
    JOURNAL_HISTORY("journal_history"),
    TAROT_DAILY_SPREADS("tarot_daily_spreads"),
    PDF_EXPORT("pdf_export"),
    ADVANCED_WIDGET("advanced_widget"),
    AI_ASSISTANT("ai_assistant"),
    PROFESSIONAL_TOOLS("professional_tools");

    companion object {
        fun fromWire(value: String): Entitlement? =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }

        val premiumDefaults: Set<Entitlement> = entries
            .filterNot { it == PROFESSIONAL_TOOLS }
            .toSet()

        val professionalDefaults: Set<Entitlement> = entries.toSet()
    }
}

data class AccessState(
    val plan: SubscriptionPlan = SubscriptionPlan.FREE,
    val premium: Boolean = false,
    val subscriptionSource: String? = null,
    val entitlements: Set<Entitlement> = emptySet(),
) {
    fun hasEntitlement(entitlement: Entitlement): Boolean = entitlement in entitlements

    companion object {
        fun from(me: MeResponse): AccessState {
            // Неактивная подписка никогда не открывает тариф только по строке plan.
            val plan = if (me.premium) {
                SubscriptionPlan.fromWire(me.plan, legacyPremium = true)
            } else {
                SubscriptionPlan.FREE
            }
            val effectiveEntitlements = me.entitlements?.let { explicit ->
                // Новый сервер уже вычислил grant/deny. Его список — источник истины.
                explicit.mapNotNull(Entitlement::fromWire).toSet()
            } ?: if (me.premium) {
                // Совместимость только со старым сервером, где поля ещё не было.
                when (plan) {
                    SubscriptionPlan.FREE -> emptySet()
                    SubscriptionPlan.PREMIUM -> Entitlement.premiumDefaults
                    SubscriptionPlan.PROFESSIONAL -> Entitlement.professionalDefaults
                }
            } else {
                emptySet()
            }
            return AccessState(
                plan = plan,
                // Сохраняем legacy premium как источник истины для существующих экранов.
                premium = me.premium,
                subscriptionSource = me.subscriptionSource,
                entitlements = effectiveEntitlements,
            )
        }
    }
}

fun MeResponse.hasEntitlement(entitlement: Entitlement): Boolean =
    accessState().hasEntitlement(entitlement)
