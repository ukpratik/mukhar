package com.vaak.app.feature.plan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.vaak.app.feature.today.TodayActions
import com.vaak.app.feature.today.TodayContent
import com.vaak.app.feature.today.TodayUiState
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.domain.billing.Plan
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** US-062: the day-6 banner on Today is the guarantee, with Keep and Manage; Keep hides it for the day. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TrialBannerTodayTest {
    @get:Rule
    val compose = createComposeRule()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val expiry = Instant.parse("2026-11-13T04:30:00Z")
    private val trial = EntitlementSnapshot(true, expiry.toEpochMilli(), isTrial = true, autoRenewing = true, plan = Plan.YEARLY, priceLabel = "₹1,499")

    @Test
    fun bannerRulesFromDay6() {
        val day5 = expiry.minus(Duration.ofHours(49))
        val day6 = expiry.minus(Duration.ofHours(30))
        assertThat(TrialBannerUi.from(trial, day5, zone, null)).isNull()
        val b = TrialBannerUi.from(trial, day6, zone, null)!!
        assertThat(b.expiry).isEqualTo(LocalDate.of(2026, 11, 13))
        assertThat(b.today).isEqualTo(LocalDate.of(2026, 11, 12))
        // Keep dismisses it for that day only.
        assertThat(TrialBannerUi.from(trial, day6, zone, LocalDate.of(2026, 11, 12))).isNull()
        assertThat(TrialBannerUi.from(trial, expiry.minusSeconds(60), zone, LocalDate.of(2026, 11, 12))).isNotNull()
        // Already cancelled: no banner.
        assertThat(TrialBannerUi.from(trial.copy(autoRenewing = false), day6, zone, null)).isNull()
    }

    @Test
    fun todayShowsTheBannerWithKeepAndManage() {
        var keep = 0
        var manage = 0
        val banner = TrialBannerUi.from(trial, expiry.minus(Duration.ofHours(30)), zone, null)
        compose.setContent {
            VaakTheme {
                TodayContent(TodayUiState(loading = false, trialBanner = banner), TodayActions(onKeepTrial = { keep++ }, onManageTrial = { manage++ }))
            }
        }
        compose.onNodeWithText("Your trial ends tomorrow").assertExists()
        compose.onNodeWithText("₹1,499 will be charged on 13 Nov for a year of Premium.").assertExists()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithText("Manage").performClick()
        assertThat(keep to manage).isEqualTo(1 to 1)
    }

    @Test
    fun noBannerWithoutATrial() {
        compose.setContent { VaakTheme { TodayContent(TodayUiState(loading = false), TodayActions()) } }
        compose.onAllNodes(hasText("Your trial ends", substring = true)).assertCountEquals(0)
    }
}
