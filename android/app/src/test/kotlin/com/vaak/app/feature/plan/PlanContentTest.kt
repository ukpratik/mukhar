package com.vaak.app.feature.plan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.vaak.data.billing.PlanOffer
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.domain.billing.Plan
import com.vaak.domain.billing.PlanCatalog
import com.vaak.domain.billing.Prices
import com.vaak.domain.billing.SubscriptionStatus
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** S18 (US-061/062): real prices with per-month and per-week lines, the trial copy, Close always visible. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PlanContentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun offers(trial: Boolean) = Plan.entries.map {
        val micros = PlanCatalog.LIST_PRICE_MICROS.getValue(it)
        PlanOffer(it, micros, "INR", Prices.format(micros), if (trial) 7 else null, "tok")
    }

    private fun paywall(trial: Boolean = true): PlanUiState {
        val o = offers(trial)
        return PlanUiState(loading = false, offers = o, selected = PlanViewModel.preselect(o))
    }

    @Test
    fun paywallShowsPricesTrialCopyAndClose() {
        var buy = 0
        var closed = 0
        compose.setContent { VaakTheme { PlanContent(paywall(), PlanActions(onBuy = { buy++ }, onClose = { closed++ })) } }
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        // The cheapest per-month plan (founding member) is pre-selected.
        compose.onNodeWithTag(TAG_PLAN_PREFIX + "founding-yearly").assertIsSelected()
        compose.onNodeWithText("₹83 a month · about ₹19 a week").assertExists()
        compose.onNodeWithText("₹125 a month · about ₹29 a week").assertExists()
        compose.onNodeWithText("about ₹57 a week").assertExists()
        compose.onNodeWithText("₹1,499").assertExists()
        compose.onNodeWithText("₹249").assertExists()
        compose.onNodeWithText("7 days free, then ₹999 a year. We remind you 1 day before.").assertIsDisplayed()
        compose.onNodeWithText("Day 6 · we remind you").performScrollTo()
        compose.onNodeWithText("Day 7 · ₹999 charged").performScrollTo()
        compose.onNodeWithText("How to cancel").performScrollTo()
        compose.onNodeWithText("Start 7 days free").assertIsDisplayed().performClick()
        compose.onNodeWithText("Restore purchase").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        assertThat(buy to closed).isEqualTo(1 to 1)
    }

    @Test
    fun choosingMonthlyUpdatesTheCta() {
        var picked: Plan? = null
        val s = paywall().copy(selected = Plan.MONTHLY)
        compose.setContent { VaakTheme { PlanContent(s, PlanActions(onSelect = { picked = it })) } }
        compose.onNodeWithText("7 days free, then ₹249 a month. We remind you 1 day before.").assertExists()
        compose.onNodeWithText("Yearly").performClick()
        assertThat(picked).isEqualTo(Plan.YEARLY)
    }

    @Test
    fun withoutTrialEligibilityNoTrialIsPromised() {
        compose.setContent { VaakTheme { PlanContent(paywall(trial = false), PlanActions()) } }
        compose.onAllNodes(hasText("days free", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Subscribe for ₹999 a year").assertExists()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun planFactsDuringTheTrialShowTheBannerAndManage() {
        var manage = 0
        var keep = 0
        val e = EntitlementSnapshot(premium = true, expiryMs = 1L, isTrial = true, autoRenewing = true, plan = Plan.YEARLY, priceLabel = "₹1,499")
        val state = PlanUiState(
            loading = false, snapshot = e, status = SubscriptionStatus.TRIAL, trialDay = 6,
            banner = TrialBannerUi(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 12), "₹1,499", Plan.YEARLY.period),
        )
        compose.setContent { VaakTheme { PlanContent(state, PlanActions(onManage = { manage++ }, onKeep = { keep++ })) } }
        compose.onNodeWithText("Your trial ends tomorrow").assertExists()
        compose.onNodeWithText("₹1,499 will be charged on 13 Nov for a year of Premium.").assertExists()
        compose.onNodeWithText("Day 6 of 7").assertExists()
        compose.onNodeWithText("Yearly · ₹1,499").assertExists()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithContentDescription("Manage subscription in Google Play").performClick()
        compose.onNodeWithText("Manage or cancel in Google Play").performScrollTo().performClick()
        assertThat(keep to manage).isEqualTo(1 to 2)
    }
}
