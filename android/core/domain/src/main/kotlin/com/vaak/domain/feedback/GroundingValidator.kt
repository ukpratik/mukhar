package com.vaak.domain.feedback

import com.vaak.domain.text.Normalizer

/**
 * Client-side grounding re-validation (LLD §4.4, US-014/015), defence in depth behind the Worker's validator: every
 * quoted item must point at a token range whose normalised text equals its quote, and must not touch a low-confidence
 * token. Invalid items are dropped; a dropped glow or grow becomes a metrics-based fallback ([FeedbackFallback]).
 */
object GroundingValidator {
    const val MAX_SPAN = 40
    const val REPAIR_WINDOW = 5
    const val MAX_ITEMS = 3

    data class Result(
        val feedback: Feedback,
        val dropped: Int,
        val fallbackGlow: Boolean,
        val fallbackGrow: Boolean,
    ) {
        /** Upgraded words of the kept better versions, de-duplicated (US-021 "Words for you"). */
        val wordsForYou: List<UpgradedWord>
            get() = feedback.betterVersions.flatMap { it.upgradedWords }.distinctBy { Normalizer.norm(it.word) }
    }

    /**
     * @param tokens the transcript tokens in the order sent to `/feedback`.
     * @param lowConf indices of low-confidence tokens (never quoted, praised or corrected).
     */
    fun validate(feedback: Feedback, tokens: List<String>, lowConf: Set<Int>, stutterFriendly: Boolean): Result {
        val norms = tokens.map(Normalizer::norm)
        var dropped = 0

        val glow = feedback.glow?.let { g -> span(norms, lowConf, g.start, g.end, g.quote)?.let { (s, e) -> g.copy(start = s, end = e) } }
        if (feedback.glow != null && glow == null) dropped++

        var grow = feedback.grow?.let { g -> span(norms, lowConf, g.start, g.end, g.quote)?.let { (s, e) -> g.copy(start = s, end = e) } }
        if (feedback.grow != null && grow == null) dropped++
        if (grow != null && stutterFriendly && grow.issue in FLUENCY_ISSUES) {
            grow = null
            dropped++
        }

        val better = mutableListOf<BetterVersion>()
        for (bv in feedback.betterVersions) {
            if (better.size == MAX_ITEMS) {
                dropped++
                continue
            }
            val kept = keepBetterVersion(bv, norms, lowConf)
            if (kept == null) dropped++ else better += kept
        }

        val mistakes = mutableListOf<Mistake>()
        for (m in feedback.mistakes) {
            val s = span(norms, lowConf, m.start, m.end, m.quote)
            if (mistakes.size == MAX_ITEMS || s == null || Normalizer.norm(m.fix) == quoteNorm(m.quote)) {
                dropped++
            } else {
                mistakes += m.copy(start = s.first, end = s.second)
            }
        }

        // Self-harm cue: no coaching items at all (US-014); the UI shows the helpline card instead.
        val coaching = feedback.safety != Safety.SELF_HARM
        return Result(
            feedback = feedback.copy(
                glow = glow.takeIf { coaching },
                grow = grow.takeIf { coaching },
                betterVersions = if (coaching) better else emptyList(),
                mistakes = if (coaching) mistakes else emptyList(),
            ),
            dropped = dropped,
            fallbackGlow = coaching && glow == null,
            fallbackGrow = coaching && grow == null,
        )
    }

    private val FLUENCY_ISSUES = setOf(GrowIssue.REPETITION, GrowIssue.PACE)

    private fun keepBetterVersion(bv: BetterVersion, norms: List<String>, lowConf: Set<Int>): BetterVersion? {
        val (s, e) = span(norms, lowConf, bv.start, bv.end, bv.quote) ?: return null
        val q = quoteNorm(bv.quote)
        val rewrite = Normalizer.norm(bv.rewrite)
        if (rewrite == q || bv.rewrite.length > 2 * bv.quote.length + 60) return null
        val words = bv.upgradedWords.filter { w ->
            val n = Normalizer.norm(w.word)
            n.isNotEmpty() && rewrite.contains(n) && !q.contains(n)
        }
        if (words.isEmpty()) return null
        return bv.copy(start = s, end = e, upgradedWords = words)
    }

    /** `span_ok` from LLD §4.4: the (possibly repaired) range, or `null` to reject. */
    fun span(norms: List<String>, lowConf: Set<Int>, start: Int, end: Int, quote: String): Pair<Int, Int>? {
        val n = norms.size
        if (start < 0 || start > end || end >= n || end - start > MAX_SPAN) return null
        val q = quoteNorm(quote)
        if (q.isEmpty()) return null
        if (lowConf.none { it in start..end } && join(norms, start, end) == q) return start to end
        // Off-by-few repair: the quote as a contiguous token run near the claimed range.
        val from = (start - REPAIR_WINDOW).coerceAtLeast(0)
        val to = (end + REPAIR_WINDOW).coerceAtMost(n - 1)
        for (s in from..to) {
            for (e in s..minOf(to, s + MAX_SPAN)) {
                if (join(norms, s, e) == q && lowConf.none { it in s..e }) return s to e
            }
        }
        return null
    }

    private fun quoteNorm(quote: String): String = Normalizer.norm(quote)

    private fun join(norms: List<String>, start: Int, end: Int): String =
        (start..end).map { norms[it] }.filter { it.isNotEmpty() }.joinToString(" ")
}
