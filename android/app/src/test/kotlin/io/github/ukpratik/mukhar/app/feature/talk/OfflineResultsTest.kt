package io.github.ukpratik.mukhar.app.feature.talk

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.talk.report.MetricKind
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportActions
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportContent
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportEvent
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportMapper
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportPhase
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_ANALYSIS_NOTES
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_GRAMMAR_NUDGE
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_NEEDS_KEY
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_NEEDS_MODEL
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_OFFLINE_CHIP
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_OFFLINE_EXPLAIN
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_WORD_UPGRADES
import io.github.ukpratik.mukhar.data.debug.CannedTalks
import io.github.ukpratik.mukhar.data.repo.StoredFeedback
import io.github.ukpratik.mukhar.data.repo.TalkStatus
import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.coach.RuleCoach
import io.github.ukpratik.mukhar.domain.coach.SynonymBook
import io.github.ukpratik.mukhar.domain.coach.SynonymEntry
import io.github.ukpratik.mukhar.domain.feedback.Alternative
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-008 results: offline chip, ≈ metrics, rule-coach notes with word upgrades, notes, and "needs an engine". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineResultsTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = ReportActions({}, {}, {}, {})

    /** A talk transcribed on the phone with the offline coach's notes, as TalkRepository would store it. */
    private fun offlineDetails(notes: List<AnalysisNote> = emptyList()): io.github.ukpratik.mukhar.data.repo.TalkDetails {
        val base = TalkFixtures.details()
        val words = base.words!!
        val book = SynonymBook(
            Lang.EN,
            listOf(SynonymEntry(words.first { it.conf >= 0.6 && it.w.length > 3 }.w, listOf(Alternative("splendid", "very good", Register.FORMAL, "A splendid result.")))),
        )
        val fb = RuleCoach.coach(RuleCoach.Input(words, Lang.EN, MetricsCalculator.compute(words, Lang.EN), TalkFixtures.AVERAGES, 180_000, false, book, notes))
        val upgraded = fb.copy(
            wordUpgrades = listOf(io.github.ukpratik.mukhar.domain.feedback.WordUpgrade("good", 4, 0, listOf(Alternative("splendid", "very good", Register.FORMAL, "A splendid result.")))),
        )
        return base.copy(
            transcriptProvider = "vosk",
            feedback = StoredFeedback(upgraded, RuleCoach.VERSION, 0, false, false, null, false, model = RuleCoach.MODEL),
        )
    }

    private fun offlineState(notes: List<AnalysisNote> = emptyList()) =
        ReportMapper.map(offlineDetails(notes), TalkFixtures.AVERAGES, "Prompt", false, 180)

    @Test
    fun mapperMarksOfflineAnalysisAndApproxMetrics() {
        val s = offlineState()
        assertThat(s.phase).isEqualTo(ReportPhase.READY)
        assertThat(s.offline).isTrue()
        assertThat(s.ruleNotes).isTrue()
        assertThat(s.tiles.first { it.kind == MetricKind.PACE }.approx).isFalse()
        assertThat(s.tiles.first { it.kind == MetricKind.FILLERS }.approx).isTrue()
        assertThat(s.glow!!.quote).isNull() // metrics-based, no quote to tap
        assertThat(s.upgrades.single().alternatives.single().word).isEqualTo("splendid")
        assertThat(s.better).isEmpty()
        // A cloud/own-key transcript is exact.
        val exact = TalkFixtures.report()
        assertThat(exact.offline).isFalse()
        assertThat(exact.ruleNotes).isFalse()
        assertThat(exact.tiles.none { it.approx }).isTrue()
    }

    @Test
    fun needsEngineIsItsOwnState() {
        assertThat(ReportMapper.phase(TalkStatus.FAILED, "MODEL_NOT_INSTALLED", false, io.github.ukpratik.mukhar.domain.feedback.Safety.NONE))
            .isEqualTo(ReportPhase.NEEDS_ENGINE)
        assertThat(ReportMapper.phase(TalkStatus.FAILED, "STT_FAILED", false, io.github.ukpratik.mukhar.domain.feedback.Safety.NONE))
            .isEqualTo(ReportPhase.STT_FAILED)
    }

    @Test
    fun offlineResultsShowTheChipApproxValuesUpgradesAndTheKeyNudge() {
        var keys = 0
        val events = mutableListOf<ReportEvent>()
        compose.setContent { MukharTheme { ReportContent(offlineState(), { events += it }, actions.copy(onKeys = { keys++ })) } }
        compose.onNodeWithTag(TAG_OFFLINE_CHIP).performScrollTo().performClick()
        compose.onNodeWithTag(TAG_OFFLINE_EXPLAIN).assertExists()
        assertThat(compose.onAllNodesWithText("≈", substring = true).fetchSemanticsNodes()).isNotEmpty()
        compose.onNodeWithTag(TAG_WORD_UPGRADES).performScrollTo()
        compose.onNodeWithText("You said \"good\" 4 times").assertExists()
        compose.onNodeWithText("splendid · formal").performScrollTo().performClick()
        assertThat((events.single() as ReportEvent.ToggleWord).word.word).isEqualTo("splendid")
        compose.onNodeWithTag(TAG_GRAMMAR_NUDGE).performScrollTo()
        compose.onNodeWithText("Add your own AI key").performClick()
        assertThat(keys).isEqualTo(1)
    }

    @Test
    fun fallbackNotesAreNeverSilent() {
        compose.setContent { MukharTheme { ReportContent(offlineState(listOf(AnalysisNote.LLM_KEY_REJECTED)), {}, actions) } }
        compose.onNodeWithTag(TAG_ANALYSIS_NOTES).performScrollTo()
        compose.onNodeWithText("Your Anthropic key wasn't accepted", substring = true).assertExists()
    }

    @Test
    fun noEngineShowsAFriendlySavedStateWithBothWaysForward() {
        var models = 0
        var keys = 0
        val events = mutableListOf<ReportEvent>()
        val state = TalkFixtures.report(status = TalkStatus.FAILED, errorCode = "MODEL_NOT_INSTALLED", withFeedback = false)
        assertThat(state.phase).isEqualTo(ReportPhase.NEEDS_ENGINE)
        compose.setContent { MukharTheme { ReportContent(state, { events += it }, actions.copy(onAnalysisSettings = { models++ }, onKeys = { keys++ })) } }
        compose.onNodeWithText("Install the offline speech model or add your own key to analyse this talk.").assertExists()
        compose.onNodeWithTag(TAG_NEEDS_MODEL).performScrollTo().performClick()
        compose.onNodeWithTag(TAG_NEEDS_KEY).performScrollTo().performClick()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertThat(models).isEqualTo(1)
        assertThat(keys).isEqualTo(1)
        assertThat(events).containsExactly(ReportEvent.Reanalyse)
    }

    @Suppress("unused")
    private val keep = CannedTalks
}
