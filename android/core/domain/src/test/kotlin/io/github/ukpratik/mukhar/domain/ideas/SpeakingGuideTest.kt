package io.github.ukpratik.mukhar.domain.ideas

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Difficulty
import io.github.ukpratik.mukhar.domain.prompt.Prompt
import io.github.ukpratik.mukhar.domain.prompt.Track
import org.junit.Test

class SpeakingGuideTest {
    private val prep = listOf("Point: what you bring.", "Reason: why it matters.", "Example: proof.", "Point again: why you.")

    private fun prompt(lang: Lang = Lang.EN, d: Difficulty = Difficulty.MEDIUM, prep: List<String> = emptyList(), tip: String? = null, id: String = "en-001") =
        Prompt(id, lang, setOf(Track.PROFESSIONAL), d, "Tell us why you", null, prep, tip)

    @Test
    fun everyTrackHasItsStructureInBothLanguages() {
        val expected = mapOf(
            GuideTrack.INTERVIEWS to listOf("Situation", "Task", "Action", "Result"),
            GuideTrack.PROFESSIONAL to listOf("Point", "Reason", "Example", "Point again"),
            GuideTrack.PUBLIC_SPEAKING to listOf("Hook", "Point 1", "Point 2", "Point 3", "Close"),
            GuideTrack.CONTENT_CREATION to listOf("Hook (3 s)", "Story", "Takeaway", "Call to action"),
            GuideTrack.EVERYDAY to listOf("What happened", "How it felt", "What's next"),
            GuideTrack.WORD_POWER to listOf("Point", "Reason", "Example", "Point again"),
        )
        expected.forEach { (track, labels) ->
            val en = SpeakingGuideBuilder.build(prompt(), track, emptyList())
            assertThat(en.steps.map { it.label }).containsExactlyElementsIn(labels).inOrder()
            assertThat(en.opening).isNotEmpty()
            val hi = SpeakingGuideBuilder.build(prompt(Lang.HI, id = "hi-001"), track, emptyList())
            assertThat(hi.steps).hasSize(labels.size)
            // Hindi copy is in Devanagari.
            assertThat(hi.steps.all { s -> s.cue.any { it in 'ऀ'..'ॿ' } }).isTrue()
            assertThat(hi.opening.any { it in 'ऀ'..'ॿ' }).isTrue()
        }
        assertThat(SpeakingGuideBuilder.build(prompt(), GuideTrack.INTERVIEWS, emptyList()).structureName).isEqualTo("STAR")
        assertThat(SpeakingGuideBuilder.build(prompt(), GuideTrack.CONTENT_CREATION, emptyList()).structureLine)
            .isEqualTo("Hook (3 s) → Story → Takeaway → Call to action")
    }

    @Test
    fun prepLinesFillPrepStepsAndAreListedForOtherStructures() {
        val prof = SpeakingGuideBuilder.build(prompt(prep = prep), GuideTrack.PROFESSIONAL, emptyList())
        assertThat(prof.steps.map { it.forThisPrompt }).containsExactlyElementsIn(prep).inOrder()
        assertThat(prof.promptPoints).isEmpty()
        val star = SpeakingGuideBuilder.build(prompt(prep = prep), GuideTrack.INTERVIEWS, emptyList())
        assertThat(star.steps.all { it.forThisPrompt == null }).isTrue()
        assertThat(star.promptPoints).isEqualTo(prep)
    }

    @Test
    fun tipWordsAndPaceFollowThePrompt() {
        val g = SpeakingGuideBuilder.build(prompt(d = Difficulty.HARD, tip = "Try: what → why"), GuideTrack.WORD_POWER, listOf("pivotal", " pivotal", "concise", "", "candid", "fifth"))
        assertThat(g.tip).isEqualTo("Try: what → why")
        assertThat(g.wordsToTry).containsExactly("pivotal", "concise", "candid").inOrder()
        assertThat(g.pace).contains("counter-point")
        assertThat(SpeakingGuideBuilder.build(prompt(d = Difficulty.EASY), GuideTrack.EVERYDAY, emptyList()).pace).contains("one or two sentences")
        assertThat(SpeakingGuideBuilder.build(prompt(tip = " "), GuideTrack.EVERYDAY, emptyList()).tip).isNull()
    }

    @Test
    fun theOpeningIsStableForAPromptAndNeverAWholeTalk() {
        val a = SpeakingGuideBuilder.build(prompt(id = "en-042"), GuideTrack.INTERVIEWS, emptyList())
        val b = SpeakingGuideBuilder.build(prompt(id = "en-042"), GuideTrack.INTERVIEWS, emptyList())
        assertThat(a.opening).isEqualTo(b.opening)
        GuideTrack.entries.forEach { t ->
            Lang.entries.forEach { l -> assertThat(SpeakingGuideBuilder.build(prompt(l), t, emptyList()).opening.length).isLessThan(80) }
        }
    }

    @Test
    fun talkTracksMapToGuideTracks() {
        assertThat(GuideTrack.of(Track.PUBLIC_SPEAKING)).isEqualTo(GuideTrack.PUBLIC_SPEAKING)
        assertThat(GuideTrack.fromKey("content_creation")).isEqualTo(GuideTrack.CONTENT_CREATION)
        assertThat(GuideTrack.fromKey("nope")).isNull()
    }
}
