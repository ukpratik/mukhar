package com.vaak.domain.revise

import com.vaak.domain.fsrs.Card
import com.vaak.domain.fsrs.CardState
import java.time.Instant

/** `card.type` (LLD §2.1). Saved lines are self-graded (read aloud). */
enum class CardType(val key: String) {
    WORD_MEANING("word_meaning"),
    MEANING_WORD("meaning_word"),
    MISTAKE_FIX("mistake_fix"),
    SAVED_LINE("saved_line"),
}

data class QueueItem(val card: Card, val type: CardType, val createdAt: Instant, val suspended: Boolean = false)

/**
 * Builds a quick-revision session (US-022, US-040, LLD §4.5): cards due before the next streak cutoff, oldest due
 * first, at most [maxItems]; new cards capped at [newPerDay] minus those already introduced today (extra talk words
 * wait for later days, US-021); types interleaved so no 3 of a type run in a row when avoidable.
 */
object QueueBuilder {
    const val MAX_ITEMS = 15
    const val NEW_PER_DAY = 5
    private const val MAX_RUN = 2

    fun build(
        items: List<QueueItem>,
        cutoff: Instant,
        maxItems: Int = MAX_ITEMS,
        newPerDay: Int = NEW_PER_DAY,
        newIntroducedToday: Int = 0,
    ): List<QueueItem> {
        require(maxItems >= 0 && newPerDay >= 0) { "limits must be non-negative" }
        val active = items.filter { !it.suspended }
        val due = active.filter { it.card.state != CardState.NEW && it.card.due < cutoff }
            .sortedWith(compareBy({ it.card.due }, { it.card.id }))
        val newCap = (newPerDay - newIntroducedToday).coerceAtLeast(0)
        val fresh = active.filter { it.card.state == CardState.NEW }
            .sortedWith(compareBy({ it.createdAt }, { it.card.id }))
            .take(newCap)
        val dueTaken = due.take(maxItems)
        val chosen = dueTaken + fresh.take(maxItems - dueTaken.size)
        return interleave(chosen)
    }

    /** Greedy: take the highest-priority remaining item that doesn't make a run of 3 of one type. */
    private fun interleave(priority: List<QueueItem>): List<QueueItem> {
        val remaining = priority.toMutableList()
        val out = ArrayList<QueueItem>(priority.size)
        while (remaining.isNotEmpty()) {
            val runType = out.takeLast(MAX_RUN).map { it.type }.distinct().singleOrNull()
                ?.takeIf { out.size >= MAX_RUN }
            val index = remaining.indexOfFirst { it.type != runType }.takeIf { it >= 0 } ?: 0
            out += remaining.removeAt(index)
        }
        return out
    }
}
