package io.github.ukpratik.mukhar.domain.words

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class WordDeckTest {
    private val t0 = Instant.parse("2026-11-12T05:00:00Z")

    private fun d(id: String, choice: DeckChoice, seq: Long, at: Instant = t0) = DeckDecision(id, choice, at, seq)

    @Test
    fun decidedWordsLeaveTheDeckAndNewWordsKeepTheirOrder() {
        val decisions = listOf(d("a", DeckChoice.DONE, 1), d("b", DeckChoice.STARRED, 2), d("c", DeckChoice.DISCARDED, 3), d("e", DeckChoice.LATER, 4))
        assertThat(WordDeck.queue(listOf("a", "b", "c", "d", "e", "f"), decisions, t0)).containsExactly("d", "f").inOrder()
    }

    @Test
    fun laterComesBackAfterThreeDays() {
        val later = d("x", DeckChoice.LATER, 1)
        val decisions = listOf(later)
        assertThat(WordDeck.queue(listOf("y"), decisions, t0.plus(Duration.ofDays(3)).minusSeconds(1))).containsExactly("y")
        assertThat(WordDeck.queue(listOf("y"), decisions, t0.plus(Duration.ofDays(3)))).containsExactly("x", "y").inOrder()
    }

    @Test
    fun laterComesBackAfterFiftyMoreCards() {
        val decisions = listOf(d("x", DeckChoice.LATER, 1)) + (2L..50L).map { d("w$it", DeckChoice.DONE, it) }
        assertThat(WordDeck.queue(listOf("n"), decisions, t0)).containsExactly("n") // 49 more
        val fifty = decisions + d("w51", DeckChoice.DONE, 51)
        assertThat(WordDeck.queue(listOf("n"), fifty, t0)).containsExactly("x", "n").inOrder()
    }

    @Test
    fun theLongestWaitingLaterCardComesFirstAndExcludedWordsNeverShow() {
        val old = t0.minus(Duration.ofDays(5))
        val decisions = listOf(d("late2", DeckChoice.LATER, 2, old), d("late1", DeckChoice.LATER, 1, old))
        assertThat(WordDeck.queue(listOf("n", "talk"), decisions, t0, exclude = setOf("talk"))).containsExactly("late1", "late2", "n").inOrder()
    }

    @Test
    fun todayCountsEveryDecisionSinceTheDayStarted() {
        val dayStart = t0.minus(Duration.ofHours(2))
        val decisions = listOf(d("a", DeckChoice.DONE, 1, dayStart.minusSeconds(1)), d("b", DeckChoice.LATER, 2), d("c", DeckChoice.DISCARDED, 3))
        assertThat(WordDeck.decidedToday(decisions, dayStart)).isEqualTo(2)
        assertThat(WordDeck.nextSeq(decisions)).isEqualTo(4)
        assertThat(WordDeck.nextSeq(emptyList())).isEqualTo(1)
    }

    @Test
    fun onlyDoneAndStarredAreRevised() {
        assertThat(DeckChoice.entries.filter { it.inGym }).containsExactly(DeckChoice.DONE, DeckChoice.STARRED)
        assertThat(DeckChoice.of("later")).isEqualTo(DeckChoice.LATER)
    }

    @Test
    fun theDeckOrderHasNoCap() {
        val pool = (1..120).map { WordCandidate("en:w$it", "en", setOf("everyday")) }
        val order = DailyWordPicker.order(pool, listOf("en"), listOf("everyday"), LocalDate.of(2026, 11, 12))
        assertThat(order).hasSize(120)
        assertThat(order.map { it.id }.toSet()).hasSize(120)
        // The daily pick is the start of the same order.
        val pick = DailyWordPicker.pick(5, emptyList(), pool, listOf("en"), listOf("everyday"), LocalDate.of(2026, 11, 12))
        assertThat(pick).isEqualTo(order.take(5))
    }
}
