package io.github.ukpratik.mukhar.app.feature.quota

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/** S17 (US-060): Close always visible, "Come back tomorrow" primary, Premium secondary, the local reset time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class QuotaContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun freeTalkUsedOffersTomorrowFirstAndPremiumSecond() {
        var closed = 0
        var premium = 0
        var tomorrow = 0
        compose.setContent {
            MukharTheme {
                QuotaContent(
                    QuotaUiState(loading = false, opensAt = LocalTime.of(3, 0), due = 6, checksLeft = 7, checksLimit = 10),
                    QuotaActions(onClose = { closed++ }, onTomorrow = { tomorrow++ }, onPremium = { premium++ }),
                )
            }
        }
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithText("That's today's free talk done").assertExists()
        compose.onNodeWithTag(TAG_QUOTA_RESET).assertExists()
        compose.onNodeWithText("Next free talk opens at 3:00 am", ignoreCase = true).assertExists()
        compose.onNodeWithText("6 due").assertExists()
        compose.onNodeWithText("7 of 10 checks left").assertExists()
        compose.onNodeWithText("Come back tomorrow").assertIsDisplayed().performClick()
        compose.onNodeWithText("Try Premium free for 7 days").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertThat(listOf(tomorrow, premium, closed)).containsExactly(1, 1, 1).inOrder()
    }

    @Test
    fun outsideIstTheResetShowsInLocalTimeAndAWaitingTalkIsExplained() {
        compose.setContent {
            MukharTheme {
                QuotaContent(QuotaUiState(loading = false, opensAt = LocalTime.of(5, 30), checksLeft = 10, checksLimit = 10, talkWaiting = true), QuotaActions())
            }
        }
        compose.onNodeWithText("Next free talk opens at 5:30 am", ignoreCase = true).assertExists()
        compose.onNodeWithText("Your latest talk is saved", substring = true).assertExists()
        compose.onAllNodes(hasText("Revise")).assertCountEquals(0) // nothing due → row hidden
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun premiumFairUseHasNoUpsell() {
        compose.setContent { MukharTheme { QuotaContent(QuotaUiState(loading = false, premium = true), QuotaActions()) } }
        compose.onAllNodes(hasText("Try Premium free for 7 days")).assertCountEquals(0)
        compose.onNodeWithText("Come back tomorrow").assertExists()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }
}
