package com.vaak.domain.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Energy voice-activity detector (LLD §4.1, US-011/018/031). Pure and frame-based, so it runs on synthetic PCM in
 * unit tests and on the recorder's 20 ms frames (320 samples at 16 kHz) on the device.
 *
 * - Frame level `L = 20·log10(rms / 32768)` dBFS.
 * - Noise floor `N` = 10th percentile of the first [WARMUP_FRAMES] frames, then tracked: `N ← min(L, N + 0.02 dB)`.
 *   The warm-up frames are replayed through the detector once `N` is known, so speech at the very start counts.
 * - Speech when `L > N + 10 dB` for [ONSET_FRAMES] consecutive frames (60 ms); it stays speech until [HANGOVER_FRAMES]
 *   frames in a row are below (300 ms). Onset frames are counted as speech.
 * - `snrDb = median(L of speech frames) − N`.
 */
class EnergyVad(private val frameMs: Int = FRAME_MS) {
    private val warmup = ArrayList<Double>(WARMUP_FRAMES)
    private var floor = Double.NaN
    private var inSpeech = false
    private var above = 0
    private var below = 0
    private var speechFrames = 0L
    private var lastFrameSpeech = false
    private val speechLevels = ArrayList<Double>()

    /** Detected speech so far, in ms. */
    val speechMs: Long get() = speechFrames * frameMs

    /** Current noise floor in dBFS, or `null` during the first [WARMUP_FRAMES] frames. */
    val noiseFloorDb: Double? get() = floor.takeUnless { it.isNaN() }

    /** Whether the last processed frame was speech (after the warm-up). */
    val isSpeech: Boolean get() = lastFrameSpeech

    /** Processes one PCM frame and returns its level in dBFS. */
    fun process(frame: ShortArray, length: Int = frame.size): Double {
        val level = levelDb(frame, length)
        if (floor.isNaN()) {
            warmup += level
            if (warmup.size == WARMUP_FRAMES) {
                floor = percentile(warmup, 0.10)
                warmup.forEach(::decide)
                warmup.clear()
            }
        } else {
            decide(level)
        }
        return level
    }

    /** `median(L_speech) − N`, or `null` when no speech was detected. */
    fun snrDb(): Double? {
        val n = noiseFloorDb ?: return null
        if (speechLevels.isEmpty()) return null
        return percentile(speechLevels, 0.5) - n
    }

    private fun decide(level: Double) {
        floor = min(level, floor + FLOOR_RISE_DB)
        lastFrameSpeech = false
        val loud = level > floor + SPEECH_MARGIN_DB
        if (inSpeech) {
            if (loud) {
                below = 0
            } else {
                below++
                if (below >= HANGOVER_FRAMES) {
                    inSpeech = false
                    below = 0
                }
            }
            if (inSpeech) countSpeech(level, loud)
        } else if (loud) {
            above++
            if (above >= ONSET_FRAMES) {
                inSpeech = true
                speechFrames += above.toLong() - 1 // the earlier onset frames count too
                above = 0
                below = 0
                countSpeech(level, true)
                return
            }
        } else {
            above = 0
        }
    }

    private fun countSpeech(level: Double, loud: Boolean) {
        speechFrames++
        if (loud) speechLevels += level
        lastFrameSpeech = true
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        const val WARMUP_FRAMES = 25
        const val ONSET_FRAMES = 3
        const val HANGOVER_FRAMES = 15
        const val SPEECH_MARGIN_DB = 10.0
        const val FLOOR_RISE_DB = 0.02
        const val SILENCE_DB = -120.0

        /** Below this SNR the talk is "noisy" (US-018; tuned at gate zero). */
        const val NOISY_SNR_DB = 15.0

        /** Ambient level above which the ready-screen mic check warns about noise (no speech yet, so no SNR). */
        const val NOISY_AMBIENT_DB = -45.0

        fun levelDb(frame: ShortArray, length: Int = frame.size): Double {
            if (length <= 0) return SILENCE_DB
            var sum = 0.0
            for (i in 0 until length) {
                val s = frame[i].toDouble()
                sum += s * s
            }
            val rms = sqrt(sum / length)
            return if (rms <= 0.0) SILENCE_DB else max(SILENCE_DB, 20 * log10(rms / 32768.0))
        }

        private fun percentile(values: List<Double>, p: Double): Double {
            val sorted = values.sorted()
            val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }
    }
}
