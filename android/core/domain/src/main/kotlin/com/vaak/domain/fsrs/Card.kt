package com.vaak.domain.fsrs

import java.time.Instant

enum class Rating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    companion object {
        fun of(value: Int): Rating = entries.first { it.value == value }
    }
}

/** Stored as `card.state` (LLD §2.1). NEW = never reviewed (py-fsrs: Learning, step 0, no memory state). */
enum class CardState(val value: Int) {
    NEW(0), LEARNING(1), REVIEW(2), RELEARNING(3);

    companion object {
        fun of(value: Int): CardState = entries.first { it.value == value }
    }
}

/** FSRS scheduling state of one card. [stability] and [difficulty] are `null` until the first review. */
data class Card(
    val id: String,
    val due: Instant,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: CardState = CardState.NEW,
    val step: Int? = 0,
    val lastReview: Instant? = null,
)

data class ReviewLogEntry(
    val cardId: String,
    val rating: Rating,
    val reviewedAt: Instant,
    val stateBefore: CardState,
    val stabilityAfter: Double,
    val durationMs: Long?,
)
