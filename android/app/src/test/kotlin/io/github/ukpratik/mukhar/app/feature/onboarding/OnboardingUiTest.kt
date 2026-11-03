package io.github.ukpratik.mukhar.app.feature.onboarding

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.testing.OnboardingHarness
import io.github.ukpratik.mukhar.data.prefs.AgeGate
import io.github.ukpratik.mukhar.data.prefs.Settings
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI tests (Robolectric) for the P0 onboarding flows: age gate and the prominent disclosure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class OnboardingUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val h = OnboardingHarness()

    @After
    fun tearDown() = h.close()

    private fun settings(): Settings = runBlocking { h.settings.current() }

    private fun show() {
        compose.setContent { MukharTheme { OnboardingScreen(h.vm, onFinished = {}, onExit = {}) } }
    }

    private fun waitForText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun ageGateUnder18IsAKindFinalStopThatStoresNothing() {
        show()
        waitForText("First, which year were you born?")
        compose.onNodeWithContentDescription("Onboarding step 1 of 6").assertExists()
        compose.onNodeWithTag(TAG_CONTINUE).assertIsNotEnabled()
        compose.onNodeWithTag(TAG_YEAR).performTextInput("2012")
        compose.onNodeWithTag(TAG_CONTINUE).performClick()

        waitForText("Mukhar is for 18+ for now")
        compose.onNodeWithText("We haven't saved anything you entered.").assertExists()
        compose.onNodeWithText("Close Mukhar").assertExists()
        compose.onNodeWithText("Why 18+?").assertExists()
        compose.waitUntil(5_000) { settings().ageGate == AgeGate.BLOCKED }
        assertThat(settings()).isEqualTo(Settings(ageGate = AgeGate.BLOCKED))
    }

    @Test
    fun ageGateAdultContinuesToLanguages() {
        show()
        waitForText("First, which year were you born?")
        compose.onNodeWithTag(TAG_YEAR).performTextInput("1998")
        compose.onNodeWithTag(TAG_CONTINUE).performClick()
        waitForText("Let's make it yours")
        compose.onNodeWithText("Pick at least one goal").assertIsNotEnabled()
    }

    @Test
    fun disclosureSwitchIsOffAndAgreeDoesNotEnableAnalytics() {
        h.resumeAt(OnboardingStep.DISCLOSURE)
        show()
        waitForText("What leaves your phone")
        compose.onNodeWithText("Your audio goes to a speech service").assertExists()
        compose.onNodeWithText("Nothing is stored on our server").assertExists()
        compose.onNodeWithTag(TAG_ANALYTICS).performScrollTo().assertIsOff()

        compose.onNodeWithTag(TAG_AGREE).performClick()
        io.github.ukpratik.mukhar.app.testing.waitFor { settings().disclosureConsentAt != null }
        assertThat(settings().analyticsConsent).isFalse()
        waitForText("Record your starting point")
    }

    @Test
    fun theAnalyticsSwitchIsIndependentOfAgree() {
        h.resumeAt(OnboardingStep.DISCLOSURE)
        show()
        waitForText("What leaves your phone")
        compose.onNodeWithTag(TAG_ANALYTICS).performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Not now").performClick()
        waitForText("No problem. Your call.")
        assertThat(settings().disclosureConsentAt).isNull()
        assertThat(settings().analyticsConsent).isTrue()
    }

    @Test
    fun thePrivacyPolicyRowOpensThePolicy() {
        var opened = 0
        compose.setContent { MukharTheme { DisclosureBody(analyticsOptIn = false, onAnalytics = {}, onOpenPolicy = { opened++ }) } }
        compose.onNodeWithTag(TAG_POLICY).assertHasClickAction().performClick()
        assertThat(opened).isEqualTo(1)
        // The analytics switch reads its body too (consent copy is never hidden).
        compose.onNodeWithTag(TAG_ANALYTICS).assertContentDescriptionEquals(
            "Share usage and crash reports (optional). Optional. Helps us fix bugs. No audio or transcripts.",
        )
    }

    @Test
    fun micDeniedOffersRetrySettingsAndContinue() {
        h.resumeAt(OnboardingStep.MIC_DENIED)
        show()
        waitForText("Mukhar listens to coach you")
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Open settings").assertExists()
        compose.onNodeWithText("Continue without mic").performClick()
        waitForText("Make it part of your day")
    }

    @Test
    fun reminderPreviewIsTheExactNotification() {
        h.resumeAt(OnboardingStep.REMINDER)
        show()
        waitForText("Make it part of your day")
        compose.onNodeWithContentDescription("Notification preview: After your morning tea: today's talk is ready ☕").assertExists()
        compose.onNodeWithText("lunch").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Notification preview: After your lunch: today's talk is ready")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Onboarding step 6 of 6").assertExists()
    }
}
