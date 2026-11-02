package com.vaak.data.content

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.data.prefs.GoalTrack
import com.vaak.domain.model.Lang
import com.vaak.domain.text.Normalizer
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/**
 * The curated word packs (US-020, D-011): ≥ 500 words per language, valid schema, unique ids that match the
 * talk-harvest id (`lang:norm(lemma)`, so a harvested word dedupes against a curated one), every track covered.
 * Accuracy and tone are a human check (Q-011).
 */
class WordContentTest {
    private val packs = Lang.entries.associateWith { lang ->
        WordCatalog.parse(Json, File("src/main/assets/${WordCatalog.asset(lang)}").readText())
    }

    @Test
    fun atLeastFiveHundredPerLanguageWithUniqueIds() {
        for ((lang, words) in packs) {
            assertWithMessage("$lang count").that(words.size).isAtLeast(500)
            assertWithMessage("$lang ids").that(words.map { it.id }.toSet()).hasSize(words.size)
            assertWithMessage("$lang lemmas").that(words.map { Normalizer.norm(it.lemma) }.toSet()).hasSize(words.size)
            words.forEach { assertThat(it.id).isEqualTo("${lang.code}:${Normalizer.norm(it.lemma)}") }
        }
    }

    @Test
    fun everyFieldIsFilledAndFromItsVocabulary() {
        val tracks = GoalTrack.entries.map { it.key }.toSet()
        for ((lang, words) in packs) for (w in words) {
            assertWithMessage(w.id).that(w.pos).isIn(setOf("noun", "verb", "adjective", "adverb", "phrase"))
            assertWithMessage(w.id).that(w.register).isIn(setOf("formal", "casual", "power"))
            assertWithMessage(w.id).that(w.difficulty).isIn(setOf("easy", "medium", "hard"))
            assertWithMessage(w.id).that(w.tracks).isNotEmpty()
            assertWithMessage(w.id).that(tracks.containsAll(w.tracks)).isTrue()
            listOf(w.lemma, w.meaning, w.gloss, w.example).forEach { assertWithMessage(w.id).that(it.isNotBlank()).isTrue() }
            assertWithMessage(w.id).that(Normalizer.norm(w.meaning)).isNotEqualTo(Normalizer.norm(w.lemma))
            assertWithMessage(w.id).that(w.lang).isEqualTo(lang)
        }
    }

    @Test
    fun everyTrackAndRegisterIsCovered() {
        for ((lang, words) in packs) {
            for (track in GoalTrack.entries) {
                assertWithMessage("$lang ${track.key}").that(words.count { track.key in it.tracks }).isAtLeast(40)
            }
            assertThat(words.map { it.register }.toSet()).containsExactly("formal", "casual", "power")
            assertThat(words.map { it.difficulty }.toSet()).containsExactly("easy", "medium", "hard")
        }
    }

    @Test
    fun examplesUseTheWord() {
        for ((lang, words) in packs) for (w in words) {
            var head = Normalizer.norm(w.lemma).split(' ').first()
            // Hindi verbs are listed in the -ना form; examples conjugate the stem (ढलना → ढल जाता).
            if (lang == Lang.HI && head.endsWith("ना") && head.length > 2) head = head.dropLast(2)
            val prefix = head.take(if (lang == Lang.HI) 3 else 4)
            assertWithMessage("${w.id}: ${w.example}").that(Normalizer.norm(w.example)).contains(prefix)
        }
    }

    @Test
    fun englishGlossIsHindiAndHindiWordsAreDevanagariWithTransliteration() {
        fun deva(s: String) = s.any { it in 'ऀ'..'ॿ' }
        packs.getValue(Lang.EN).forEach { assertWithMessage(it.id).that(deva(it.gloss)).isTrue() }
        packs.getValue(Lang.HI).forEach { w ->
            assertWithMessage(w.id).that(deva(w.lemma) && deva(w.meaning) && deva(w.example)).isTrue()
            assertWithMessage(w.id).that(w.translit.orEmpty().matches(Regex("[a-z][a-z .'-]*"))).isTrue()
            assertWithMessage(w.id).that(w.exampleGloss).isNotEmpty()
            assertWithMessage(w.id).that(deva(w.gloss)).isFalse()
        }
    }
}
