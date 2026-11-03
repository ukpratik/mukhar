package io.github.ukpratik.mukhar.domain.insight

import io.github.ukpratik.mukhar.domain.metrics.Mattr
import io.github.ukpratik.mukhar.domain.metrics.TalkSummary
import io.github.ukpratik.mukhar.domain.model.Lang

data class InsightInput(
    /** Coached talks (not re-takes, read-aloud or baseline), any order. */
    val talks: List<TalkSummary>,
    val bestStreak: Int,
    val wordsLearned: Int,
    /** Rule keys already achieved (`insight` table); each fires once. */
    val achieved: Set<String>,
)

/**
 * R1 insight rules (PRD US-050): fillers per minute down ≥ 25% (last 3 vs first 3 talks, per language, needs ≥ 6
 * talks so the groups don't overlap); 10, 25 or 50 talks; first talk with ≥ 100 tokens; 7, 30 or 100-day streak;
 * 50, 100 or 250 words learned.
 */
object InsightRules {
    val TALK_MILESTONES = listOf(10, 25, 50)
    val STREAK_MILESTONES = listOf(7, 30, 100)
    val WORD_MILESTONES = listOf(50, 100, 250)
    const val FILLER_DROP = 0.25
    const val FILLER_GROUP = 3

    fun fillersDownKey(lang: Lang) = "fillers_down_25_${lang.code}"
    fun talksKey(n: Int) = "talks_$n"
    fun streakKey(n: Int) = "streak_$n"
    fun wordsKey(n: Int) = "words_$n"
    const val FIRST_100_TOKENS = "first_100_tokens"

    /** Newly achieved rule keys, in a stable display order. */
    fun evaluate(input: InsightInput): List<String> {
        val hits = mutableListOf<String>()
        for (lang in Lang.entries) {
            val talks = input.talks.filter { it.lang == lang }.sortedBy { it.createdAt }
            if (talks.size >= 2 * FILLER_GROUP) {
                val first = talks.take(FILLER_GROUP).map { it.fillersPerMin }.average()
                val last = talks.takeLast(FILLER_GROUP).map { it.fillersPerMin }.average()
                if (first > 0 && last <= first * (1 - FILLER_DROP)) hits += fillersDownKey(lang)
            }
        }
        TALK_MILESTONES.filter { input.talks.size >= it }.forEach { hits += talksKey(it) }
        if (input.talks.any { it.lexicalTokens >= Mattr.MIN_TOKENS }) hits += FIRST_100_TOKENS
        STREAK_MILESTONES.filter { input.bestStreak >= it }.forEach { hits += streakKey(it) }
        WORD_MILESTONES.filter { input.wordsLearned >= it }.forEach { hits += wordsKey(it) }
        return hits.filter { it !in input.achieved }
    }
}
