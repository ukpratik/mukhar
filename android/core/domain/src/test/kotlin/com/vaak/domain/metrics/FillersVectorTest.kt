package com.vaak.domain.metrics

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.domain.TestVectors
import com.vaak.domain.model.Lang
import com.vaak.domain.model.Word
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FillersVectorTest(
    private val name: String,
    private val lang: Lang,
    private val words: List<Word>,
    private val expectedIndices: List<Int>,
    private val expectedCount: Int,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = TestVectors.load("fillers.json").getValue("cases").jsonArray.map {
            val o = it.jsonObject
            arrayOf(
                o.getValue("name").jsonPrimitive.content,
                Lang.fromCode(o.getValue("lang").jsonPrimitive.content),
                TestVectors.words(o.getValue("words")),
                o.getValue("filler_indices").jsonArray.map { i -> i.jsonPrimitive.int },
                o.getValue("count").jsonPrimitive.int,
            )
        }
    }

    @Test
    fun detectsAsSpecified() {
        val result = Fillers.detect(words, lang)
        assertWithMessage(name).that(result.mask.indices.filter { result.mask[it] }).isEqualTo(expectedIndices)
        assertWithMessage(name).that(result.count).isEqualTo(expectedCount)
    }
}

class FillerLexiconTest {
    private val json = TestVectors.load("fillers.json")

    @Test
    fun kotlinListsMatchSharedJson() {
        val lists = json.getValue("lists").jsonObject
        for (lang in Lang.entries) {
            val l = lists.getValue(lang.code).jsonObject
            assertWithMessage("${lang.code} unambiguous").that(FillerLexicon.unambiguous(lang))
                .containsExactlyElementsIn(TestVectors.strings(l.getValue("unambiguous")))
            assertWithMessage("${lang.code} ambiguous").that(FillerLexicon.ambiguous(lang))
                .containsExactlyElementsIn(TestVectors.strings(l.getValue("ambiguous")))
        }
        assertThat(FillerLexicon.NEVER).containsExactlyElementsIn(TestVectors.strings(lists.getValue("never")))
        assertThat(json.getValue("version").jsonPrimitive.int).isEqualTo(FillerLexicon.VERSION)
        assertThat(json.getValue("pause_gate_ms").jsonPrimitive.int.toLong()).isEqualTo(Fillers.PAUSE_GATE_MS)
    }

    @Test
    fun listsAreAlreadyNormalised() {
        for (lang in Lang.entries) {
            for (w in FillerLexicon.unambiguous(lang) + FillerLexicon.ambiguous(lang)) {
                assertWithMessage(w).that(com.vaak.domain.text.Normalizer.norm(w)).isEqualTo(w)
            }
        }
    }

    @Test
    fun kiIsNeverAFillerInEitherLanguage() {
        for (lang in Lang.entries) {
            for (ki in FillerLexicon.NEVER) {
                assertThat(FillerLexicon.unambiguous(lang)).doesNotContain(ki)
                assertThat(FillerLexicon.ambiguous(lang)).doesNotContain(ki)
                val words = listOf(Word("a", 0, 100, 0.9), Word(ki, 1000, 1100, 0.99), Word("b", 2000, 2100, 0.9))
                assertThat(Fillers.detect(words, lang).count).isEqualTo(0)
            }
        }
    }
}
