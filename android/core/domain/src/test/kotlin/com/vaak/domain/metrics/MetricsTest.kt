package com.vaak.domain.metrics

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.domain.model.Lang
import com.vaak.domain.model.Word
import org.junit.Test
import java.time.Instant

/** Builds evenly spaced words: each [durMs] long with [gapMs] silence before the next. */
private fun words(vararg w: String, durMs: Long = 300, gapMs: Long = 100, conf: Double = 0.9): List<Word> {
    var t = 0L
    return w.map { text -> Word(text, t, t + durMs, conf).also { t += durMs + gapMs } }
}

/** Words with explicit gaps: gaps[i] is the silence before word i+1. */
private fun spaced(text: List<String>, gaps: List<Long>, durMs: Long = 300): List<Word> {
    require(gaps.size == text.size - 1)
    var t = 0L
    return text.mapIndexed { i, s -> Word(s, t, t + durMs, 0.9).also { t += durMs + (gaps.getOrNull(i) ?: 0) } }
}

class WpmTest {
    @Test
    fun spanCapsThinkBreaksAtFiveSeconds() {
        val w = listOf(Word("a", 0, 1000, 0.9), Word("b", 9000, 10_000, 0.9)) // 8 s gap → 3 s excess removed
        assertThat(Wpm.spanMs(w)).isEqualTo(7_000)
        assertThat(Wpm.spanMs(emptyList())).isEqualTo(0)
    }

    @Test
    fun wpmCountsLexicalTokensOnly() {
        // 4 words, one unambiguous filler, over exactly 1 minute.
        val w = listOf(Word("I", 0, 1000, 0.9), Word("um", 1100, 1500, 0.9), Word("like", 1600, 2000, 0.9), Word("tea", 59_000, 60_000, 0.9))
        val m = MetricsCalculator.compute(w, Lang.EN)
        assertThat(m.spanMs).isEqualTo(60_000 - (59_000 - 2000 - 5000))
        assertThat(m.lexicalTokens).isEqualTo(3)
        assertThat(m.fillerCount).isEqualTo(1)
    }

    @Test
    fun bandsPerLanguage() {
        data class Row(val wpm: Double, val lang: Lang, val band: WpmBand)
        val table = listOf(
            Row(109.9, Lang.EN, WpmBand.SLOW), Row(110.0, Lang.EN, WpmBand.COMFORTABLE),
            Row(160.0, Lang.EN, WpmBand.COMFORTABLE), Row(160.1, Lang.EN, WpmBand.FAST),
            Row(99.9, Lang.HI, WpmBand.SLOW), Row(100.0, Lang.HI, WpmBand.COMFORTABLE),
            Row(150.0, Lang.HI, WpmBand.COMFORTABLE), Row(150.1, Lang.HI, WpmBand.FAST),
            Row(0.0, Lang.EN, WpmBand.SLOW),
        )
        for (r in table) assertWithMessage("$r").that(Wpm.band(r.wpm, r.lang)).isEqualTo(r.band)
    }

    @Test
    fun perMinuteHandlesZeroSpan() {
        assertThat(Wpm.perMinute(5, 0)).isEqualTo(0.0)
        assertThat(Wpm.perMinute(30, 30_000)).isEqualTo(60.0)
    }
}

class PausesTest {
    @Test
    fun longPausesAreStrictlyOverOnePointFiveSeconds() {
        val w = spaced(listOf("a", "b", "c", "d"), listOf(1500, 1501, 4000))
        assertThat(Pauses.countLong(w)).isEqualTo(2)
        assertThat(Pauses.countLong(emptyList())).isEqualTo(0)
    }
}

class RepetitionTest {
    private data class Row(val name: String, val lang: Lang, val words: List<Word>, val count: Int, val indices: Set<Int>)

    @Test
    fun table() {
        val rows = listOf(
            Row("EN immediate repeat", Lang.EN, words("I", "I", "think"), 1, setOf(1)),
            Row("EN triple counts twice", Lang.EN, words("the", "the", "the", "end"), 2, setOf(1, 2)),
            Row("EN allowlisted very very", Lang.EN, words("very", "very", "good"), 0, emptySet()),
            Row("EN case and punctuation normalised", Lang.EN, words("We,", "we", "went"), 1, setOf(1)),
            Row("EN gap ≥ 1 s is not a repeat", Lang.EN, spaced(listOf("go", "go"), listOf(1000)), 0, emptySet()),
            Row("EN repeat through a filler doesn't count", Lang.EN, words("I", "um", "I"), 0, emptySet()),
            Row("EN low confidence ignored", Lang.EN, listOf(Word("I", 0, 100, 0.9), Word("I", 150, 250, 0.3)), 0, emptySet()),
            Row("EN bigram repeat", Lang.EN, words("I", "went", "I", "went", "home"), 1, setOf(2, 3)),
            Row("EN two bigram repeats", Lang.EN, words("I", "am", "I", "am", "I", "am"), 2, setOf(2, 3, 4, 5)),
            Row("HI reduplication is grammatical", Lang.HI, words("धीरे", "धीरे", "चलो"), 0, emptySet()),
            Row("HI three identical counts once", Lang.HI, words("बहुत", "बहुत", "बहुत", "अच्छा"), 1, setOf(2)),
            Row("HI bigram repeat counts", Lang.HI, words("मैं", "गया", "मैं", "गया"), 1, setOf(2, 3)),
        )
        for (r in rows) {
            val mask = Fillers.detect(r.words, r.lang).mask
            val result = Repetition.detect(r.words, mask, r.lang, MetricsCalculator.LOW_CONF)
            assertWithMessage(r.name).that(result.count).isEqualTo(r.count)
            assertWithMessage(r.name).that(result.indices).isEqualTo(r.indices)
        }
    }
}

class MattrTest {
    @Test
    fun lockedBelowOneHundredTokens() {
        assertThat(Mattr.compute(List(99) { "w$it" })).isNull()
    }

    @Test
    fun allDistinctIsOne() {
        assertThat(Mattr.compute(List(120) { "w$it" })).isEqualTo(1.0)
    }

    @Test
    fun allSameIsOneFiftieth() {
        assertThat(Mattr.compute(List(100) { "same" })).isWithin(1e-12).of(1.0 / 50)
    }

    @Test
    fun matchesBruteForce() {
        val rnd = java.util.Random(7)
        val tokens = List(300) { "t${rnd.nextInt(60)}" }
        val brute = (0..tokens.size - 50).map { k -> tokens.subList(k, k + 50).toSet().size / 50.0 }.average()
        assertThat(Mattr.compute(tokens)).isWithin(1e-12).of(brute)
    }
}

class MetricsCalculatorTest {
    private fun talk(n: Int, vocab: Int = 1000): List<Word> =
        (0 until n).map { i -> Word("w${i % vocab}", i * 400L, i * 400L + 300, 0.9) }

    @Test
    fun stutterFriendlyHidesPausesAndRepetitions() {
        val w = spaced(listOf("I", "I", "want", "to", "go"), listOf(100, 2000, 100, 100))
        val normal = MetricsCalculator.compute(w, Lang.EN)
        assertThat(normal.longPauses).isEqualTo(1)
        assertThat(normal.repetitions).isEqualTo(1)
        assertThat(normal.repeatIndices).containsExactly(1)

        val friendly = MetricsCalculator.compute(w, Lang.EN, stutterFriendly = true)
        assertThat(friendly.longPauses).isNull()
        assertThat(friendly.longPausesPerMin).isNull()
        assertThat(friendly.repetitions).isNull()
        assertThat(friendly.repeatIndices).isEmpty()
        assertThat(friendly.wpm).isEqualTo(normal.wpm)
        assertThat(friendly.fillerCount).isEqualTo(normal.fillerCount)
    }

    @Test
    fun mattrUnlocksAtOneHundredLexicalConfidentTokens() {
        assertThat(MetricsCalculator.compute(talk(99), Lang.EN).mattr).isNull()
        assertThat(MetricsCalculator.compute(talk(100), Lang.EN).mattr).isEqualTo(1.0)
        // Low-confidence tokens don't count towards the 100.
        val withLowConf = talk(100).mapIndexed { i, w -> if (i == 0) w.copy(conf = 0.5) else w }
        assertThat(MetricsCalculator.compute(withLowConf, Lang.EN).mattr).isNull()
    }

    @Test
    fun emptyTranscriptIsSafe() {
        val m = MetricsCalculator.compute(emptyList(), Lang.HI)
        assertThat(m.wpm).isEqualTo(0.0)
        assertThat(m.wpmBand).isEqualTo(WpmBand.SLOW)
        assertThat(m.mattr).isNull()
        assertThat(m.algoVersion).isEqualTo(1)
    }

    @Test
    fun comfortableEnglishTalk() {
        // 140 words over 60 s with no fillers.
        val w = (0 until 140).map { i -> Word("w$i", i * 60_000L / 140, i * 60_000L / 140 + 300, 0.9) }
        val m = MetricsCalculator.compute(w, Lang.EN)
        assertThat(m.wpm).isWithin(1.0).of(140.0)
        assertThat(m.wpmBand).isEqualTo(WpmBand.COMFORTABLE)
        assertThat(m.fillersPerMin).isEqualTo(0.0)
    }

    @Test
    fun sixHundredTokensUnderFiftyMs() {
        val w = talk(600, vocab = 120)
        repeat(20) { MetricsCalculator.compute(w, Lang.EN) } // warm up the JIT
        val start = System.nanoTime()
        MetricsCalculator.compute(w, Lang.EN)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertThat(ms).isLessThan(50L)
    }
}

class ComparisonTest {
    private fun summary(lang: Lang, day: Int, fpm: Double, pauses: Double? = 1.0) = TalkSummary(
        lang, Instant.parse("2026-11-01T10:00:00Z").plusSeconds(day * 86_400L), 130.0, fpm, pauses, 2, null, 150,
    )

    @Test
    fun averagesLastFiveSameLanguageBeforeTheTalk() {
        val history = (1..7).map { summary(Lang.EN, it, it.toDouble()) } + summary(Lang.HI, 8, 100.0)
        val avg = Comparison.previousAverage(history, Lang.EN, before = Instant.parse("2026-11-20T00:00:00Z"))!!
        assertThat(avg.talks).isEqualTo(5)
        assertThat(avg.fillersPerMin).isEqualTo((3 + 4 + 5 + 6 + 7) / 5.0)
        assertThat(avg.mattr).isNull()
    }

    @Test
    fun noHistoryInLanguageGivesNull() {
        assertThat(Comparison.previousAverage(listOf(summary(Lang.HI, 1, 2.0)), Lang.EN, Instant.MAX)).isNull()
    }
}
