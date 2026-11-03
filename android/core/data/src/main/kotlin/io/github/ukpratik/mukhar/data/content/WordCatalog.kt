package io.github.ukpratik.mukhar.data.content

import android.content.Context
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class WordFileDto(val version: Int, val lang: String, val words: List<CuratedWordDto>)

@Serializable
internal data class CuratedWordDto(
    val id: String,
    val lemma: String,
    val translit: String? = null,
    val pos: String,
    val meaning: String,
    val gloss: String,
    val example: String,
    @SerialName("example_gloss") val exampleGloss: String? = null,
    val tracks: List<String>,
    val register: String,
    val difficulty: String,
)

/**
 * One curated word (`assets/words_<lang>.json`, US-020). English: [meaning] in simple English, [gloss] in Hindi.
 * Hindi: [meaning] in simple Hindi, [gloss] in English, plus [translit] and [exampleGloss].
 * [tracks] are goal-track keys (S02); [pos] is noun, verb, adjective, adverb or phrase.
 */
data class CuratedWord(
    val id: String,
    val lang: Lang,
    val lemma: String,
    val translit: String?,
    val pos: String,
    val meaning: String,
    val gloss: String,
    val example: String,
    val exampleGloss: String?,
    val tracks: List<String>,
    val register: String,
    val difficulty: String,
)

/** The bundled word packs, parsed once. Used for daily picks and to enrich word rows (part of speech, gloss). */
class WordCatalog(private val load: suspend (Lang) -> String, private val json: Json) {
    constructor(context: Context, json: Json) : this(
        load = { lang -> withContext(Dispatchers.IO) { context.assets.open(asset(lang)).bufferedReader().use { it.readText() } } },
        json = json,
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<Lang, List<CuratedWord>>()
    private var byId: Map<String, CuratedWord> = emptyMap()

    suspend fun words(lang: Lang): List<CuratedWord> = mutex.withLock {
        cache.getOrPut(lang) { parse(json, load(lang)).also { list -> byId = byId + list.associateBy { it.id } } }
    }

    suspend fun all(): List<CuratedWord> = Lang.entries.flatMap { words(it) }

    suspend fun byId(id: String): CuratedWord? {
        val lang = Lang.entries.firstOrNull { id.startsWith("${it.code}:") } ?: return null
        words(lang)
        return byId[id]
    }

    companion object {
        fun asset(lang: Lang) = "words_${lang.code}.json"

        fun parse(json: Json, text: String): List<CuratedWord> {
            val file = json.decodeFromString<WordFileDto>(text)
            val lang = Lang.fromCode(file.lang)
            return file.words.map { w ->
                CuratedWord(
                    id = w.id, lang = lang, lemma = w.lemma, translit = w.translit, pos = w.pos, meaning = w.meaning,
                    gloss = w.gloss, example = w.example, exampleGloss = w.exampleGloss, tracks = w.tracks,
                    register = w.register, difficulty = w.difficulty,
                )
            }
        }
    }
}
