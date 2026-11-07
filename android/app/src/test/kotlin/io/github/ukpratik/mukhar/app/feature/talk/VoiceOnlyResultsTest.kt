package io.github.ukpratik.mukhar.app.feature.talk

import androidx.compose.ui.test.assertCountEquals
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
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_PROMPT_DONE_CTA
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_VOICE_ONLY
import io.github.ukpratik.mukhar.app.feature.talk.report.TAG_VOICE_ONLY_WRITE
import io.github.ukpratik.mukhar.app.feature.talk.report.VoiceOnlyReason
import io.github.ukpratik.mukhar.data.db.MetricsEntity
import io.github.ukpratik.mukhar.data.repo.StoredFeedback
import io.github.ukpratik.mukhar.data.repo.TalkStatus
import io.github.ukpratik.mukhar.domain.audio.AcousticAnalyzer
import io.github.ukpratik.mukhar.domain.audio.AcousticMetrics
import io.github.ukpratik.mukhar.domain.coach.AcousticCoach
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D-048 voice-only reports and US-079 "Mark as done" on the results screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceOnlyResultsTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = ReportActions({}, {}, {}, {})

    private fun voiceOnly(code: String = "AUDIO_ONLY:MODEL_NOT_INSTALLED", promptDone: Boolean = false) = run {
        val m = AcousticMetrics(182_000, 140_000, 170_000, 560, 197.6, 141.0, WpmBand.COMFORTABLE, 4, 1.4, 2_600)
        val f = AcousticCoach.coach(m, Lang.EN, null, emptyList())
        val details = TalkFixtures.details().let {
            it.copy(
                session = TalkFixtures.session(status = TalkStatus.FEEDBACK_READY, errorCode = code).copy(audioPath = "/x.aac"),
                words = null,
                metrics = MetricsEntity("s1", m.estWpm, m.wpmBand.key, 0, 0, 0.0, m.longPauses, m.longPausesPerMin, null, null, m.spanMs.toInt(), AcousticAnalyzer.ALGO_VERSION),
                feedback = StoredFeedback(f, AcousticCoach.VERSION, 0, false, false, null, false, model = AcousticCoach.MODEL),
            )
        }
        ReportMapper.map(details, TalkFixtures.AVERAGES, "Tell us about a project you're proud of.", false, 180).copy(promptDone = promptDone)
    }

    @Test
    fun mapperShowsPaceAndPausesAndMarksWordMetricsAsNeedingATranscript() {
        val s = voiceOnly()
        assertThat(s.phase).isEqualTo(ReportPhase.READY)
        assertThat(s.voiceOnly).isEqualTo(VoiceOnlyReason.NO_ENGINE)
        assertThat(s.transcript).isNull()
        val pace = s.tiles.first { it.kind == MetricKind.PACE }
        assertThat(pace.approx).isTrue()
        assertThat(pace.value).isEqualTo("141")
        assertThat(s.tiles.first { it.kind == MetricKind.PAUSES }.value).isEqualTo("1.4")
        assertThat(s.tiles.filter { it.needsTranscript }.map { it.kind }).containsExactly(MetricKind.FILLERS, MetricKind.VARIETY)
        assertThat(s.glow!!.note).contains("141")
        assertThat(ReportMapper.voiceOnlyReason("OFFLINE")).isEqualTo(VoiceOnlyReason.OFFLINE)
        assertThat(ReportMapper.voiceOnlyReason("AUDIO_MISSING")).isEqualTo(VoiceOnlyReason.AUDIO_GONE)
        assertThat(ReportMapper.voiceOnlyReason("BYOK_FAILED")).isEqualTo(VoiceOnlyReason.SERVICE)
    }

    @Test
    fun voiceOnlyCardOffersToWriteTheTranscriptAndAKey() {
        val events = mutableListOf<ReportEvent>()
        var keys = 0
        compose.setContent { MukharTheme { ReportContent(voiceOnly(), { events += it }, actions.copy(onKeys = { keys++ })) } }
        compose.onNodeWithTag(TAG_VOICE_ONLY).assertExists()
        compose.onAllNodesWithText("Needs a transcript", useUnmergedTree = true).assertCountEquals(2) // fillers and variety
        compose.onNodeWithTag(TAG_VOICE_ONLY_WRITE).performScrollTo().performClick()
        assertThat(events).contains(ReportEvent.WriteTranscript)
        compose.onNodeWithText("Add a speech-to-text key").performScrollTo().performClick()
        assertThat(keys).isEqualTo(1)
    }

    @Test
    fun aDeletedRecordingCantBeTranscribed() {
        compose.setContent { MukharTheme { ReportContent(voiceOnly(code = "AUDIO_ONLY:AUDIO_MISSING"), {}, actions) } }
        compose.onNodeWithTag(TAG_VOICE_ONLY).assertExists()
        compose.onNodeWithTag(TAG_VOICE_ONLY_WRITE).assertDoesNotExist()
    }

    @Test
    fun markAsDoneAndUndo() {
        val events = mutableListOf<ReportEvent>()
        compose.setContent { MukharTheme { ReportContent(TalkFixtures.report(), { events += it }, actions) } }
        compose.onNodeWithTag(TAG_PROMPT_DONE_CTA).performScrollTo().performClick()
        assertThat(events).contains(ReportEvent.SetPromptDone(true))
    }

    @Test
    fun aDonePromptShowsUndo() {
        val events = mutableListOf<ReportEvent>()
        compose.setContent { MukharTheme { ReportContent(TalkFixtures.report().copy(promptDone = true), { events += it }, actions) } }
        compose.onNodeWithText("Done. This prompt won't come back.").performScrollTo().assertExists()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        assertThat(events).contains(ReportEvent.SetPromptDone(false))
    }
}
