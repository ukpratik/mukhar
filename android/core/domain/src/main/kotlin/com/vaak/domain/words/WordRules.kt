package com.vaak.domain.words

import com.vaak.domain.text.Normalizer
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
 */
object DailyWordPicker {
    const val DEFAULT_COUNT = 3
    val COUNT_RANGE = 1..5

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
        val n = count.coerceIn(COUNT_RANGE)
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
