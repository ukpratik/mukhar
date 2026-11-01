package com.vaak.app.feature.onboarding.warmup

import com.vaak.domain.model.Lang
import kotlin.math.log10
import kotlin.math.sqrt

enum class PaceBand { SLOW, COMFORTABLE, FAST }

enum class VolumeBand { SOFT, CLEAR, LOUD, NOISY }

/** On-device encouragement for S03: two bands as words, no scores (US-002). [marker]s are 0..1 for the band bar. */
data class WarmupResult(
    val speechMs: Long,
    val pace: PaceBand,
    val paceMarker: Float,
    val volume: VolumeBand,
    val volumeMarker: Float,
) {
    val enoughSpeech: Boolean get() = speechMs >= WarmupAnalyzer.MIN_SPEECH_MS
}

/**
 * A small energy meter for the warm-up (LLD §4.1 parameters: 20 ms frames, noise floor from the first 25 frames,
 * speech when 10 dB above it for 3 frames, 300 ms hangover). The paragraph is known, so pace = its word count ÷
 * speech time. Audio is processed in memory and never stored. M2's EnergyVad can replace the detector after merge.
 */
class WarmupAnalyzer(private val wordCount: Int, private val lang: Lang) {
    private val calibration = ArrayList<Float>(CALIBRATION_FRAMES)
    private val speechLevels = ArrayList<Float>()
    private var floor = Float.NaN
    private var above = 0
    private var hangover = 0
    private var speechFrames = 0
    private var inSpeech = false

    val speechMs: Long get() = speechFrames.toLong() * FRAME_MS

    /** Feeds one 20 ms frame of 16-bit PCM; returns its level in dBFS. */
    fun feed(frame: ShortArray, size: Int = frame.size): Float = feedLevel(levelDb(frame, size))

    /** Feeds a precomputed frame level (dBFS). */
    fun feedLevel(level: Float): Float {
        if (calibration.size < CALIBRATION_FRAMES) {
            calibration += level
            if (calibration.size == CALIBRATION_FRAMES) floor = calibration.sorted()[CALIBRATION_FRAMES / 10]
        }
        val noise = if (floor.isNaN()) calibration.minOrNull() ?: level else floor
        // The floor drifts up only outside speech, so a continuous read doesn't raise it into the voice.
        if (!floor.isNaN() && !inSpeech) floor = minOf(level, floor + FLOOR_RISE_DB)
        if (level > noise + SPEECH_ABOVE_DB) {
            above++
            if (above >= ONSET_FRAMES) {
                inSpeech = true
                hangover = HANGOVER_FRAMES
            }
        } else {
            above = 0
            if (inSpeech && --hangover <= 0) inSpeech = false
        }
        if (inSpeech) {
            speechFrames++
            speechLevels += level
        }
        return level
    }

    fun result(): WarmupResult {
        val speechMin = speechMs / 60_000.0
        val wpm = if (speechMin > 0) wordCount / speechMin else 0.0
        val (slow, fast) = if (lang == Lang.HI) 100.0 to 150.0 else 110.0 to 160.0
        val pace = when {
            wpm < slow -> PaceBand.SLOW
            wpm > fast -> PaceBand.FAST
            else -> PaceBand.COMFORTABLE
        }
        val median = speechLevels.sorted().let { if (it.isEmpty()) SILENCE_DB else it[it.size / 2] }
        val noise = if (floor.isNaN()) SILENCE_DB else floor
        val volume = when {
            median - noise < NOISY_SNR_DB -> VolumeBand.NOISY
            median < SOFT_DB -> VolumeBand.SOFT
            median > LOUD_DB -> VolumeBand.LOUD
            else -> VolumeBand.CLEAR
        }
        return WarmupResult(
            speechMs = speechMs,
            pace = pace,
            paceMarker = ((wpm - 60) / 160).toFloat().coerceIn(0.05f, 0.95f),
            volume = volume,
            volumeMarker = ((median - SILENCE_DB) / (0f - SILENCE_DB)).coerceIn(0.05f, 0.95f),
        )
    }

    companion object {
        const val FRAME_MS = 20
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        const val MIN_SPEECH_MS = 8_000L
        const val DURATION_MS = 20_000L
        private const val CALIBRATION_FRAMES = 25
        private const val FLOOR_RISE_DB = 0.02f
        private const val SPEECH_ABOVE_DB = 10f
        private const val ONSET_FRAMES = 3
        private const val HANGOVER_FRAMES = 15
        private const val NOISY_SNR_DB = 15f
        private const val SOFT_DB = -38f
        private const val LOUD_DB = -12f
        private const val SILENCE_DB = -60f

        fun levelDb(frame: ShortArray, size: Int = frame.size): Float {
            if (size == 0) return SILENCE_DB
            var sum = 0.0
            for (i in 0 until size) sum += frame[i].toDouble() * frame[i]
            val rms = sqrt(sum / size)
            return if (rms < 1.0) SILENCE_DB else (20 * log10(rms / 32_768.0)).toFloat().coerceAtLeast(SILENCE_DB)
        }

        fun countWords(text: String): Int = text.split(Regex("\\s+")).count { w -> w.any(Char::isLetter) }
    }
}
