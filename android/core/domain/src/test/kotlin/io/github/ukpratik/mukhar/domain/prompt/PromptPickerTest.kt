package io.github.ukpratik.mukhar.domain.prompt

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test
import kotlin.random.Random

class PromptPickerTest {
    private fun prompt(i: Int, lang: Lang = Lang.EN, track: Track = Track.EVERYDAY) =
        Prompt("p$i", lang, setOf(track), Difficulty.EASY, "Prompt $i", null, emptyList(), null)

    @Test
    fun neverRepeatsTheLast30() {
        val pool = (1..40).map { prompt(it) }
        val recent = ArrayDeque<String>()
        val random = Random(7)
        var current: String? = null
        repeat(500) {
            val next = PromptPicker.next(pool, recent.toList(), current, random)!!
            assertThat(next.id).isNotIn(recent.take(30))
            assertThat(next.id).isNotEqualTo(current)
            recent.addFirst(next.id)
            current = next.id
        }
    }

    @Test
    fun smallPoolsStillShuffleWithoutImmediateRepeats() {
        val pool = (1..5).map { prompt(it) }
        val recent = listOf("p3", "p1", "p5", "p2", "p4") // all 5 shown recently, p4 longest ago
        val next = PromptPicker.next(pool, recent, currentId = "p3")
        assertThat(next!!.id).isEqualTo("p4")
    }

    @Test
    fun singlePromptPoolReturnsIt() {
        assertThat(PromptPicker.next(listOf(prompt(1)), listOf("p1"), "p1")!!.id).isEqualTo("p1")
        assertThat(PromptPicker.next(emptyList(), emptyList(), null)).isNull()
    }

    @Test
    fun poolFiltersByLanguageAndTrack() {
        val all = listOf(prompt(1), prompt(2, Lang.HI), prompt(3, track = Track.INTERVIEWS))
        assertThat(PromptPicker.pool(all, Lang.EN, Track.EVERYDAY).map { it.id }).containsExactly("p1")
        assertThat(PromptPicker.pool(all, Lang.HI, Track.EVERYDAY).map { it.id }).containsExactly("p2")
    }

    @Test
    fun donePromptsNeverComeBackWhileOthersRemain() {
        val pool = (1..10).map { prompt(it) }
        val done = setOf("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8")
        val random = Random(1)
        val recent = ArrayDeque<String>()
        var current: String? = null
        repeat(100) {
            val next = PromptPicker.next(pool, recent.toList(), current, random, done)!!
            assertThat(next.id).isIn(listOf("p9", "p10"))
            recent.addFirst(next.id)
            current = next.id
        }
    }

    @Test
    fun whenEveryPromptIsDoneTheyComeBackLeastRecentFirst() {
        val pool = (1..3).map { prompt(it) }
        val next = PromptPicker.next(pool, listOf("p2", "p3", "p1"), "p2", Random(1), done = setOf("p1", "p2", "p3"))
        assertThat(next!!.id).isEqualTo("p1")
    }
}
