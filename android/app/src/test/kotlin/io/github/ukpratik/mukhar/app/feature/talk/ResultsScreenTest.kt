package io.github.ukpratik.mukhar.app.feature.talk

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportActions
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportContent
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportEvent
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportUiState
import io.github.ukpratik.mukhar.data.repo.TalkStatus
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S11 Results and S10 states on Robolectric (P0 flow: record → analysing → results with the fake pipeline). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResultsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<ReportEvent>()
    private var sayAgain: ReportUiState? = null
    private val actions = ReportActions(onBack = {}, onSayAgain = { sayAgain = it }, onRecordAgain = {}, onSettings = {})

    private fun show(state: ReportUiState) = compose.setContent { MukharTheme(darkTheme = false) { ReportContent(state, { events += it }, actions) } }

    @Test
    fun englishFullReport() {
        show(TalkFixtures.report())
        compose.onNodeWithText("Coach's report").assertExists()
        compose.onNodeWithText("A clear story with a strong middle. Let the ending land more cleanly.").assertExists()
        compose.onNodeWithText("Day 13 counted · weekly goal met").assertExists()
        for (label in listOf("PACE", "FILLERS", "LONG PAUSES", "REPEATS", "WORD VARIETY")) compose.onNodeWithText(label).assertExists()
        compose.onNodeWithText("Comfortable band", substring = true).assertExists()
        compose.onNodeWithText("Down from 9.0 avg").assertExists()
        compose.onNodeWithText("What you said").assertExists()
        compose.onNodeWithText("filler").assertExists() // legend
        compose.onNodeWithText("unsure").assertExists() // "Zuora" was low confidence
        compose.onNodeWithText("Glow · what worked").assertExists()
        compose.onNodeWithText("Grow · one thing to try").assertExists()
        compose.onNodeWithText("“I sat with the finance team every Tuesday and we mapped each report one by one.”").assertExists()
        compose.onNodeWithText("Better versions").assertExists()
        compose.onNodeWithText("3 lines").assertExists()
        compose.onAllNodesWithText("Save line").assertCountEquals(3)
        compose.onNodeWithText("Something off? Report this feedback").assertExists()
        compose.onNodeWithContentDescription("About Pace").assertExists()
        compose.onNodeWithContentDescription("Report this feedback").assertExists()

        compose.onNodeWithText("Say it again in 2:15").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(sayAgain!!.retakeCapS).isEqualTo(135)
        compose.onAllNodesWithText("Save line")[0].performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events.single()).isInstanceOf(ReportEvent.ToggleSave::class.java)
    }

    @Test
    fun hindiReportUsesDevanagariAndHindiSubtitles() {
        show(TalkFixtures.report(lang = Lang.HI))
        compose.onNodeWithText("सुझाव साफ़ और ठोस है। बस शुरुआत को थोड़ा कसिए।").assertExists()
        compose.onNodeWithText("Glow · क्या अच्छा रहा").assertExists() // one heading, Hindi part in a Hindi voice
        compose.onNodeWithText("Grow · एक चीज़ आज़माएँ").assertExists()
        compose.onNodeWithText("क्यों").assertExists()
        compose.onNodeWithText("कैसे").assertExists()
        compose.onNodeWithText("Comfortable · provisional", substring = true).assertExists()
        compose.onNodeWithText("English words like metro, office and traffic are a natural part of Hindi. We never count them as mistakes.").assertExists()
        compose.onNodeWithText("“उम्म, मतलब, metro तो आ गई है”").assertExists()
    }

    @Test
    fun stutterFriendlyModeHidesRepeatsAndPauses() {
        show(TalkFixtures.report(stutter = true))
        compose.onNodeWithText("Stutter-friendly: repeats and pauses aren't scored.").assertExists()
        compose.onAllNodesWithText("Not scored").assertCountEquals(2)
        compose.onAllNodesWithText("repeat").assertCountEquals(0)
    }

    @Test
    fun feedbackFailedKeepsTranscriptAndMetricsWithRetry() {
        show(TalkFixtures.report(status = TalkStatus.FEEDBACK_FAILED, withFeedback = false, errorCode = "LLM_FAILED"))
        compose.onNodeWithText("Your coach's notes didn't load").assertExists()
        compose.onNodeWithText("Your transcript and numbers are safe, and this didn't use today's talk.").assertExists()
        compose.onNodeWithText("Your numbers are in. Fillers are down from your average.").assertExists()
        compose.onNodeWithText("What you said").assertExists()
        compose.onNodeWithText("PACE").assertExists()
        compose.onAllNodesWithText("Glow · what worked").assertCountEquals(0)
        compose.onNodeWithText("Try again").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(ReportEvent.RetryFeedback)
    }

    @Test
    fun feedbackStillFailingShowsTheMetricsFallback() {
        show(TalkFixtures.report(status = TalkStatus.FEEDBACK_FAILED, withFeedback = false, errorCode = "LLM_FAILED", manualRetryFailed = true))
        compose.onNodeWithText("Try again later").assertExists()
        compose.onNodeWithText("Glow · what worked").assertExists()
        compose.onNodeWithText("Grow · one thing to try").assertExists()
    }

    @Test
    fun queuedOfflineState() {
        show(TalkFixtures.report(status = TalkStatus.QUEUED, withFeedback = false, errorCode = "OFFLINE"))
        compose.onNodeWithText("Talk saved").assertExists()
        compose.onNodeWithText("We'll analyse it when you're online").assertExists()
        compose.onNodeWithText("Waiting · 3:00").assertExists()
        compose.onNodeWithText("Discard this talk").assertExists()
    }

    @Test
    fun busyAndTranscriptionErrorStates() {
        show(TalkFixtures.report(status = TalkStatus.FAILED, withFeedback = false, errorCode = "STT_FAILED"))
        compose.onNodeWithText("We couldn't get your transcript this time").assertExists()
        compose.onNodeWithText("Writing your transcript · try again").assertExists()
        compose.onNodeWithText("Keep it for later").assertExists()
        compose.onNodeWithText("Try again").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(ReportEvent.RetryTranscript)
    }

    @Test
    fun busyState() {
        show(TalkFixtures.report(status = TalkStatus.QUEUED, withFeedback = false, errorCode = "BUSY"))
        compose.onNodeWithText("We're busy right now").assertExists()
        compose.onNodeWithText("We'll send one notification when it's ready. It won't use a free talk until then.").assertExists()
    }

    @Test
    fun noisyTalkShowsTheWarning() {
        show(TalkFixtures.report(snr = 8.0))
        compose.onNodeWithText("It's noisy — results may be less accurate").assertExists()
    }

    @Test
    fun reportSheetSendsOnlyWithAReason() {
        show(TalkFixtures.report())
        compose.onNodeWithContentDescription("Report this feedback").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Thanks for telling us. What was off?").assertExists()
        compose.onNodeWithText("Also send my transcript and this feedback", substring = true).assertExists()
        compose.onNodeWithText("The advice is wrong").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Send report").performSemanticsAction(SemanticsActions.OnClick)
        val sent = events.single() as ReportEvent.SendReport
        assertThat(sent.reason.key).isEqualTo("wrong_advice")
        assertThat(sent.consent).isFalse() // consent defaults off
    }
}
