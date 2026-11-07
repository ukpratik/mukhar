package io.github.ukpratik.mukhar.domain.metrics

import io.github.ukpratik.mukhar.domain.model.Lang

/** How far a metric can be trusted with a given speech engine (ADR-008 "What each mode can and can't do"). */
enum class Reliability { HIGH, APPROX, HIDDEN }

data class MetricReliability(
    val pace: Reliability = Reliability.HIGH,
    val pauses: Reliability = Reliability.HIGH,
    val fillers: Reliability = Reliability.HIGH,
    val repetition: Reliability = Reliability.HIGH,
    val variety: Reliability = Reliability.HIGH,
) {
    val anyApprox: Boolean get() = listOf(pace, pauses, fillers, repetition, variety).any { it == Reliability.APPROX }
}

/** Speech engine families, from the transcript's `provider` (e.g. `vosk`, `deepgram`, `sarvam`, the Worker's ids). */
enum class EngineFamily { ON_DEVICE, DEEPGRAM, SARVAM, CLOUD }

object EngineReliability {
    const val PROVIDER_ON_DEVICE = "vosk"
    const val PROVIDER_DEEPGRAM = "deepgram"
    const val PROVIDER_SARVAM = "sarvam"

    fun family(provider: String?): EngineFamily = when {
        provider == null -> EngineFamily.CLOUD
        provider.startsWith(PROVIDER_ON_DEVICE) || provider.startsWith("local") -> EngineFamily.ON_DEVICE
        provider.startsWith(PROVIDER_DEEPGRAM) -> EngineFamily.DEEPGRAM
        provider.startsWith(PROVIDER_SARVAM) -> EngineFamily.SARVAM
        else -> EngineFamily.CLOUD
    }

    /**
     * Per-metric reliability. On-device: pace is reliable (word-count errors partly cancel); fillers are under-counted,
     * repeats can be false, variety moves with WER, and pauses come from word gaps until the VAD-based pauses land
     * (2B), so they're marked approximate too. Deepgram: Hindi fillers come from our lexicon only. Sarvam: chunk-level
     * timestamps are interpolated, so pauses and repeats are approximate.
     */
    fun of(provider: String?, lang: Lang): MetricReliability = when (family(provider)) {
        EngineFamily.ON_DEVICE -> MetricReliability(
            pace = Reliability.HIGH,
            pauses = Reliability.APPROX,
            fillers = Reliability.APPROX,
            repetition = Reliability.APPROX,
            variety = Reliability.APPROX,
        )
        EngineFamily.DEEPGRAM -> MetricReliability(fillers = if (lang == Lang.HI) Reliability.APPROX else Reliability.HIGH)
        EngineFamily.SARVAM -> MetricReliability(pauses = Reliability.APPROX, repetition = Reliability.APPROX)
        EngineFamily.CLOUD -> MetricReliability()
    }
}
