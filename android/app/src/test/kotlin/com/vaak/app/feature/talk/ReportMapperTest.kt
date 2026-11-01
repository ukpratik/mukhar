package com.vaak.app.feature.talk

import com.google.common.truth.Truth.assertThat
import com.vaak.app.feature.talk.report.MetricDelta
import com.vaak.app.feature.talk.report.MetricKind
import com.vaak.app.feature.talk.report.ReportMapper
import com.vaak.app.feature.talk.report.ReportPhase
import com.vaak.data.repo.TalkCodes
import com.vaak.data.repo.TalkStatus
import com.vaak.domain.feedback.Safety
import com.vaak.domain.metrics.WpmBand
import com.vaak.domain.model.Lang
import org.junit.Test

/** Room rows → S10/S11 state (progressive reveal, fallbacks, stutter-friendly). Pure JVM. */
class ReportMapperTest {
    @Test
    fun phasesFollowTheSessionStatus() {
        assertThat(ReportMapper.phase(TalkStatus.UPLOADING, null, false, Safety.NONE)).isEqualTo(ReportPhase.LISTENING)
        assertThat(ReportMapper.phase(TalkStatus.QUEUED, "OFFLINE", false, Safety.NONE)).isEqualTo(ReportPhase.QUEUED)
        assertThat(ReportMapper.phase(TalkStatus.QUEUED, "BUSY", false, Safety.NONE)).isEqualTo(ReportPhase.BUSY)
        assertThat(ReportMapper.phase(TalkStatus.FAILED, "STT_FAILED", false, Safety.NONE)).isEqualTo(ReportPhase.STT_FAILED)
        assertThat(ReportMapper.phase(TalkStatus.FAILED, "TOO_LITTLE_SPEECH", false, Safety.NONE)).isEqualTo(ReportPhase.TOO_LITTLE)
        assertThat(ReportMapper.phase(TalkStatus.TRANSCRIBED, null, true, Safety.NONE)).isEqualTo(ReportPhase.METRICS_READY)
        assertThat(ReportMapper.phase(TalkStatus.FEEDBACK_FAILED, "LLM_FAILED", true, Safety.NONE)).isEqualTo(ReportPhase.FEEDBACK_FAILED)
        assertThat(ReportMapper.phase(TalkStatus.FEEDBACK_READY, null, true, Safety.SELF_HARM)).isEqualTo(ReportPhase.HELPLINE)
        assertThat(ReportMapper.phase(TalkStatus.RECORDED, TalkCodes.RECOVERED, false, Safety.NONE)).isEqualTo(ReportPhase.QUEUED)
    }

    @Test
    fun metricsArriveBeforeTheNotes() {
        val s = TalkFixtures.report(status = TalkStatus.TRANSCRIBED, withFeedback = false)
        assertThat(s.phase).isEqualTo(ReportPhase.METRICS_READY)
        assertThat(s.tiles.map { it.kind }).containsExactly(MetricKind.PACE, MetricKind.FILLERS, MetricKind.PAUSES, MetricKind.REPEATS, MetricKind.VARIETY).inOrder()
        assertThat(s.transcript).isNotNull()
        assertThat(s.glow).isNull()
    }

    @Test
    fun fullEnglishReport() {
        val s = TalkFixtures.report()
        assertThat(s.phase).isEqualTo(ReportPhase.READY)
        assertThat(s.summary).startsWith("A clear story")
        assertThat(s.glow!!.quote).startsWith("I sat with")
        assertThat(s.better).hasSize(3)
        val t = s.transcript!!
        assertThat(t.hasFiller).isTrue()
        assertThat(t.hasUnsure).isTrue() // "Zuora"
        assertThat(t.tokens.filter { it.glow }.joinToString(" ") { it.text }).startsWith("I sat with the finance team")
        assertThat(t.paragraphs.size).isAtLeast(3)
        assertThat(s.retakeCapS).isEqualTo(135) // 3:00 → 2:15
        assertThat(s.credit!!.day).isEqualTo(13)
        val fillers = s.tiles.first { it.kind == MetricKind.FILLERS }
        assertThat(fillers.delta).isInstanceOf(MetricDelta.Down::class.java)
    }

    @Test
    fun hindiPaceIsProvisionalAndVarietyComparesHindiOnly() {
        val s = TalkFixtures.report(lang = Lang.HI)
        val pace = s.tiles.first { it.kind == MetricKind.PACE }
        assertThat((pace.delta as MetricDelta.Band).provisional).isTrue()
        val variety = s.tiles.first { it.kind == MetricKind.VARIETY }
        assertThat(variety.locked || variety.delta == MetricDelta.HindiProvisional).isTrue()
    }

    @Test
    fun stutterFriendlyHidesRepeatsAndPauses() {
        val s = TalkFixtures.report(stutter = true)
        assertThat(s.tiles.filter { it.off }.map { it.kind }).containsExactly(MetricKind.REPEATS, MetricKind.PAUSES)
        assertThat(s.transcript!!.hasRepeat).isFalse()
    }

    @Test
    fun feedbackFailureShowsFallbackOnlyAfterTheManualRetryFails() {
        val first = TalkFixtures.report(status = TalkStatus.FEEDBACK_FAILED, withFeedback = false, errorCode = "LLM_FAILED")
        assertThat(first.phase).isEqualTo(ReportPhase.FEEDBACK_FAILED)
        assertThat(first.glow).isNull()
        assertThat(first.fillersDown).isTrue()
        val after = TalkFixtures.report(status = TalkStatus.FEEDBACK_FAILED, withFeedback = false, errorCode = "LLM_FAILED", manualRetryFailed = true)
        assertThat(after.glow!!.quote).isNull() // metrics-based fallback
        assertThat(after.grow!!.why).isNotEmpty()
    }

    @Test
    fun noisyTalksGetTheBanner() {
        assertThat(TalkFixtures.report(snr = 9.0).noisy).isTrue()
        assertThat(TalkFixtures.report(snr = 25.0).noisy).isFalse()
    }

    @Test
    fun bandMarkerSitsInTheComfortableBand() {
        val p = ReportMapper.bandPosition(138.0, 110.0, 160.0)
        assertThat(p).isWithin(0.01f).of(0.48f)
        assertThat(ReportMapper.bandPosition(80.0, 110.0, 160.0)).isLessThan(0.25f)
        assertThat(ReportMapper.bandPosition(200.0, 110.0, 160.0)).isGreaterThan(0.667f)
        assertThat(WpmBand.COMFORTABLE.key).isEqualTo("comfortable")
    }
}
