package io.github.ukpratik.mukhar.domain.metrics

import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import io.github.ukpratik.mukhar.domain.text.Normalizer

/** One detected filler occurrence: token indices [start, end] (end > start for a bigram such as "you know"). */
data class FillerHit(val start: Int, val end: Int)

data class FillerResult(val hits: List<FillerHit>, val mask: BooleanArray) {
    val count: Int get() = hits.size

    override fun equals(other: Any?): Boolean =
        other is FillerResult && hits == other.hits && mask.contentEquals(other.mask)

    override fun hashCode(): Int = 31 * hits.hashCode() + mask.contentHashCode()
}

/**
 * `isFiller(i)` (LLD §4.2): confident tokens only; unambiguous fillers always count, ambiguous ones only with a pause
 * of ≥ 300 ms on both sides (around the pair for bigrams). "ki" never counts.
 */
object Fillers {
    const val PAUSE_GATE_MS = 300L

    fun detect(words: List<Word>, lang: Lang, lowConf: Double = MetricsCalculator.LOW_CONF): FillerResult {
        val unambiguous = FillerLexicon.unambiguous(lang)
        val ambiguous = FillerLexicon.ambiguous(lang)
        val ambiguousPairs = ambiguous.filter { ' ' in it }.toSet()
        val norms = words.map { Normalizer.norm(it.w) }
        val mask = BooleanArray(words.size)
        val hits = mutableListOf<FillerHit>()
        var i = 0
        while (i < words.size) {
            val confident = words[i].conf >= lowConf
            // Bigram first ("you know"), so its parts aren't judged alone.
            if (i + 1 < words.size && confident && words[i + 1].conf >= lowConf) {
                val pair = norms[i] + " " + norms[i + 1]
                if (pair in ambiguousPairs && gated(words, i, i + 1)) {
                    mask[i] = true
                    mask[i + 1] = true
                    hits += FillerHit(i, i + 1)
                    i += 2
                    continue
                }
            }
            val n = norms[i]
            val isFiller = confident && n !in FillerLexicon.NEVER &&
                (n in unambiguous || (n in ambiguous && gated(words, i, i)))
            if (isFiller) {
                mask[i] = true
                hits += FillerHit(i, i)
            }
            i++
        }
        return FillerResult(hits, mask)
    }

    private fun gated(words: List<Word>, start: Int, end: Int): Boolean =
        Gaps.before(words, start) >= PAUSE_GATE_MS && Gaps.after(words, end) >= PAUSE_GATE_MS
}
