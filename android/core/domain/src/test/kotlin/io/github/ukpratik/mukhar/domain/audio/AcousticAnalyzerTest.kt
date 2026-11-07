package io.github.ukpratik.mukhar.domain.audio

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class AcousticAnalyzerTest {

    /** Fixtures: 10 ms dBFS levels of synthesised talks (75 words each, -60 dBFS noise added), see the fixture files. */
    private fun fixture(name: String): FloatArray =
        javaClass.getResource("/acoustic/$name.levels")!!.readText().lines().filter { it.isNotBlank() }.map { it.toFloat() }.toFloatArray()

    private fun trueWpm(levels: FloatArray, words: Int): Double {
        val r = AcousticAnalyzer.analyze(levels, Lang.EN)
        return words / (r.spanMs / 60_000.0)
    }

    @Test
    fun englishPaceIsWithinTenPercent() {
        for (name in listOf("en", "en_fast")) {
            val levels = fixture(name)
            val r = AcousticAnalyzer.analyze(levels, Lang.EN)
            val truth = trueWpm(levels, 75)
            assertThat(abs(r.estWpm - truth) / truth).isLessThan(0.10)
        }
    }

    @Test
    fun hindiPaceIsWithinTenPercent() {
        val levels = fixture("hi")
        val r = AcousticAnalyzer.analyze(levels, Lang.HI)
        val truth = 75 / (r.spanMs / 60_000.0)
        assertThat(abs(r.estWpm - truth) / truth).isLessThan(0.10)
    }

    /** Syllable-like bursts: [rate] per second, for [seconds], then [gapMs] of noise, repeated [blocks] times. */
    private fun bursts(rate: Double, seconds: Double, blocks: Int, gapMs: Int): ShortArray {
        val sr = AcousticAnalyzer.SAMPLE_RATE
        val random = Random(3)
        val out = ArrayList<Short>()
        fun noise(n: Int) = repeat(n) { out += (random.nextInt(-30, 31)).toShort() }
        noise(sr / 2)
        repeat(blocks) {
            val n = (seconds * sr).toInt()
            for (i in 0 until n) {
                val env = sin(PI * ((i * rate / sr) % 1.0)) // one hump per syllable
                val carrier = sin(2 * PI * 180 * i / sr)
                out += (env * env * carrier * 9_000 + random.nextInt(-30, 31)).toInt().toShort()
            }
            noise(gapMs * sr / 1000)
        }
        return out.toShortArray()
    }

    @Test
    fun countsSyllableBurstsAndLongPauses() {
        val a = AcousticAnalyzer()
        val pcm = bursts(rate = 4.0, seconds = 10.0, blocks = 3, gapMs = 2_000)
        pcm.toList().chunked(777).forEach { a.feed(it.toShortArray()) } // odd chunk sizes
        val r = a.result(Lang.EN)
        assertThat(r.syllables).isIn(108..122) // 120 bursts
        assertThat(r.longPauses).isEqualTo(2)
        assertThat(r.longestPauseMs).isAtLeast(1_900)
        assertThat(r.speechMs).isIn(28_000L..31_000L)
    }

    @Test
    fun shortGapsInsideSpeechAreNotPauses() {
        val r = AcousticAnalyzer().apply { feed(bursts(4.0, 5.0, 4, 600)) }.result(Lang.EN)
        assertThat(r.longPauses).isEqualTo(0)
    }

    @Test
    fun silenceGivesNothing() {
        val r = AcousticAnalyzer().apply { feed(ShortArray(16_000 * 5)) }.result(Lang.EN)
        assertThat(r.syllables).isEqualTo(0)
        assertThat(r.estWpm).isEqualTo(0.0)
        assertThat(r.wpmBand).isEqualTo(WpmBand.SLOW)
        assertThat(r.durationMs).isEqualTo(5_000)
    }
}
