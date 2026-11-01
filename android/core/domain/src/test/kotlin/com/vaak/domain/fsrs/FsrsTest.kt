package com.vaak.domain.fsrs

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Reference cases ported from py-fsrs 6.3.2 `tests/test_basic.py` (MIT). */
class FsrsTest {
    private val start = Instant.parse("2022-11-29T12:30:00Z")
    private fun newCard(at: Instant = start) = Card(id = "c1", due = at)
    private fun daysBetween(a: Instant, b: Instant) = Duration.between(a, b).toDays()

    @Test
    fun referenceIntervalHistory() { // test_review_card
        val fsrs = Fsrs(enableFuzzing = false)
        val ratings = listOf(Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD,
            Rating.AGAIN, Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD)
        var card = newCard()
        var at = start
        val history = ratings.map { r ->
            card = fsrs.review(card, r, at).first
            daysBetween(card.lastReview!!, card.due).also { at = card.due }
        }
        assertThat(history).containsExactly(0L, 2L, 11L, 46L, 163L, 498L, 0L, 0L, 2L, 4L, 7L, 12L, 21L).inOrder()
    }

    @Test
    fun repeatedEasyReachesMinimumDifficulty() { // test_repeated_correct_reviews
        val fsrs = Fsrs(enableFuzzing = false)
        var card = newCard()
        for (k in 0 until 10) card = fsrs.review(card, Rating.EASY, start.plusNanos(k * 1000L)).first
        assertThat(card.difficulty).isEqualTo(1.0)
    }

    @Test
    fun referenceMemoryState() { // test_memo_state
        val fsrs = Fsrs()
        val ratings = listOf(Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD)
        val gaps = listOf(0L, 0L, 1L, 3L, 8L, 21L)
        var card = newCard()
        var at = start
        for ((r, gap) in ratings.zip(gaps)) {
            at = at.plus(Duration.ofDays(gap))
            card = fsrs.review(card, r, at).first
        }
        assertThat(card.stability!!).isWithin(1e-4).of(53.62691)
        assertThat(card.difficulty!!).isWithin(1e-4).of(6.3574867)
    }

    @Test
    fun learningSteps() { // test_{good,again,hard,easy}_learning_steps
        val fsrs = Fsrs()
        val good = fsrs.review(newCard(), Rating.GOOD, start).first
        assertThat(good.state).isEqualTo(CardState.LEARNING)
        assertThat(good.step).isEqualTo(1)
        assertThat(Duration.between(start, good.due)).isEqualTo(Duration.ofMinutes(10))
        val graduated = fsrs.review(good, Rating.GOOD, good.due).first
        assertThat(graduated.state).isEqualTo(CardState.REVIEW)
        assertThat(graduated.step).isNull()
        assertThat(Duration.between(good.due, graduated.due).toHours()).isAtLeast(24)

        val again = fsrs.review(newCard(), Rating.AGAIN, start).first
        assertThat(again.step).isEqualTo(0)
        assertThat(Duration.between(start, again.due)).isEqualTo(Duration.ofMinutes(1))

        val hard = fsrs.review(newCard(), Rating.HARD, start).first
        assertThat(hard.step).isEqualTo(0)
        assertThat(Duration.between(start, hard.due)).isEqualTo(Duration.ofSeconds(330))

        val easy = fsrs.review(newCard(), Rating.EASY, start).first
        assertThat(easy.state).isEqualTo(CardState.REVIEW)
        assertThat(daysBetween(start, easy.due)).isAtLeast(1)
    }

    @Test
    fun reviewLapseAndRelearning() { // test_review_state, test_relearning
        val fsrs = Fsrs(enableFuzzing = false)
        var card = newCard()
        card = fsrs.review(card, Rating.GOOD, card.due).first
        card = fsrs.review(card, Rating.GOOD, card.due).first
        card = fsrs.review(card, Rating.GOOD, card.due).first
        assertThat(card.state).isEqualTo(CardState.REVIEW)
        val (lapsed, log) = fsrs.review(card, Rating.AGAIN, card.due)
        assertThat(lapsed.state).isEqualTo(CardState.RELEARNING)
        assertThat(lapsed.step).isEqualTo(0)
        assertThat(lapsed.lapses).isEqualTo(1)
        assertThat(Duration.between(card.due, lapsed.due)).isEqualTo(Duration.ofMinutes(10))
        assertThat(log.stateBefore).isEqualTo(CardState.REVIEW)
        assertThat(log.rating).isEqualTo(Rating.AGAIN)

        val again = fsrs.review(lapsed, Rating.AGAIN, lapsed.due).first
        assertThat(again.state).isEqualTo(CardState.RELEARNING)
        assertThat(Duration.between(lapsed.due, again.due)).isEqualTo(Duration.ofMinutes(10))
        val back = fsrs.review(again, Rating.GOOD, again.due).first
        assertThat(back.state).isEqualTo(CardState.REVIEW)
        assertThat(Duration.between(again.due, back.due).toHours()).isAtLeast(24)
        assertThat(back.reps).isEqualTo(6)
        assertThat(back.lapses).isEqualTo(1)
    }

    @Test
    fun relearningReviewedADayLateUsesLongTermStability() { // test_long_term_stability_learning_state
        val fsrs = Fsrs()
        var card = fsrs.review(newCard(), Rating.EASY, start).first
        card = fsrs.review(card, Rating.AGAIN, card.due).first
        assertThat(card.state).isEqualTo(CardState.RELEARNING)
        card = fsrs.review(card, Rating.GOOD, card.due.plus(Duration.ofDays(1))).first
        assertThat(card.state).isEqualTo(CardState.REVIEW)
    }

    @Test
    fun fuzzRangeMatchesReference() {
        val fsrs = Fsrs()
        assertThat(fsrs.fuzzRange(11)).isEqualTo(9 to 13)
        assertThat(fsrs.fuzzRange(100)).isEqualTo(93 to 107)
        assertThat(fsrs.fuzzRange(3)).isEqualTo(2 to 4)
    }

    @Test
    fun fuzzStaysInRangeAndUsesInjectedRandom() {
        fun thirdInterval(random: Double): Long {
            val fsrs = Fsrs(random = { random })
            var card = fsrs.review(newCard(), Rating.GOOD, start).first
            card = fsrs.review(card, Rating.GOOD, card.due).first
            val prev = card.due
            card = fsrs.review(card, Rating.GOOD, card.due).first
            return daysBetween(prev, card.due)
        }
        val unfuzzed = Fsrs(enableFuzzing = false).let { f ->
            var c = f.review(newCard(), Rating.GOOD, start).first
            c = f.review(c, Rating.GOOD, c.due).first
            val p = c.due
            daysBetween(p, f.review(c, Rating.GOOD, c.due).first.due)
        }
        val (lo, hi) = Fsrs().fuzzRange(unfuzzed)
        assertThat(thirdInterval(0.0)).isEqualTo(lo.toLong())
        // py-fsrs draws random() * (hi − lo + 1) + lo and rounds half-to-even.
        assertThat(thirdInterval(0.5)).isEqualTo(kotlin.math.round((hi - lo + 1) * 0.5 + lo).toLong())
    }

    @Test
    fun stabilityNeverBelowMinimum() { // test_stability_lower_bound
        val fsrs = Fsrs()
        var card = newCard()
        repeat(1000) {
            card = fsrs.review(card, Rating.AGAIN, card.due.plus(Duration.ofDays(1))).first
            assertThat(card.stability!!).isAtLeast(Fsrs.STABILITY_MIN)
        }
    }

    @Test
    fun sameDayHardDoesNotDecreaseStability() {
        val fsrs = Fsrs(learningSteps = emptyList())
        val at = Instant.parse("2026-01-01T00:00:00Z")
        val first = fsrs.review(newCard(at), Rating.GOOD, at).first
        val second = fsrs.review(first, Rating.HARD, at.plus(Duration.ofMinutes(1))).first
        assertThat(second.stability).isEqualTo(first.stability)
    }

    @Test
    fun maximumIntervalIsRespected() {
        val fsrs = Fsrs(maximumInterval = 100)
        var card = newCard()
        for (r in listOf(Rating.EASY, Rating.GOOD, Rating.EASY, Rating.GOOD)) {
            card = fsrs.review(card, r, card.due).first
            assertThat(daysBetween(card.lastReview!!, card.due)).isAtMost(100)
        }
    }

    @Test
    fun retrievabilityAndPreview() {
        val fsrs = Fsrs(enableFuzzing = false)
        assertThat(fsrs.retrievability(newCard(), start)).isEqualTo(0.0)
        val card = fsrs.review(newCard(), Rating.GOOD, start).first
        assertThat(fsrs.retrievability(card, start.plus(Duration.ofDays(3)))).isIn(com.google.common.collect.Range.closed(0.0, 1.0))
        val preview = fsrs.preview(card, card.due)
        assertThat(preview.keys).containsExactlyElementsIn(Rating.entries)
        assertThat(preview.getValue(Rating.AGAIN).due).isLessThan(preview.getValue(Rating.EASY).due)
    }
}
