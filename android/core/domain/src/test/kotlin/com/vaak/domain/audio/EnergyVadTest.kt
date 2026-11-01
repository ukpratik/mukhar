package com.vaak.domain.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic 16 kHz PCM: low noise, tone bursts as "speech" (LLD §4.1, §8 "VAD on synthetic PCM"). */
class EnergyVadTest {
    private val rnd = Random(42)

    private fun noise(frames: Int, amp: Int = 60) = List(frames) { ShortArray(EnergyVad.FRAME_SAMPLES) { rnd.nextInt(-amp, amp + 1).toShort() } }

    private fun tone(frames: Int, amp: Double = 8000.0, hz: Double = 220.0): List<ShortArray> {
        var n = 0
        return List(frames) {
            ShortArray(EnergyVad.FRAME_SAMPLES) {
                val s = amp * sin(2 * PI * hz * n++ / EnergyVad.SAMPLE_RATE) + rnd.nextInt(-60, 61)
                s.toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }

    private fun run(frames: List<ShortArray>) = EnergyVad().apply { frames.forEach { process(it) } }

    @Test
    fun levelOfSilenceAndFullScale() {
        assertThat(EnergyVad.levelDb(ShortArray(320))).isEqualTo(EnergyVad.SILENCE_DB)
        assertThat(EnergyVad.levelDb(ShortArray(320) { 32767 })).isWithin(0.01).of(0.0)
    }

    @Test
    fun noiseOnlyHasNoSpeech() {
        val vad = run(noise(250))
        assertThat(vad.speechMs).isEqualTo(0)
        assertThat(vad.snrDb()).isNull()
        assertThat(vad.noiseFloorDb).isNotNull()
    }

    @Test
    fun countsSpeechBurstsWithinHangover() {
        // 1 s noise, 2 s tone, 1 s noise, 3 s tone, 1 s noise.
        val vad = run(noise(50) + tone(100) + noise(50) + tone(150) + noise(50))
        // 5 s of tone, plus up to 300 ms hangover after each burst.
        assertThat(vad.speechMs).isAtLeast(5_000)
        assertThat(vad.speechMs).isAtMost(5_000 + 2 * 300 + 40)
        assertThat(vad.snrDb()!!).isGreaterThan(EnergyVad.NOISY_SNR_DB)
    }

    @Test
    fun speechFromTheFirstFrameIsCountedAfterWarmup() {
        // Talking straight away: the warm-up frames are replayed once the floor is known.
        val vad = run(noise(5) + tone(100) + noise(50))
        assertThat(vad.speechMs).isAtLeast(1_900)
    }

    @Test
    fun shortGapsAreBridgedByTheHangover() {
        // 200 ms gaps between words stay inside one speech segment (hangover is 300 ms).
        val words = (1..10).flatMap { tone(15) + noise(10) }
        val vad = run(noise(50) + words + noise(50))
        assertThat(vad.speechMs).isAtLeast(10 * 15 * 20L + 9 * 10 * 20L)
    }

    @Test
    fun clicksShorterThanTheOnsetAreIgnored() {
        // 40 ms clicks never reach the 60 ms onset.
        val clicks = (1..20).flatMap { tone(2) + noise(20) }
        val vad = run(noise(50) + clicks)
        assertThat(vad.speechMs).isEqualTo(0)
    }

    @Test
    fun noisyRoomGivesLowSnr() {
        // Uniform noise ±3000 (≈ −25.6 dBFS) under a tone of rms ≈ 8500 (≈ −11.7 dBFS): heard, but under 15 dB SNR.
        val vad = run(noise(50, amp = 3000) + tone(100, amp = 12_000.0) + noise(50, amp = 3000))
        assertThat(vad.speechMs).isGreaterThan(1_500)
        assertThat(vad.snrDb()!!).isLessThan(EnergyVad.NOISY_SNR_DB)
    }
}
