package io.github.ukpratik.mukhar.app.feature.plan

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.plan.PlanFixtures.paywall
import io.github.ukpratik.mukhar.app.feature.plan.PlanFixtures.trialFacts
import io.github.ukpratik.mukhar.domain.billing.Plan
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S18 (US-061/062): real prices with per-month and per-week lines, the trial copy, Close always visible. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PlanContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun paywallShowsPricesTrialCopyAndClose() {
        var buy = 0
        var closed = 0
        var restored = 0
        compose.setContent { MukharTheme { PlanContent(paywall(), PlanActions(onBuy = { buy++ }, onClose = { closed++ }, onRestore = { restored++ })) } }
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
        // Restore now ends the scroll rather than sitting in the sticky bar.
        compose.onNodeWithText("Restore purchase").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertThat(listOf(buy, closed, restored)).containsExactly(1, 1, 1).inOrder()
    }

    @Test
    fun theTrialTimelineIsReadInFullUnderAVisibleHeading() {
        compose.setContent { MukharTheme { PlanContent(paywall(), PlanActions()) } }
        compose.onNode(hasText("How the free trial works") and isHeading()).performScrollTo()
        // No summary description hides the trial length, the reminder day, the charge day or the price.
        compose.onAllNodes(hasContentDescription("How the free trial works")).assertCountEquals(0)
        compose.onNode(hasText("Today · 7 days free") and hasText("Everything in Premium, no charge.")).performScrollTo()
        compose.onNode(hasText("Day 6 · we remind you") and hasText("A notification and a banner in the app, 1 day before.")).performScrollTo()
        compose.onNode(hasText("Day 7 · ₹999 charged") and hasText("Only if you don't cancel. Renews yearly.")).performScrollTo()
    }

    @Test
    fun choosingMonthlyUpdatesTheCta() {
        var picked: Plan? = null
        val s = paywall().copy(selected = Plan.MONTHLY)
        compose.setContent { MukharTheme { PlanContent(s, PlanActions(onSelect = { picked = it })) } }
        compose.onNodeWithText("7 days free, then ₹249 a month. We remind you 1 day before.").assertExists()
        compose.onNodeWithText("Yearly").performClick()
        assertThat(picked).isEqualTo(Plan.YEARLY)
    }

    @Test
    fun withoutTrialEligibilityNoTrialIsPromised() {
        compose.setContent { MukharTheme { PlanContent(paywall(trial = false), PlanActions()) } }
        compose.onAllNodes(hasText("days free", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Subscribe for ₹999 a year").assertExists()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun loadingAndStatusAreAnnounced() {
        compose.setContent { MukharTheme { PlanContent(PlanUiState(loading = true), PlanActions()) } }
        compose.onNodeWithContentDescription("Loading prices from Google Play").assertExists()
    }

    @Test
    fun confirmingIsAPoliteLiveRegionAndTheBarTitleIsAHeading() {
        compose.setContent { MukharTheme { PlanContent(paywall().copy(confirming = true), PlanActions()) } }
        compose.onNode(hasText("Confirming…") and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertExists()
        compose.onNode(isHeading() and hasText("Mukhar Premium")).assertExists()
    }

    @Test
    fun planFactsDuringTheTrialShowTheBannerAndManage() {
        var manage = 0
        var keep = 0
        compose.setContent { MukharTheme { PlanContent(trialFacts(), PlanActions(onManage = { manage++ }, onKeep = { keep++ })) } }
        compose.onNodeWithText("Your trial ends tomorrow").assertExists()
        compose.onNodeWithText("₹1,499 will be charged on 13 Nov for a year of Premium.").assertExists()
        // The progress bar carries "Day 6 of 7"; the caption under it is not read twice.
        compose.onNodeWithTag(TAG_TRIAL_PROGRESS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Day 6 of 7"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        compose.onAllNodes(hasText("Day 6 of 7")).assertCountEquals(0)
        compose.onNodeWithText("Yearly · ₹1,499").assertExists()
        compose.onNodeWithText("Keep").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Manage subscription in Google Play").performClick()
        compose.onNodeWithText("Manage or cancel in Google Play").performScrollTo().performClick()
        assertThat(keep to manage).isEqualTo(1 to 2)
    }
}
