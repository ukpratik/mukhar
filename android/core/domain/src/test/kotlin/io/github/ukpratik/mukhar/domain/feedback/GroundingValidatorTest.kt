package io.github.ukpratik.mukhar.domain.feedback

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import org.junit.Test

/** Client-side grounding re-validation (LLD §4.4), defence in depth behind the Worker. */
class GroundingValidatorTest {
    private val en = "So last quarter I led the migration , um , and nobody was fully knowing how the reports were calculated .".split(" ")
    private val hi = "मेरे शहर में सबसे बड़ी problem है। उम्म, मतलब, metro तो आ गई है,".split(" ")

    private fun glow(s: Int, e: Int, q: String) = Glow(s, e, q, "note")
    private fun grow(s: Int, e: Int, q: String, issue: GrowIssue = GrowIssue.FILLER) = Grow(s, e, q, issue, "why", "how")
    private fun fb(
        glow: Glow? = null,
        grow: Grow? = null,
        better: List<BetterVersion> = emptyList(),
        mistakes: List<Mistake> = emptyList(),
        safety: Safety = Safety.NONE,
    ) = Feedback("summary", glow, grow, better, mistakes, safety)

    @Test
    fun exactSpansAreKept() {
        val r = GroundingValidator.validate(fb(glow(1, 2, "last quarter"), grow(3, 7, "I led the migration,")), en, emptySet(), false)
        assertThat(r.dropped).isEqualTo(0)
        assertThat(r.fallbackGlow).isFalse()
        assertThat(r.feedback.glow).isEqualTo(glow(1, 2, "last quarter"))
        assertThat(r.feedback.grow!!.end).isEqualTo(7) // the "," token normalises to "" and is skipped
    }

    @Test
    fun offByFewSpansAreRepaired() {
        val r = GroundingValidator.validate(fb(glow(3, 4, "last quarter")), en, emptySet(), false)
        assertThat(r.feedback.glow!!.start).isEqualTo(1)
        assertThat(r.feedback.glow!!.end).isEqualTo(2)
        assertThat(r.dropped).isEqualTo(0)
    }

    @Test
    fun wrongQuoteOrOutOfRangeFallsBack() {
        val r = GroundingValidator.validate(fb(glow(1, 2, "next quarter"), grow(40, 41, "nope")), en, emptySet(), false)
        assertThat(r.feedback.glow).isNull()
        assertThat(r.feedback.grow).isNull()
        assertThat(r.fallbackGlow).isTrue()
        assertThat(r.fallbackGrow).isTrue()
        assertThat(r.dropped).isEqualTo(2)
    }

    @Test
    fun spansTouchingLowConfidenceWordsAreDropped() {
        // "migration" (index 6) was unclear to the recogniser.
        val r = GroundingValidator.validate(fb(glow(4, 6, "led the migration")), en, setOf(6), false)
        assertThat(r.feedback.glow).isNull()
        assertThat(r.fallbackGlow).isTrue()
    }

    @Test
    fun spansLongerThan40TokensAreRejected() {
        val long = List(60) { "word$it" }
        assertThat(GroundingValidator.span(long, emptySet(), 0, 41, long.subList(0, 42).joinToString(" "))).isNull()
        assertThat(GroundingValidator.span(long, emptySet(), 0, 40, long.subList(0, 41).joinToString(" "))).isEqualTo(0 to 40)
    }

    @Test
    fun stutterFriendlyDropsFluencyGrows() {
        val r = GroundingValidator.validate(fb(grow = grow(1, 2, "last quarter", GrowIssue.REPETITION)), en, emptySet(), stutterFriendly = true)
        assertThat(r.feedback.grow).isNull()
        assertThat(r.fallbackGrow).isTrue()
        val kept = GroundingValidator.validate(fb(grow = grow(1, 2, "last quarter", GrowIssue.FILLER)), en, emptySet(), stutterFriendly = true)
        assertThat(kept.feedback.grow).isNotNull()
    }

    @Test
    fun betterVersionsNeedNewUpgradedWordsAndAreCappedAtThree() {
        val quote = "nobody was fully knowing how the reports were calculated"
        val good = BetterVersion(11, 19, quote, "No one could fully articulate how the reports were calculated.", listOf(UpgradedWord("articulate", "explain", Register.FORMAL), UpgradedWord("reports", "x", Register.CASUAL)))
        val sameAsQuote = BetterVersion(11, 19, quote, quote, listOf(UpgradedWord("fully", "x", Register.CASUAL)))
        val wordMissing = BetterVersion(11, 19, quote, "No one knew how the totals were made.", listOf(UpgradedWord("articulate", "x", Register.FORMAL)))
        val r = GroundingValidator.validate(fb(better = listOf(good, sameAsQuote, wordMissing, good, good, good)), en, emptySet(), false)
        assertThat(r.feedback.betterVersions).hasSize(3) // two invalid ones dropped, the 4th valid one is over the cap
        assertThat(r.dropped).isEqualTo(3)
        assertThat(r.feedback.betterVersions.first().upgradedWords.map { it.word }).containsExactly("articulate") // "reports" was in the quote
        assertThat(r.wordsForYou.map { it.word }).containsExactly("articulate")
    }

    @Test
    fun tooLongRewriteIsDropped() {
        val bv = BetterVersion(1, 2, "last quarter", "x".repeat(100) + " articulate", listOf(UpgradedWord("articulate", "m", Register.FORMAL)))
        val r = GroundingValidator.validate(fb(better = listOf(bv)), en, emptySet(), false)
        assertThat(r.feedback.betterVersions).isEmpty()
        assertThat(r.dropped).isEqualTo(1)
    }

    @Test
    fun mistakesNeedAChangedFix() {
        val ok = Mistake(11, 19, "nobody was fully knowing how the reports were calculated", "nobody fully knew how the reports were calculated")
        val noop = Mistake(1, 2, "last quarter", "Last quarter!")
        val r = GroundingValidator.validate(fb(mistakes = listOf(ok, noop)), en, emptySet(), false)
        assertThat(r.feedback.mistakes).containsExactly(ok)
        assertThat(r.dropped).isEqualTo(1)
    }

    @Test
    fun selfHarmSuppressesAllCoaching() {
        val r = GroundingValidator.validate(fb(glow(1, 2, "last quarter"), safety = Safety.SELF_HARM), en, emptySet(), false)
        assertThat(r.feedback.glow).isNull()
        assertThat(r.feedback.betterVersions).isEmpty()
        assertThat(r.fallbackGlow).isFalse() // the helpline card replaces it; no fallback coaching
    }

    @Test
    fun hindiQuotesMatchThroughNormalisation() {
        // Nukta, candrabindu and danda differences don't break grounding.
        val r = GroundingValidator.validate(fb(grow = grow(6, 11, "उम्म, मतलब, metro तो आ गई")), hi, emptySet(), false)
        assertThat(r.feedback.grow).isNotNull()
        val nukta = GroundingValidator.validate(fb(glow = glow(3, 4, "सबसे बडी")), hi, emptySet(), false) // बड़ी written without the nukta
        assertThat(nukta.feedback.glow).isNotNull()
    }

    @Test
    fun fallbackUsesMetrics() {
        val words = List(40) { Word(if (it % 5 == 0) "um" else "word$it", it * 500L, it * 500L + 300, 0.9) }
        val m = MetricsCalculator.compute(words, Lang.EN)
        val grow = FeedbackFallback.grow(m, Lang.EN)
        assertThat(grow.issue).isEqualTo(GrowIssue.FILLER)
        val glowHi = FeedbackFallback.glow(m, null, 112_000, Lang.HI)
        assertThat(glowHi.note).isNotEmpty()
        assertThat(FeedbackFallback.clock(112_000)).isEqualTo("1:52")
    }
}
