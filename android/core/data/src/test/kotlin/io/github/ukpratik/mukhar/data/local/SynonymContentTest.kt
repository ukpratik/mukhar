package io.github.ukpratik.mukhar.data.local

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.text.Normalizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/** The bundled word-upgrade maps (ADR-008): sizes, shape, and every example actually uses its word. */
class SynonymContentTest {
    private val json = Json { ignoreUnknownKeys = false }
    private fun read(name: String) = File("src/main/assets/$name").readText()
    private val repo = SynonymRepository(::read, json)

    @Test
    fun englishMapHasAbout300HeadwordsWithTwoOrThreeAlternatives() = runBlocking<Unit> {
        val book = repo.book(Lang.EN)
        assertThat(book.size).isAtLeast(290)
        check(Lang.EN)
        for (w in listOf("good", "very", "really", "nice", "thing", "basically", "actually", "important", "problem")) {
            assertThat(book.lookup(w)).isNotNull()
        }
        assertThat(book.lookup("GOOD")!!.alternatives.size).isIn(2..3)
    }

    @Test
    fun hindiMapHasAbout150HeadwordsInDevanagari() = runBlocking<Unit> {
        val book = repo.book(Lang.HI)
        assertThat(book.size).isAtLeast(145)
        check(Lang.HI)
        // Nukta and no-nukta spellings find the same entry.
        assertThat(book.lookup("चीज़")).isNotNull()
        assertThat(book.lookup("चीज")).isEqualTo(book.lookup("चीज़"))
        assertThat(book.lookup("बहुत")).isNotNull()
    }

    @Test
    fun theLoaderRejectsTheWrongLanguageFile() {
        val r = runCatching { SynonymRepository.parse(json, Lang.HI, read("synonyms_en.json")) }
        assertThat(r.isFailure).isTrue()
    }

    private fun check(lang: Lang) {
        val file = json.decodeFromString(SynonymFileDto.serializer(), read(SynonymRepository.fileName(lang)))
        assertThat(file.version).isEqualTo(1)
        val heads = file.entries.map { Normalizer.norm(it.headword) }
        assertThat(heads.toSet()).hasSize(heads.size)
        for (e in file.entries) {
            assertThat(e.alternatives.size).isIn(2..3)
            for (a in e.alternatives) {
                assertThat(a.register).isIn(listOf("formal", "casual", "power"))
                assertThat(a.example).contains(a.word)
                assertThat(Normalizer.norm(a.word)).isNotEqualTo(Normalizer.norm(e.headword))
                assertThat(a.meaning).isNotEmpty()
            }
            if (lang == Lang.HI) assertThat(e.headword.any { it in 'ऀ'..'ॿ' }).isTrue()
        }
    }
}
