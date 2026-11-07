package io.github.ukpratik.mukhar.data.local

import android.content.Context
import io.github.ukpratik.mukhar.domain.coach.SynonymBook
import io.github.ukpratik.mukhar.domain.coach.SynonymEntry
import io.github.ukpratik.mukhar.domain.feedback.Alternative
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The curated word-upgrade maps for the offline coach (a seam so tests can pass a book in). */
fun interface SynonymSource {
    suspend fun book(lang: Lang): SynonymBook
}

@Serializable
data class SynonymFileDto(val version: Int, val lang: String, val entries: List<SynonymEntryDto>)

@Serializable
data class SynonymEntryDto(val headword: String, val alternatives: List<SynonymAlternativeDto>)

@Serializable
data class SynonymAlternativeDto(val word: String, val meaning: String, val register: String, val example: String)

/** Loads `assets/synonyms_{en,hi}.json` once per language (ADR-008 "Word upgrades"). */
class SynonymRepository(private val read: (String) -> String, private val json: Json) : SynonymSource {
    constructor(context: Context, json: Json) : this({ name -> context.assets.open(name).use { it.readBytes().decodeToString() } }, json)

    private val mutex = Mutex()
    private val cache = mutableMapOf<Lang, SynonymBook>()

    override suspend fun book(lang: Lang): SynonymBook = mutex.withLock {
        cache[lang] ?: withContext(Dispatchers.IO) { parse(json, lang, read(fileName(lang))) }.also { cache[lang] = it }
    }

    companion object {
        fun fileName(lang: Lang) = "synonyms_${lang.code}.json"

        fun parse(json: Json, lang: Lang, text: String): SynonymBook {
            val file = json.decodeFromString(SynonymFileDto.serializer(), text)
            require(file.lang == lang.code) { "synonyms file is for ${file.lang}" }
            return SynonymBook(
                lang,
                file.entries.map { e ->
                    SynonymEntry(e.headword, e.alternatives.map { Alternative(it.word, it.meaning, Register.fromKey(it.register), it.example) })
                },
            )
        }
    }
}
