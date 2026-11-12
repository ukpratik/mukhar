package io.github.ukpratik.mukhar.domain.words

import java.time.Duration
import java.time.Instant

/** What the user decided for a card in the new word deck (US-081, D-054). */
enum class DeckChoice(val key: String) {
    /** Learnt: Done list and Word Gym; never back in the deck. */
    DONE("done"),

    /** Saved or liked: Starred list and Word Gym; never back in the deck. */
    STARRED("starred"),

    /** Not wanted: never back in the deck (unless brought back). */
    DISCARDED("discarded"),

    /** Not now: back in the deck after [WordDeck.LATER_DAYS] days or [WordDeck.LATER_CARDS] more cards. */
    LATER("later"),
    ;

    /** Done and Starred words are in spaced revision. */
    val inGym: Boolean get() = this == DONE || this == STARRED

    companion object {
        fun of(key: String): DeckChoice? = entries.firstOrNull { it.key == key }
    }
}

/** One decision. [seq] numbers decisions 1, 2, 3… so "50 more cards" is a difference of [seq]s. */
data class DeckDecision(val wordId: String, val choice: DeckChoice, val decidedAt: Instant, val seq: Long)

/**
 * US-081 new word deck rules: which words are in the deck, in what order, and how today's goal is counted.
 * Pure, so the repository just feeds it rows.
 */
object WordDeck {
    const val LATER_DAYS = 3L
    const val LATER_CARDS = 50L

    /** A Later card is back once [LATER_DAYS] have passed or [LATER_CARDS] more cards were decided, whichever is first. */
    fun laterIsBack(d: DeckDecision, now: Instant, latestSeq: Long): Boolean =
        d.choice == DeckChoice.LATER &&
            (!now.isBefore(d.decidedAt.plus(Duration.ofDays(LATER_DAYS))) || latestSeq - d.seq >= LATER_CARDS)

    /**
     * The deck: Later cards that are back (the longest-waiting first), then new words in [newOrder] that have no
     * decision. Words in [exclude] (already in the Word Gym another way, e.g. from a talk) never show.
     */
    fun queue(newOrder: List<String>, decisions: List<DeckDecision>, now: Instant, exclude: Set<String> = emptySet()): List<String> {
        val latest = decisions.maxOfOrNull { it.seq } ?: 0L
        val decided = decisions.map { it.wordId }.toSet()
        val back = decisions.filter { laterIsBack(it, now, latest) && it.wordId !in exclude }.sortedBy { it.seq }.map { it.wordId }
        return back + newOrder.filter { it !in decided && it !in exclude }
    }

    /** Words decided since [dayStart] (any choice), for "3 of 5 today". */
    fun decidedToday(decisions: List<DeckDecision>, dayStart: Instant): Int = decisions.count { !it.decidedAt.isBefore(dayStart) }

    /** The next decision number. */
    fun nextSeq(decisions: List<DeckDecision>): Long = (decisions.maxOfOrNull { it.seq } ?: 0L) + 1
}
