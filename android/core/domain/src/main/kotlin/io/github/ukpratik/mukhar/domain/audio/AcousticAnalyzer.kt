package io.github.ukpratik.mukhar.domain.audio

import io.github.ukpratik.mukhar.domain.metrics.Pauses
import io.github.ukpratik.mukhar.domain.metrics.Wpm
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Delivery numbers measured from the voice alone, for when no transcript could be written (D-048): no speech-to-text
 * key, offline speech not installed, or the vendor failed. Fillers, repeats and word variety need words, so they're
 * not measured here.
 *
 * [estWpm] is an estimate: syllables per minute (energy peaks, de Jong & Wempe 2009 style) divided by the average
 * syllables per word for the language.
 */
data class AcousticMetrics(
    val durationMs: Long,
    val speechMs: Long,
    /** First speech to last speech, with long thinking breaks capped like [Wpm.spanMs]. */
    val spanMs: Long,
    val syllables: Int,
    val syllablesPerMin: Double,
    val estWpm: Double,
    val wpmBand: WpmBand,
    val longPauses: Int,
    val longPausesPerMin: Double,
    val longestPauseMs: Long,
)

/**
 * Pure and frame-based (10 ms frames of 16 kHz mono PCM), so unit tests feed it synthetic signals and the phone feeds
 * it the decoded recording.
 *
 * 1. Frame level in dBFS; noise floor = 10th percentile of all frames.
 * 2. Speech = level > floor + [SPEECH_MARGIN_DB], held for [HANGOVER_FRAMES] (300 ms) so gaps inside words don't count.
 * 3. Long pause = a silence between speech runs longer than [Pauses.LONG_PAUSE_MS] (the transcript rule).
 * 4. Syllable nuclei = peaks of the 30 ms-smoothed level inside speech, at least [PEAK_MARGIN_DB] over the floor, with a
 *    dip of [DIP_DB] on both sides and ≥ [MIN_PEAK_GAP_FRAMES] apart.
 */
class AcousticAnalyzer {
    private val levels = ArrayList<Float>(18_000)
    private val carry = ShortArray(FRAME_SAMPLES)
    private var carryLen = 0

    /** Feeds PCM samples (any chunk size). */
    fun feed(samples: ShortArray, length: Int = samples.size) {
        var i = 0
        while (i < length) {
            val take = minOf(FRAME_SAMPLES - carryLen, length - i)
            System.arraycopy(samples, i, carry, carryLen, take)
            carryLen += take
            i += take
            if (carryLen == FRAME_SAMPLES) {
                levels += levelDb(carry, FRAME_SAMPLES).toFloat()
                carryLen = 0
            }
        }
    }

    fun result(lang: Lang): AcousticMetrics = analyze(levels.toFloatArray(), lang)

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 10
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        const val SPEECH_MARGIN_DB = 10.0f
        const val HANGOVER_FRAMES = 30
        const val PEAK_MARGIN_DB = 12.0f
        const val DIP_DB = 2.0f
        const val MIN_PEAK_GAP_FRAMES = 8
        const val SMOOTH_FRAMES = 3

        /** `algo_version` stored with voice-only metrics; transcript metrics use 1, so averages can tell them apart. */
        const val ALGO_VERSION = 100

        /** Average syllables per spoken word (conversational English ≈ 1.4; Hindi ≈ 1.6). */
        fun syllablesPerWord(lang: Lang): Double = if (lang == Lang.HI) 1.6 else 1.4

        fun analyze(levels: FloatArray, lang: Lang): AcousticMetrics {
            val n = levels.size
            val durationMs = n.toLong() * FRAME_MS
            if (n == 0) return empty(lang, 0)
            val floor = percentile(levels, 0.10f)
            val speech = speechMask(levels, floor)
            val runs = runs(speech)
            if (runs.isEmpty()) return empty(lang, durationMs)

            var longPauses = 0
            var longest = 0L
            var thinking = 0L
            for (k in 1 until runs.size) {
                val gapMs = (runs[k].first - runs[k - 1].last - 1).toLong() * FRAME_MS
                if (gapMs > Pauses.LONG_PAUSE_MS) longPauses++
                longest = max(longest, gapMs)
                if (gapMs > Wpm.THINK_BREAK_CAP_MS) thinking += gapMs - Wpm.THINK_BREAK_CAP_MS
            }
            val spanMs = ((runs.last().last - runs.first().first + 1).toLong() * FRAME_MS - thinking).coerceAtLeast(FRAME_MS.toLong())
            val speechMs = speech.count { it }.toLong() * FRAME_MS

            val syllables = syllableNuclei(smooth(levels), speech, floor)
            val perMin = Wpm.perMinute(syllables, spanMs)
            val wpm = perMin / syllablesPerWord(lang)
            return AcousticMetrics(
                durationMs = durationMs,
                speechMs = speechMs,
                spanMs = spanMs,
                syllables = syllables,
                syllablesPerMin = perMin,
                estWpm = wpm,
                wpmBand = Wpm.band(wpm, lang),
                longPauses = longPauses,
                longPausesPerMin = Wpm.perMinute(longPauses, spanMs),
                longestPauseMs = longest,
            )
        }

        private fun empty(lang: Lang, durationMs: Long) = AcousticMetrics(
            durationMs, 0, 0, 0, 0.0, 0.0, Wpm.band(0.0, lang), 0, 0.0, 0,
        )

        /** Loud frames, with gaps shorter than [HANGOVER_FRAMES] (300 ms) inside a run filled in. */
        private fun speechMask(levels: FloatArray, floor: Float): BooleanArray {
            val mask = BooleanArray(levels.size)
            var lastLoud = -1
            for (i in levels.indices) {
                if (levels[i] > floor + SPEECH_MARGIN_DB) {
                    if (lastLoud >= 0 && i - lastLoud <= HANGOVER_FRAMES) for (k in lastLoud + 1 until i) mask[k] = true
                    mask[i] = true
                    lastLoud = i
                }
            }
            return mask
        }

        private fun runs(mask: BooleanArray): List<IntRange> {
            val out = ArrayList<IntRange>()
            var start = -1
            for (i in mask.indices) {
                if (mask[i] && start < 0) start = i
                if (!mask[i] && start >= 0) {
                    out += start until i
                    start = -1
                }
            }
            if (start >= 0) out += start until mask.size
            return out
        }

        private fun smooth(levels: FloatArray): FloatArray {
            val out = FloatArray(levels.size)
            val half = SMOOTH_FRAMES / 2
            for (i in levels.indices) {
                var sum = 0f
                var count = 0
                for (k in maxOf(0, i - half)..minOf(levels.lastIndex, i + half)) {
                    sum += levels[k]
                    count++
                }
                out[i] = sum / count
            }
            return out
        }

        /** Peaks with a [DIP_DB] valley on both sides (prominence), inside speech, over floor + [PEAK_MARGIN_DB]. */
        private fun syllableNuclei(s: FloatArray, speech: BooleanArray, floor: Float): Int {
            var count = 0
            var last = -1
            for (i in 1 until s.size - 1) {
                if (!speech[i] || s[i] < s[i - 1] || s[i] <= s[i + 1] || s[i] <= floor + PEAK_MARGIN_DB) continue
                if (last < 0) {
                    count = 1
                    last = i
                    continue
                }
                var valley = Float.MAX_VALUE
                for (k in last + 1 until i) valley = minOf(valley, if (speech[k]) s[k] else floor)
                val prominent = s[last] - valley >= DIP_DB && s[i] - valley >= DIP_DB
                if (prominent && i - last >= MIN_PEAK_GAP_FRAMES) {
                    count++
                    last = i
                } else if (s[i] > s[last]) {
                    last = i // a higher peak in the same syllable: the nucleus moves there
                }
            }
            return count
        }

        private fun percentile(values: FloatArray, p: Float): Float {
            val sorted = values.copyOf().also { it.sort() }
            return sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)]
        }

        fun levelDb(frame: ShortArray, length: Int): Double {
            if (length <= 0) return EnergyVad.SILENCE_DB
            var sum = 0.0
            for (i in 0 until length) {
                val v = frame[i].toDouble()
                sum += v * v
            }
            val rms = sqrt(sum / length)
            return if (rms <= 0.0) EnergyVad.SILENCE_DB else max(EnergyVad.SILENCE_DB, 20 * log10(rms / 32768.0))
        }
    }
}
