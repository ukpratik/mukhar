package io.github.ukpratik.mukhar.domain.words

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.fsrs.Rating
import org.junit.Test
import java.time.LocalDate

class WordRulesTest {
    private val today = LocalDate.of(2026, 11, 6)

    // --- HarvestPolicy (US-021) ---

    @Test
    fun harvestUsesTodayWhileUnderTheCap() {
        assertThat(HarvestPolicy.releaseDay(today, mapOf(today to 4))).isEqualTo(today)
    }

    @Test
    fun harvestCarriesOverToTheNextDayWithRoom() {
        val counts = mapOf(today to 5, today.plusDays(1) to 5, today.plusDays(2) to 2)
        assertThat(HarvestPolicy.releaseDay(today, counts)).isEqualTo(today.plusDays(2))
    }

    @Test
    fun harvestIgnoresPastDays() {
        assertThat(HarvestPolicy.releaseDay(today, mapOf(today.minusDays(1) to 9))).isEqualTo(today)
    }

    // --- DailyWordPicker (US-020) ---

    private fun c(id: String, lang: String = "en", vararg tracks: String) = WordCandidate(id, lang, tracks.toSet())

    private val curated = (1..30).map { c("en:w$it", "en", if (it % 2 == 0) "interviews" else "professional") } +
        (1..30).map { c("hi:w$it", "hi", "everyday") }

    @Test
    fun talkWordsComeFirst() {
        val talk = listOf(c("en:pivotal"), c("en:articulate"))
        val picked = DailyWordPicker.pick(3, talk, curated, listOf("en"), listOf("interviews"), today)
        assertThat(picked.map { it.id }.take(2)).containsExactly("en:pivotal", "en:articulate").inOrder()
        assertThat(picked).hasSize(3)
        assertThat(picked[2].tracks).contains("interviews")
    }

    @Test
    fun moreTalkWordsThanSlotsFillEverySlot() {
        val talk = (1..7).map { c("en:t$it") }
        assertThat(DailyWordPicker.pick(5, talk, curated, listOf("en"), emptyList(), today).map { it.id })
            .containsExactly("en:t1", "en:t2", "en:t3", "en:t4", "en:t5").inOrder()
    }

    @Test
    fun countIsClampedToOneToFive() {
        assertThat(DailyWordPicker.pick(9, emptyList(), curated, listOf("en"), listOf("professional"), today)).hasSize(5)
        assertThat(DailyWordPicker.pick(0, emptyList(), curated, listOf("en"), listOf("professional"), today)).hasSize(1)
    }

    @Test
    fun sameDaySameWordsAndOnlyTheUsersTracksAndLanguages() {
        val a = DailyWordPicker.pick(3, emptyList(), curated, listOf("en"), listOf("professional"), today)
        val b = DailyWordPicker.pick(3, emptyList(), curated.shuffled(), listOf("en"), listOf("professional"), today)
        assertThat(a).isEqualTo(b)
        assertThat(a.all { it.lang == "en" && "professional" in it.tracks }).isTrue()
    }

    @Test
    fun bothLanguagesAreMixed() {
        val picked = DailyWordPicker.pick(4, emptyList(), curated, listOf("en", "hi"), listOf("everyday", "professional"), today)
        assertThat(picked.map { it.lang }.toSet()).containsExactly("en", "hi")
        assertThat(picked.map { it.id }.toSet()).hasSize(4)
    }

    @Test
    fun fallsBackToAnyCuratedWordWhenTracksRunDry() {
        val picked = DailyWordPicker.pick(3, emptyList(), curated.take(2), listOf("en"), listOf("content_creation"), today)
        assertThat(picked).hasSize(2)
    }

    // --- TargetWordMatcher + verdict mapping (US-023) ---

    @Test
    fun fuzzyMatchToleratesSmallSttSlips() {
        assertThat(TargetWordMatcher.found("articulate", "I will articulated the plan".split(' '))).isTrue()
        assertThat(TargetWordMatcher.found("meticulous", "she is very meticulus".split(' '))).isTrue()
        assertThat(TargetWordMatcher.found("ahead of schedule", "We launched ahead of schedule.".split(' '))).isTrue()
        assertThat(TargetWordMatcher.found("pivotal", "it was very important".split(' '))).isFalse()
    }

    @Test
    fun shortWordsNeedACloserMatch() {
        assertThat(TargetWordMatcher.found("keen", "I am keep on it".split(' '))).isTrue()
        assertThat(TargetWordMatcher.found("keen", "I am kept on it".split(' '))).isFalse()
    }

    @Test
    fun hindiInflectionsMatch() {
        assertThat(TargetWordMatcher.found("सुधारना", "हमें यह process सुधारना होगा।".split(' '))).isTrue()
        assertThat(TargetWordMatcher.found("ज़िम्मेदारी", "यह मेरी जिम्मेदारी है".split(' '))).isTrue()
    }

    @Test
    fun verdictMapsResultAndWordFound() {
        assertThat(SentenceCheckRules.verdict("ok", true, "pivotal", "That call was pivotal")).isEqualTo(SentenceVerdict.NICELY_USED)
        assertThat(SentenceCheckRules.verdict("ok", false, "pivotal", "That call was important")).isEqualTo(SentenceVerdict.ALMOST)
        // The client match rescues a server miss.
        assertThat(SentenceCheckRules.verdict("ok", false, "pivotal", "That call was pivotal")).isEqualTo(SentenceVerdict.NICELY_USED)
        assertThat(SentenceCheckRules.verdict("almost", true, "pivotal", "x")).isEqualTo(SentenceVerdict.ALMOST)
        assertThat(SentenceCheckRules.verdict("no", true, "pivotal", "x")).isEqualTo(SentenceVerdict.NOT_YET)
        assertThat(SentenceCheckRules.verdict("weird", true, "pivotal", "x")).isEqualTo(SentenceVerdict.NOT_YET)
    }

    @Test
    fun suggestedGradesFollowTheDesign() {
        assertThat(SentenceVerdict.NICELY_USED.suggested).isEqualTo(Rating.GOOD)
        assertThat(SentenceVerdict.ALMOST.suggested).isEqualTo(Rating.HARD)
        assertThat(SentenceVerdict.NOT_YET.suggested).isEqualTo(Rating.AGAIN)
    }
}
