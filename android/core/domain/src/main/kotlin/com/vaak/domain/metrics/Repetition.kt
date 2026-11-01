package com.vaak.domain.metrics

import com.vaak.domain.model.Lang
import com.vaak.domain.model.Word
import com.vaak.domain.text.Normalizer

/** [indices] are the repeated tokens to underline (the second occurrence of each repeat). */
data class RepetitionResult(val count: Int, val indices: Set<Int>)

/**
 * LLD §4.2. Immediate repeat: same normalised token as the previous one (both non-filler, confident, gap < 1 s).
 * Bigram repeat: tokens (i−1, i) equal (i−3, i−2).
 * EN: allowlist {very very, no no, bye bye}; every extra copy counts ("the the the" = 2).
 * HI: single-token reduplication is grammatical (धीरे धीरे), so a run counts only from its third copy
 * (run of n identical tokens = n − 2); bigram repeats count as in EN.
 */
object Repetition {
    const val MAX_GAP_MS = 1_000L
    val EN_ALLOWLIST: Set<String> = setOf("very very", "no no", "bye bye")

    fun detect(words: List<Word>, fillerMask: BooleanArray, lang: Lang, lowConf: Double): RepetitionResult {
        val norms = words.map { Normalizer.norm(it.w) }
        fun eligible(i: Int) = !fillerMask[i] && words[i].conf >= lowConf && norms[i].isNotEmpty()
        fun joined(i: Int) = words[i].startMs - words[i - 1].endMs < MAX_GAP_MS

        val indices = sortedSetOf<Int>()
        var count = 0

        // Single-token runs.
        var i = 1
        while (i < words.size) {
            if (eligible(i) && eligible(i - 1) && joined(i) && norms[i] == norms[i - 1]) {
                // Extend the run [start..end] of identical, joined, eligible tokens.
                val start = i - 1
                var end = i
                while (end + 1 < words.size && eligible(end + 1) && joined(end + 1) && norms[end + 1] == norms[start]) end++
                val runLength = end - start + 1
                val counted = when (lang) {
                    Lang.EN -> if (runLength == 2 && "${norms[start]} ${norms[end]}" in EN_ALLOWLIST) 0 else runLength - 1
                    Lang.HI -> (runLength - 2).coerceAtLeast(0)
                }
                if (counted > 0) {
                    count += counted
                    for (k in (end - counted + 1)..end) indices += k
                }
                i = end + 1
            } else {
                i++
            }
        }

        // Bigram repeats: (i−1, i) == (i−3, i−2), with distinct tokens inside the pair (identical ones are runs above).
        i = 3
        while (i < words.size) {
            val all = (i - 3..i).all(::eligible)
            if (all && norms[i - 1] == norms[i - 3] && norms[i] == norms[i - 2] && norms[i - 1] != norms[i] && joined(i - 1)) {
                count++
                indices += i - 1
                indices += i
                i += 2
            } else {
                i++
            }
        }
        return RepetitionResult(count, indices)
    }
}
