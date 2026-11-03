package io.github.ukpratik.mukhar.app.feature.onboarding.warmup

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test

/** The on-device warm-up meter: bands only, no network (US-002). */
class WarmupAnalyzerTest {
    /** [quietFrames] of noise at [noiseDb], then [speechFrames] at [speechDb]. */
    private fun analyse(words: Int, quietFrames: Int, speechFrames: Int, speechDb: Float, noiseDb: Float = -55f, lang: Lang = Lang.EN): WarmupResult {
        val a = WarmupAnalyzer(words, lang)
        repeat(quietFrames) { a.feedLevel(noiseDb) }
        repeat(speechFrames) { a.feedLevel(speechDb) }
        return a.result()
    }

    @Test
    fun comfortablePaceAndClearVolume() {
        // 45 words over ~18 s of speech = 150 wpm.
        val r = analyse(words = 45, quietFrames = 25, speechFrames = 900, speechDb = -24f)
        assertThat(r.enoughSpeech).isTrue()
        assertThat(r.pace).isEqualTo(PaceBand.COMFORTABLE)
        assertThat(r.volume).isEqualTo(VolumeBand.CLEAR)
    }

    @Test
    fun paceBandsUseTheKnownWordCount() {
        assertThat(analyse(45, 25, 900, -24f).pace).isEqualTo(PaceBand.COMFORTABLE) // 150 wpm
        assertThat(analyse(45, 25, 1500, -24f).pace).isEqualTo(PaceBand.SLOW) // 90 wpm
        assertThat(analyse(45, 25, 600, -24f).pace).isEqualTo(PaceBand.FAST) // 225 wpm
        // Hindi bands are 100 to 150: 155 wpm is fast in Hindi, comfortable in English.
        assertThat(analyse(45, 25, 870, -24f, lang = Lang.HI).pace).isEqualTo(PaceBand.FAST)
        assertThat(analyse(45, 25, 870, -24f, lang = Lang.EN).pace).isEqualTo(PaceBand.COMFORTABLE)
    }

    @Test
    fun volumeBands() {
        assertThat(analyse(45, 25, 900, -42f, noiseDb = -70f).volume).isEqualTo(VolumeBand.SOFT)
        assertThat(analyse(45, 25, 900, -8f).volume).isEqualTo(VolumeBand.LOUD)
        assertThat(analyse(45, 25, 900, -30f, noiseDb = -40f).volume).isEqualTo(VolumeBand.NOISY)
    }

    @Test
    fun underEightSecondsOfSpeechAsksForAnotherGo() {
        val r = analyse(words = 45, quietFrames = 25, speechFrames = 300, speechDb = -24f) // 6 s
        assertThat(r.enoughSpeech).isFalse()
    }

    @Test
    fun silenceIsNotSpeech() {
        val a = WarmupAnalyzer(45, Lang.EN)
        repeat(1000) { a.feed(ShortArray(320)) }
        assertThat(a.speechMs).isEqualTo(0)
    }

    @Test
    fun wordCountWorksForBothScripts() {
        assertThat(WarmupAnalyzer.countWords("Every morning I make a cup of tea.")).isEqualTo(8)
        assertThat(WarmupAnalyzer.countWords("हर सुबह चाय की खुशबू।")).isEqualTo(5)
    }
}
