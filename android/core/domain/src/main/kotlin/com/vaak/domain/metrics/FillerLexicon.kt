package com.vaak.domain.metrics

import com.vaak.domain.model.Lang

/**
 * Filler sets (PRD US-012, LLD §4.2), in normalised form. Mirrors `shared/test-vectors/fillers.json`
 * (a test asserts they match). Multi-word entries ("you know") are matched as a token pair.
 */
object FillerLexicon {
    const val VERSION = 1

    private val UNAMBIGUOUS = mapOf(
        Lang.EN to setOf("um", "umm", "uh", "er", "erm", "hmm"),
        Lang.HI to setOf("umm", "उम्म", "aa", "आ", "अ", "hmm", "हम्म", "matlab", "मतलब"),
    )

    private val AMBIGUOUS = mapOf(
        Lang.EN to setOf("like", "basically", "you know", "actually", "so"),
        Lang.HI to setOf("toh", "तो", "wo", "वो", "haan", "हां", "basically", "बेसिकली"),
    )

    /** Never a filler in any language: "ki" / कि / की is grammatical (PRD US-012). */
    val NEVER: Set<String> = setOf("ki", "कि", "की")

    fun unambiguous(lang: Lang): Set<String> = UNAMBIGUOUS.getValue(lang)

    fun ambiguous(lang: Lang): Set<String> = AMBIGUOUS.getValue(lang)
}
