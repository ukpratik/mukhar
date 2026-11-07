package io.github.ukpratik.mukhar.domain.words

import io.github.ukpratik.mukhar.domain.text.Normalizer
import java.time.LocalDate

/**
 * US-021: words harvested from talks enter the Word Gym at most [DAILY_CAP] a day; the rest wait for the next
 * day with room ("Saved for 8 Nov" on S14).
 */
object HarvestPolicy {
    const val DAILY_CAP = 5

    /** The first streak day on or after [today] with fewer than [cap] harvested words already released on it. */
    fun releaseDay(today: LocalDate, releasedPerDay: Map<LocalDate, Int>, cap: Int = DAILY_CAP): LocalDate {
        require(cap > 0) { "cap must be positive" }
        var day = today
        while ((releasedPerDay[day] ?: 0) >= cap) day = day.plusDays(1)
        return day
    }
}

/** A word the daily picker can choose from. [tracks] are goal-track keys (S02). */
data class WordCandidate(val id: String, val lang: String, val tracks: Set<String>)

/**
 * US-020 daily words: words from the user's talks first ("your talk is the curriculum"), then curated words for the
 * user's tracks, round-robin across tracks (and languages, when both are picked). Deterministic for a given day,
 * so the repository can persist the result and show the same words all day.
 *
 * US-076 (D-045): the repository calls this once per language with that language's count, and again with just the
 * missing number when the count is raised during the day (a top-up), so [pick] itself only caps at [MAX_COUNT].
 */
object DailyWordPicker {
    /** New words a day per language (US-076). */
    const val DEFAULT_COUNT = 5
    const val MIN_COUNT = 5
    const val MAX_COUNT = 50
    val COUNT_RANGE = MIN_COUNT..MAX_COUNT

    /** Above this the settings show "That's a lot" (a soft note, never a block). */
    const val SOFT_LIMIT = 20

    /** "≈ N min of revision a day once it builds up": about 0.25 min per new word a day, rounded, at least 1. */
    fun revisionMinutes(perDay: Int): Int = Math.round(perDay.coerceAtLeast(0) * MINUTES_PER_WORD).toInt().coerceAtLeast(1)

    private const val MINUTES_PER_WORD = 0.25

    /**
     * @param talkWords harvested words released by today and not shown as daily words yet, oldest first.
     * @param curated the curated pool, minus words already in the user's Word Gym.
     */
    fun pick(
        count: Int,
        talkWords: List<WordCandidate>,
        curated: List<WordCandidate>,
        langs: List<String>,
        tracks: List<String>,
        day: LocalDate,
    ): List<WordCandidate> {
        val n = count.coerceIn(0, MAX_COUNT)
        if (n == 0) return emptyList()
        val chosen = talkWords.take(n).toMutableList()
        if (chosen.size >= n) return chosen
        val taken = chosen.map { it.id }.toMutableSet()
        val langOrder = langs.ifEmpty { curated.map { it.lang }.distinct() }
        // A stable order that still moves day to day: hash of (id, day) so two users with the same tracks differ by day.
        val ordered = curated.sortedBy { stableHash(it.id, day) }
        val slots = langOrder.flatMap { l -> tracks.ifEmpty { listOf(ANY) }.map { t -> l to t } }
        var slot = Math.floorMod(day.toEpochDay(), slots.size.coerceAtLeast(1).toLong()).toInt()
        var misses = 0
        while (chosen.size < n && misses < slots.size) {
            val (lang, track) = slots[slot % slots.size]
            val next = ordered.firstOrNull { it.id !in taken && it.lang == lang && (track == ANY || track in it.tracks) }
            if (next == null) {
                misses++
            } else {
                misses = 0
                chosen += next
                taken += next.id
            }
            slot++
        }
        // Tracks ran dry: fall back to any curated word in the user's languages.
        for (c in ordered) {
            if (chosen.size >= n) break
            if (c.id !in taken && (langOrder.isEmpty() || c.lang in langOrder)) {
                chosen += c
                taken += c.id
            }
        }
        return chosen
    }

    private const val ANY = "*"

    private fun stableHash(id: String, day: LocalDate): Int {
        var h = 1_125_899_907
        for (ch in "$id|${day.toEpochDay() / 7}") h = 31 * h + ch.code
        return h xor (h ushr 16)
    }
}

/**
 * B-rc1-01 / US-076: how today's set follows a count changed during the day. Raising it adds the difference;
 * lowering it removes only words the user hasn't opened or graded, newest first, so nothing already seen goes away
 * (the set can stay above the new count).
 */
object DailyWordPlan {
    data class Plan(val add: Int, val remove: List<String>)

    /** [current] is today's words for one language in position order; [seen] the ids opened or graded. */
    fun plan(target: Int, current: List<String>, seen: Set<String>): Plan {
        val diff = target - current.size
        if (diff >= 0) return Plan(diff, emptyList())
        val removable = current.filter { it !in seen }.asReversed()
        return Plan(0, removable.take(-diff))
    }
}

/**
 * US-077: the learnt-words history. A word belongs to the first day it was shown (a daily word) or released from a
 * talk; days newest first, words in the order they arrived within a day.
 */
object WordHistory {
    data class Entry(val wordId: String, val day: LocalDate, val order: Long)
    data class Day(val day: LocalDate, val wordIds: List<String>)

    fun group(entries: List<Entry>, today: LocalDate): List<Day> = entries
        .filter { !it.day.isAfter(today) }
        .groupBy { it.wordId }
        .map { (_, es) -> es.minWith(compareBy<Entry> { it.day }.thenBy { it.order }) }
        .groupBy { it.day }
        .toSortedMap(reverseOrder())
        .map { (day, es) -> Day(day, es.sortedBy { it.order }.map { it.wordId }) }

    /** The days of [today]'s week window for "Revise all from this week": the last 7 streak days, today included. */
    fun weekStart(today: LocalDate): LocalDate = today.minusDays(6)
}

/**
 * US-023 fuzzy target-word match (mirrors the Worker's `wordFound`): the target's normalised tokens against every
 * same-length window of the transcript, Levenshtein ≤ 2 (≤ 1 for targets of 4 characters or fewer). Hindi also
 * accepts a common inflection of the stem (सुधार → सुधारा, सुधारें).
 */
object TargetWordMatcher {
    fun found(target: String, transcriptWords: List<String>): Boolean {
        val t = Normalizer.norm(target).split(' ').filter(String::isNotEmpty)
        if (t.isEmpty()) return false
        val words = transcriptWords.map(Normalizer::norm).flatMap { it.split(' ') }.filter(String::isNotEmpty)
        val tj = t.joinToString(" ")
        val maxDist = if (tj.length <= SHORT) 1 else 2
        for (i in 0..words.size - t.size) {
            val window = words.subList(i, i + t.size).joinToString(" ")
            if (levenshtein(window, tj) <= maxDist) return true
            if (isDevanagari(tj) && tj.length > SHORT && window.startsWith(tj.dropLast(1))) return true
        }
        return false
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private const val SHORT = 4
    private fun isDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }
}
