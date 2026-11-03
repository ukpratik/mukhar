package io.github.ukpratik.mukhar.app.feature.talk

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.intl.LocaleList
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordContent
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordPhase
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordUiState
import io.github.ukpratik.mukhar.app.feature.talk.record.TAG_RECORD_ANNOUNCER
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportActions
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportContent
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportUiState
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_REPORT_ANNOUNCER
import io.github.ukpratik.mukhar.data.repo.TalkStatus
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack semantics for the talk flow (NFR-008, design-system §10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TalkA11yTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = ReportActions(onBack = {}, onSayAgain = {}, onRecordAgain = {}, onSettings = {})

    /** Reduced motion on: spinners, shimmer and the recording dot stand still, so the screens go idle. */
    @Before
    fun stillMotion() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun showReport(state: ReportUiState) {
        compose.setContent { MukharTheme(darkTheme = false) { ReportContent(state, {}, actions) } }
        compose.waitForIdle()
    }

    private fun update(change: () -> Unit) {
        compose.runOnIdle(change)
        compose.waitForIdle()
    }

    private fun hasState(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun announcer(tag: String): String? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test
    fun transcriptReadsMarkersInsteadOfColours() {
        showReport(TalkFixtures.report())
        assertThat(compose.onAllNodes(hasText("(filler)", substring = true)).fetchSemanticsNodes()).isNotEmpty()
        compose.onNode(hasText("Zuora (unsure)", substring = true)).assertExists()
    }

    @Test
    fun hindiTranscriptCarriesTheHindiLocale() {
        showReport(TalkFixtures.report(lang = Lang.HI))
        val node = compose.onAllNodes(hasText("(filler)", substring = true)).fetchSemanticsNodes().first()
        val spoken = node.config[SemanticsProperties.Text].first()
        assertThat(spoken.spanStyles.any { it.item.localeList == LocaleList("hi") }).isTrue()
    }

    @Test
    fun recordingAnnouncerIsOneStableNode() {
        val prompt = io.github.ukpratik.mukhar.domain.prompt.Prompt(
            "en-002", Lang.EN, setOf(Track.PROFESSIONAL), io.github.ukpratik.mukhar.domain.prompt.Difficulty.MEDIUM, "Tell us about a project.", null, emptyList(), null,
        )
        var state by mutableStateOf(RecordUiState(phase = RecordPhase.RECORDING, prompt = prompt, capMs = 180_000, elapsedMs = 4_000, pausesLeft = 2))
        compose.setContent { MukharTheme(darkTheme = false) { RecordContent(state, {}) } }
        compose.waitForIdle()
        assertThat(announcer(TAG_RECORD_ANNOUNCER)).isEqualTo("Recording started")
        val node = compose.onNodeWithTag(TAG_RECORD_ANNOUNCER).fetchSemanticsNode().id

        update { state = state.copy(phase = RecordPhase.PAUSED, pausesLeft = 1) }
        assertThat(announcer(TAG_RECORD_ANNOUNCER)).isEqualTo("Recording paused")
        assertThat(compose.onNodeWithTag(TAG_RECORD_ANNOUNCER).fetchSemanticsNode().id).isEqualTo(node)

        update { state = state.copy(phase = RecordPhase.RECORDING, elapsedMs = 40_000) }
        assertThat(announcer(TAG_RECORD_ANNOUNCER)).isEqualTo("Recording resumed")

        update { state = state.copy(elapsedMs = 61_000) }
        assertThat(announcer(TAG_RECORD_ANNOUNCER)).isEqualTo("Recording. 1:00 of 3:00.")

        update { state = state.copy(phase = RecordPhase.THINK, thinkLeft = 18) }
        assertThat(announcer(TAG_RECORD_ANNOUNCER)).isEqualTo("20 seconds to think. Recording starts by itself.")
        // The timer is not a heading any more.
        assertThat(compose.onNodeWithText("0:18").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Heading)).isNull()
    }

    @Test
    fun analysingStepsHaveStates() {
        showReport(TalkFixtures.report(status = TalkStatus.UPLOADING, withFeedback = false))
        compose.onNode(hasText("Saved on your phone", substring = true) and hasState("Done")).assertExists()
        compose.onNode(hasText("Writing your transcript") and hasState("In progress")).assertExists()
        compose.onNode(hasText("Measuring pace and fillers") and hasState("Not started")).assertExists()
        assertThat(announcer(TAG_REPORT_ANNOUNCER)).isEqualTo("Writing your transcript")
        // Reduced motion: no looping spinner anywhere (a still hourglass instead).
        assertThat(compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun reportPhaseIsAnnouncedFromOneNode() {
        var state by mutableStateOf(TalkFixtures.report(status = TalkStatus.TRANSCRIBED, withFeedback = false))
        compose.setContent { MukharTheme(darkTheme = false) { ReportContent(state, {}, actions) } }
        compose.waitForIdle()
        assertThat(announcer(TAG_REPORT_ANNOUNCER)).isEqualTo("Transcript ready. Writing your notes")
        update { state = TalkFixtures.report() }
        assertThat(announcer(TAG_REPORT_ANNOUNCER)).isEqualTo("Your feedback is ready")
        update { state = TalkFixtures.report(status = TalkStatus.FEEDBACK_FAILED, withFeedback = false, errorCode = "LLM_FAILED") }
        assertThat(announcer(TAG_REPORT_ANNOUNCER)).isEqualTo("Notes couldn't load. Your numbers and transcript are here")
    }

    @Test
    fun retryWithOnlyASpinnerSaysTryingAgain() {
        showReport(TalkFixtures.report(status = TalkStatus.FEEDBACK_FAILED, withFeedback = false, errorCode = "LLM_FAILED").copy(retrying = true))
        compose.onNode(hasContentDescription("Trying again")).assertExists()
    }

    @Test
    fun metricTileIsOneSpokenSentence() {
        showReport(TalkFixtures.report())
        compose.onNode(hasContentDescription("Pace. ", substring = true) and hasContentDescription("words per minute. Comfortable band", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Fillers. ", substring = true) and hasContentDescription("per minute. Down from 9.0 avg. Better than your average", substring = true))
            .assertExists()
        // The ⓘ stays its own button.
        compose.onNode(hasContentDescription("About Pace")).assertExists()
    }

    @Test
    fun quoteIsAButtonThatSaysWhereItGoes() {
        showReport(TalkFixtures.report())
        val node = compose.onNodeWithText("“I sat with the finance team every Tuesday and we mapped each report one by one.”").fetchSemanticsNode()
        assertThat(node.config[SemanticsProperties.Role]).isEqualTo(Role.Button)
        assertThat(node.config[SemanticsActions.OnClick].label).isEqualTo("Show in transcript")
    }

    @Test
    fun expandRowHasAState() {
        showReport(TalkFixtures.report())
        val rows = compose.onAllNodes(hasText("Show full transcript")).fetchSemanticsNodes()
        if (rows.isNotEmpty()) {
            assertThat(rows.first().config[SemanticsProperties.StateDescription]).isEqualTo("Collapsed")
            assertThat(rows.first().config[SemanticsProperties.Role]).isEqualTo(Role.Button)
        }
    }

    @Test
    fun feedbackTagsAreHeadings() {
        showReport(TalkFixtures.report())
        val tag = compose.onNodeWithText("Glow · what worked").fetchSemanticsNode()
        assertThat(tag.config.getOrNull(SemanticsProperties.Heading)).isNotNull()
    }
}
