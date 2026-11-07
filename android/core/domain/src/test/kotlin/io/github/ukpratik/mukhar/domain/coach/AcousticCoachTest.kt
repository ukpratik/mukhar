package io.github.ukpratik.mukhar.domain.coach

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.audio.AcousticMetrics
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.hasSpan
import io.github.ukpratik.mukhar.domain.metrics.Wpm
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test

class AcousticCoachTest {
    private fun m(wpm: Double, pauses: Int = 0, spanMs: Long = 120_000, lang: Lang = Lang.EN) = AcousticMetrics(
        durationMs = spanMs + 2_000, speechMs = spanMs * 8 / 10, spanMs = spanMs, syllables = (wpm * 1.4 * spanMs / 60_000).toInt(),
        syllablesPerMin = wpm * 1.4, estWpm = wpm, wpmBand = Wpm.band(wpm, lang), longPauses = pauses,
        longPausesPerMin = Wpm.perMinute(pauses, spanMs), longestPauseMs = if (pauses > 0) 2_400 else 800,
    )

    @Test
    fun fastSpeechGetsAPaceGrowAndNoQuotes() {
        val f = AcousticCoach.coach(m(190.0), Lang.EN, null, emptyList())
        assertThat(f.grow!!.issue).isEqualTo(GrowIssue.PACE)
        assertThat(f.grow!!.why).contains("190")
        assertThat(f.glow!!.hasSpan).isFalse()
        assertThat(f.grow!!.hasSpan).isFalse()
        assertThat(f.notes).contains(AnalysisNote.AUDIO_ONLY)
        assertThat(f.betterVersions).isEmpty()
    }

    @Test
    fun manyLongPausesGetAStructureGrow() {
        val f = AcousticCoach.coach(m(135.0, pauses = 8), Lang.EN, null, emptyList())
        assertThat(f.grow!!.issue).isEqualTo(GrowIssue.STRUCTURE)
        assertThat(f.glow!!.note).contains("135") // comfortable pace is the glow
    }

    @Test
    fun hindiCopyIsInHindiAndKeepsOtherNotes() {
        val f = AcousticCoach.coach(m(80.0, lang = Lang.HI), Lang.HI, null, listOf(AnalysisNote.STT_KEY_REJECTED))
        assertThat(f.summary).contains("आवाज़")
        assertThat(f.grow!!.issue).isEqualTo(GrowIssue.PACE)
        assertThat(f.notes).containsExactly(AnalysisNote.AUDIO_ONLY, AnalysisNote.STT_KEY_REJECTED).inOrder()
    }
}
