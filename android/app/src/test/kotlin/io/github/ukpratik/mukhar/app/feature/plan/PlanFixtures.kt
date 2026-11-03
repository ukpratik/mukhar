package io.github.ukpratik.mukhar.app.feature.plan

import io.github.ukpratik.mukhar.data.billing.PlanOffer
import io.github.ukpratik.mukhar.domain.billing.EntitlementSnapshot
import io.github.ukpratik.mukhar.domain.billing.Plan
import io.github.ukpratik.mukhar.domain.billing.PlanCatalog
import io.github.ukpratik.mukhar.domain.billing.Prices
import io.github.ukpratik.mukhar.domain.billing.SubscriptionStatus
import java.time.LocalDate

/** S18 states shared by the paywall and Plan tests. */
object PlanFixtures {
    fun offers(trial: Boolean) = Plan.entries.map {
        val micros = PlanCatalog.LIST_PRICE_MICROS.getValue(it)
        PlanOffer(it, micros, "INR", Prices.format(micros), if (trial) 7 else null, "tok")
    }

    /** The paywall with real prices; the founding plan pre-selected. */
    fun paywall(trial: Boolean = true): PlanUiState {
        val o = offers(trial)
        return PlanUiState(loading = false, offers = o, selected = PlanViewModel.preselect(o))
    }

    /** Plan facts on day 6 of a yearly trial, with the day-6 banner. */
    fun trialFacts(): PlanUiState {
        val e = EntitlementSnapshot(premium = true, expiryMs = 1L, isTrial = true, autoRenewing = true, plan = Plan.YEARLY, priceLabel = "₹1,499")
        return PlanUiState(
            loading = false, snapshot = e, status = SubscriptionStatus.TRIAL, trialDay = 6,
            banner = TrialBannerUi(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 12), "₹1,499", Plan.YEARLY.period),
        )
    }
}
