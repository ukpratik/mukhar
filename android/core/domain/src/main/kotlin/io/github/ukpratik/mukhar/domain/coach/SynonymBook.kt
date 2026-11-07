package io.github.ukpratik.mukhar.domain.coach

import io.github.ukpratik.mukhar.domain.feedback.Alternative
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.text.Normalizer

/** One curated headword (`synonyms_{en,hi}.json`) with its 2–3 alternatives. */
data class SynonymEntry(val headword: String, val alternatives: List<Alternative>)

/** The bundled word-upgrade map for one language, looked up by normalised headword. */
class SynonymBook(val lang: Lang, entries: List<SynonymEntry>) {
    private val byNorm: Map<String, SynonymEntry> = entries.associateBy { Normalizer.norm(it.headword) }

    val size: Int get() = byNorm.size

    fun lookup(word: String): SynonymEntry? = byNorm[Normalizer.norm(word)]

    companion object {
        fun empty(lang: Lang) = SynonymBook(lang, emptyList())
    }
}
