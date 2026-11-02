package com.vaak.domain.revise

import com.google.common.truth.Truth.assertThat
import com.vaak.domain.fsrs.Card
import com.vaak.domain.fsrs.CardState
import org.junit.Test
import java.time.Duration
import java.time.Instant

class QueueBuilderTest {
    private val now = Instant.parse("2026-11-02T05:00:00Z")
    private val cutoff = Instant.parse("2026-11-02T21:30:00Z") // next 03:00 IST

    private fun review(id: String, type: CardType, dueInMin: Long, suspended: Boolean = false) = QueueItem(
        Card(id, now.plus(Duration.ofMinutes(dueInMin)), stability = 3.0, difficulty = 5.0, state = CardState.REVIEW, step = null),
        type, now.minus(Duration.ofDays(10)), suspended,
    )

    private fun fresh(id: String, type: CardType, createdMinAgo: Long) =
        QueueItem(Card(id, now), type, now.minus(Duration.ofMinutes(createdMinAgo)))

    @Test
    fun takesOnlyCardsDueBeforeTheCutoffOldestFirst() {
        val items = listOf(
            review("b", CardType.WORD_MEANING, dueInMin = -10),
            review("a", CardType.MISTAKE_FIX, dueInMin = -60),
            review("later", CardType.WORD_MEANING, dueInMin = 24 * 60),
            review("susp", CardType.WORD_MEANING, dueInMin = -5, suspended = true),
        )
        assertThat(QueueBuilder.build(items, cutoff).map { it.card.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun capsAtFifteenItems() {
        val items = (0 until 30).map { review("r$it", CardType.entries[it % 4], dueInMin = -it.toLong()) }
        assertThat(QueueBuilder.build(items, cutoff)).hasSize(15)
    }

    @Test
    fun capsNewCardsPerDayIncludingThoseAlreadyIntroduced() {
        val items = (0 until 8).map { fresh("n$it", CardType.WORD_MEANING, createdMinAgo = 100L - it) }
        assertThat(QueueBuilder.build(items, cutoff).map { it.card.id }).containsExactly("n0", "n1", "n2", "n3", "n4").inOrder()
        assertThat(QueueBuilder.build(items, cutoff, newIntroducedToday = 3)).hasSize(2)
        assertThat(QueueBuilder.build(items, cutoff, newIntroducedToday = 9)).isEmpty()
    }

    @Test
    fun dueReviewsComeBeforeNewWhenFull() {
        val reviews = (0 until 14).map { review("r$it", CardType.entries[it % 4], dueInMin = -it.toLong()) }
        val news = (0 until 5).map { fresh("n$it", CardType.MEANING_WORD, createdMinAgo = 10) }
        val q = QueueBuilder.build(reviews + news, cutoff)
        assertThat(q).hasSize(15)
        assertThat(q.count { it.card.state == CardState.NEW }).isEqualTo(1)
    }

    @Test
    fun neverThreeOfATypeInARowWhenAvoidable() {
        val items = (0 until 6).map { review("w$it", CardType.WORD_MEANING, -100L + it) } +
            (0 until 3).map { review("m$it", CardType.MISTAKE_FIX, -50L + it) } +
            listOf(review("s0", CardType.SAVED_LINE, -1))
        val types = QueueBuilder.build(items, cutoff).map { it.type }
        assertThat(types).hasSize(10)
        for (k in 2 until types.size) {
            assertThat(types[k] == types[k - 1] && types[k] == types[k - 2]).isFalse()
        }
    }

    @Test
    fun allOneTypeStillReturnsEverything() {
        val items = (0 until 5).map { review("w$it", CardType.SAVED_LINE, -it.toLong()) }
        assertThat(QueueBuilder.build(items, cutoff)).hasSize(5)
    }

    @Test
    fun newCapCanApplyToWordCardsOnly() {
        val now = java.time.Instant.parse("2026-11-06T04:30:00Z")
        fun item(id: String, type: CardType) =
            QueueItem(com.vaak.domain.fsrs.Card(id, now.minusSeconds(60)), type, now.minusSeconds(60))
        val items = (1..4).map { item("w$it", CardType.WORD_MEANING) } + (1..3).map { item("f$it", CardType.MISTAKE_FIX) }
        val q = QueueBuilder.build(items, now.plusSeconds(3600), newPerDay = 2, newCapTypes = setOf(CardType.WORD_MEANING, CardType.MEANING_WORD))
        assertThat(q.count { it.type == CardType.WORD_MEANING }).isEqualTo(2)
        assertThat(q.count { it.type == CardType.MISTAKE_FIX }).isEqualTo(3)
    }
}
