package io.github.ukpratik.mukhar.domain.metrics

import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import io.github.ukpratik.mukhar.domain.text.Normalizer

/**
 * On-device delivery metrics for one talk (US-013, LLD §4.2, `algo_version = 1`).
 * In stutter-friendly mode [longPauses], [longPausesPerMin] and [repetitions] are `null` (not scored) and
 * [repeatIndices] is empty (not highlighted).
 */
data class Metrics(
    val wpm: Double,
    val wpmBand: WpmBand,
    val lexicalTokens: Int,
    val fillerCount: Int,
    val fillersPerMin: Double,
    val longPauses: Int?,
    val longPausesPerMin: Double?,
    val repetitions: Int?,
    val mattr: Double?,
    val spanMs: Long,
    val fillerIndices: Set<Int>,
    val repeatIndices: Set<Int>,
    val algoVersion: Int = MetricsCalculator.ALGO_VERSION,
)

object MetricsCalculator {
    const val ALGO_VERSION = 1
    const val LOW_CONF = 0.6

    fun compute(
        words: List<Word>,
        lang: Lang,
        stutterFriendly: Boolean = false,
        lowConf: Double = LOW_CONF,
    ): Metrics {
        val fillers = Fillers.detect(words, lang, lowConf)
        val spanMs = Wpm.spanMs(words)
        val lexical = fillers.mask.count { !it }
        val wpm = Wpm.perMinute(lexical, spanMs)
        val longPauses = Pauses.countLong(words)
        val repetition = Repetition.detect(words, fillers.mask, lang, lowConf)
        val mattrTokens = words.indices
            .filter { !fillers.mask[it] && words[it].conf >= lowConf }
            .map { Normalizer.norm(words[it].w) }
            .filter { it.isNotEmpty() }
        return Metrics(
            wpm = wpm,
            wpmBand = Wpm.band(wpm, lang),
            lexicalTokens = lexical,
            fillerCount = fillers.count,
            fillersPerMin = Wpm.perMinute(fillers.count, spanMs),
            longPauses = if (stutterFriendly) null else longPauses,
            longPausesPerMin = if (stutterFriendly) null else Wpm.perMinute(longPauses, spanMs),
            repetitions = if (stutterFriendly) null else repetition.count,
            mattr = Mattr.compute(mattrTokens),
            spanMs = spanMs,
            fillerIndices = fillers.mask.indices.filter { fillers.mask[it] }.toSet(),
            repeatIndices = if (stutterFriendly) emptySet() else repetition.indices,
        )
    }
}
