package io.github.ukpratik.mukhar.domain.revise

import kotlin.math.ceil

/**
 * I-002: the "~N min" next to a due count, the same on Today and in Words. A card takes about 15 seconds (the same
 * 0.25 min per card the daily-words setting quotes), rounded up, at least 1 minute.
 */
object ReviewEstimate {
    const val MINUTES_PER_CARD = 0.25

    fun minutes(cards: Int): Int = ceil(cards.coerceAtLeast(0) * MINUTES_PER_CARD).toInt().coerceAtLeast(1)
}
