package com.vaak.data.content

import com.google.common.truth.Truth.assertThat
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Track
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/** The bundled prompt pack (US-010): ≥ 150 per language (M4), tagged, Hindi in Devanagari with a transliteration. */
class PromptContentTest {
    private val text = File("src/main/assets/prompts.json").readText()
    private val prompts = PromptRepository.parse(Json, text)

    @Test
    fun oneHundredFiftyPerLanguageWithUniqueIds() {
        assertThat(prompts.count { it.lang == Lang.EN }).isAtLeast(150)
        assertThat(prompts.count { it.lang == Lang.HI }).isAtLeast(150)
        // The M2 prompts keep their ids (prompt_history and talk_session.prompt_id point at them).
        (1..30).forEach { n -> assertThat(prompts.map { it.id }).containsAtLeast("en-%03d".format(n), "hi-%03d".format(n)) }
        assertThat(prompts.map { it.id }.toSet()).hasSize(prompts.size)
    }

    @Test
    fun everyDefaultTrackHasPrompts() {
        for (lang in Lang.entries) for (track in Track.entries) {
            assertThat(prompts.count { it.lang == lang && track in it.tracks }).isAtLeast(30)
        }
    }

    @Test
    fun hindiIsDevanagariWithTransliteration() {
        prompts.filter { it.lang == Lang.HI }.forEach { p ->
            assertThat(p.text.any { it in 'ऀ'..'ॿ' }).isTrue()
            assertThat(p.translit).isNotEmpty()
        }
    }

    @Test
    fun prepHintsHaveFourSteps() {
        val prep = prompts.filter { it.prep.isNotEmpty() }
        assertThat(prep.size).isAtLeast(10)
        prep.forEach { assertThat(it.prep).hasSize(4) }
    }
}
