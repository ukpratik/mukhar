package io.github.ukpratik.mukhar.domain.insight

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.metrics.TalkSummary
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test
import java.time.Instant

class InsightRulesTest {
    private fun talk(n: Int, fpm: Double, lang: Lang = Lang.EN, tokens: Int = 80) =
        TalkSummary(lang, Instant.parse("2026-11-01T10:00:00Z").plusSeconds(n * 86_400L), 130.0, fpm, 1.0, 1, null, tokens)

    private fun eval(talks: List<TalkSummary>, streak: Int = 0, words: Int = 0, achieved: Set<String> = emptySet()) =
        InsightRules.evaluate(InsightInput(talks, streak, words, achieved))

    @Test
    fun fillersDown25PercentLastThreeVsFirstThree() {
        val down = listOf(8.0, 8.0, 8.0, 7.0, 6.0, 6.0).mapIndexed { i, f -> talk(i, f) } // 6.33 vs 8 = −20.8%
        assertThat(eval(down)).doesNotContain("fillers_down_25_en")
        val more = listOf(8.0, 8.0, 8.0, 6.0, 6.0, 6.0).mapIndexed { i, f -> talk(i, f) } // exactly −25%
        assertThat(eval(more)).contains("fillers_down_25_en")
    }

    @Test
    fun fillersRuleNeedsSixTalksInOneLanguage() {
        val mixed = listOf(8.0, 8.0, 8.0).mapIndexed { i, f -> talk(i, f) } +
            listOf(2.0, 2.0, 2.0).mapIndexed { i, f -> talk(i + 3, f, Lang.HI) }
        assertThat(eval(mixed).filter { it.startsWith("fillers") }).isEmpty()
    }

    @Test
    fun fillersFromZeroNeverFires() {
        assertThat(eval(List(6) { talk(it, 0.0) })).doesNotContain("fillers_down_25_en")
    }

    @Test
    fun milestones() {
        val talks = List(25) { talk(it, 5.0, tokens = if (it == 3) 100 else 50) }
        assertThat(eval(talks, streak = 30, words = 100)).containsExactly(
            "talks_10", "talks_25", "first_100_tokens", "streak_7", "streak_30", "words_50", "words_100",
        ).inOrder()
    }

    @Test
    fun alreadyAchievedDoNotRepeat() {
        val talks = List(10) { talk(it, 5.0) }
        assertThat(eval(talks, streak = 7, achieved = setOf("talks_10", "streak_7"))).isEmpty()
    }

    @Test
    fun nothingForANewUser() {
        assertThat(eval(emptyList())).isEmpty()
    }
}
