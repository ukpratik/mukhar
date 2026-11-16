package io.github.ukpratik.mukhar.data.dictionary

import io.github.ukpratik.mukhar.data.byok.AnthropicClient
import io.github.ukpratik.mukhar.data.byok.ByokError
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.ByokResult
import io.github.ukpratik.mukhar.data.byok.KeySource
import io.github.ukpratik.mukhar.data.byok.UsageMeter
import io.github.ukpratik.mukhar.data.byok.WordExplanation
import io.github.ukpratik.mukhar.data.content.CuratedWord
import io.github.ukpratik.mukhar.data.content.WordCatalog
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.data.local.SynonymSource
import io.github.ukpratik.mukhar.data.repo.LookupWord
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.domain.dictionary.DictEntry
import io.github.ukpratik.mukhar.domain.dictionary.LookupKey
import io.github.ukpratik.mukhar.domain.dictionary.LookupRanking
import io.github.ukpratik.mukhar.domain.dictionary.MatchKind
import io.github.ukpratik.mukhar.domain.feedback.Alternative
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** One row in the search results (D-056). [other] is the word in the other language (Hindi words for English, English for Hindi). */
data class LookupHit(
    val id: String,
    val lang: Lang,
    val word: String,
    val translit: String?,
    val pos: String?,
    val meaning: String,
    val other: String?,
    /** One of Mukhar's own hand-picked words (it has meanings in both languages and an example). */
    val mukhar: Boolean,
)

/** Everything the word page shows: Mukhar's own card when the word has one, the dictionary's meanings, and stronger words. */
data class LookupEntry(
    val id: String,
    val lang: Lang,
    val word: String,
    val translit: String?,
    val ipa: String?,
    val curated: CuratedWord?,
    val dict: DictEntry?,
    val better: List<Alternative>,
) {
    /** A word only the AI can explain (not in the dictionary or Mukhar's words). */
    val unknown: Boolean get() = curated == null && dict == null

    /**
     * What "Add to my words" saves: Mukhar's own card, else the dictionary's first meaning, else the AI's. Like the
     * curated packs, the meaning is in the word's language when known and the gloss in the other one.
     */
    fun toLookupWord(ai: WordExplanation? = null): LookupWord? {
        val first = dict?.senses?.firstOrNull()
        val aiMeaning = ai?.takeIf { it.isWord }?.let { if (lang == Lang.HI) it.meaningHi else it.meaningEn }
        val aiGloss = ai?.takeIf { it.isWord }?.let { if (lang == Lang.HI) it.meaningEn else it.meaningHi }
        val meaning = curated?.meaning ?: first?.definition ?: aiMeaning ?: return null
        return LookupWord(
            id = id, lang = lang, lemma = word, meaning = meaning,
            example = curated?.example ?: dict?.senses?.firstNotNullOfOrNull { it.example } ?: ai?.examples?.firstOrNull()?.sentence.orEmpty(),
            gloss = curated?.gloss ?: dict?.hindi?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: aiGloss,
            pos = curated?.pos ?: first?.pos ?: ai?.pos?.takeIf { it.isNotBlank() },
            translit = curated?.translit ?: translit,
        )
    }

    companion object {
        /** A typed word neither source knows, so the page can still offer "Explain with AI". */
        fun unknown(lang: Lang, word: String) = LookupEntry(
            DictionaryRepository.idFor(lang, word), lang, word.trim(), null, null, null, null, emptyList(),
        )
    }
}

sealed interface ExplainResult {
    data class Ok(val explanation: WordExplanation) : ExplainResult
    data object NoKey : ExplainResult
    data class Failed(val reason: IdeasFailure) : ExplainResult
}

/** AI explanations cached per word (`files/lookup/`), so a word is paid for once. A seam for tests. */
interface ExplainCache {
    suspend fun get(id: String): WordExplanation?
    suspend fun put(id: String, explanation: WordExplanation)
}

/** Not backed up and wiped by "Delete all my data" (the folder is in the wiper's list). */
class FileExplainCache(private val dir: File, private val json: Json) : ExplainCache {
    override suspend fun get(id: String): WordExplanation? = withContext(Dispatchers.IO) {
        val f = file(id)
        if (!f.exists()) return@withContext null
        try {
            json.decodeFromString(WordExplanation.serializer(), f.readText())
        } catch (e: SerializationException) {
            f.delete()
            null
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun put(id: String, explanation: WordExplanation) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            file(id).writeText(json.encodeToString(WordExplanation.serializer(), explanation))
        } catch (e: IOException) {
            MukharLog.i("lookup: cache write failed")
        }
    }

    private fun file(id: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    companion object {
        const val DIR = "lookup"
    }
}

/**
 * Word search (D-056): the offline dictionary ([DictionarySource]: WordNet for English, Wiktionary for Hindi) plus
 * Mukhar's own curated words, which rank first and carry both meanings. English, Hindi in Devanagari and romanised
 * Hindi ("khush") all work; English endings are tried too ("running" finds run). "Explain with AI" is one call on the
 * user's own Anthropic key, cached per word, under the same "pause my keys" cap as the other AI features.
 */
class DictionaryRepository(
    private val dictionary: DictionarySource,
    private val catalog: WordCatalog,
    private val synonyms: SynonymSource,
    private val keys: KeySource,
    private val client: AnthropicClient,
    private val cache: ExplainCache,
    private val usage: UsageMeter,
) {
    suspend fun search(query: String, limit: Int = LIMIT): List<LookupHit> {
        val q = query.trim().take(MAX_QUERY)
        if (q.isEmpty()) return emptyList()
        val deva = LookupKey.isDevanagari(q)
        val key = LookupKey.exact(q)
        val loose = if (deva) "" else LookupKey.loose(q)
        val found = LinkedHashMap<String, Pair<DictEntry?, CuratedWord?>>()
        val candidates = mutableListOf<LookupRanking.Candidate>()

        fun add(id: String, entry: DictEntry?, curated: CuratedWord?, kind: MatchKind, rank: Int, length: Int) {
            val (e, c) = found[id] ?: (null to null)
            found[id] = (e ?: entry) to (c ?: curated)
            candidates += LookupRanking.Candidate(id, kind, rank, curated != null || c != null, length)
        }

        // Mukhar's own words: exact, then the start of the word (or of its romanisation).
        for (w in catalog.all()) {
            val lemma = LookupKey.exact(w.lemma)
            val translit = w.translit?.let(LookupKey::loose)
            val kind = when {
                lemma == key -> MatchKind.EXACT
                !deva && translit != null && translit == loose -> MatchKind.TRANSLIT
                key.length >= 2 && lemma.startsWith(key) -> MatchKind.PREFIX
                !deva && translit != null && loose.length >= 2 && translit.startsWith(loose) -> MatchKind.PREFIX
                else -> null
            } ?: continue
            add(w.id, null, w, kind, rank = 0, length = w.lemma.length)
        }

        val stems = if (deva) emptyList() else LookupKey.englishStems(q)
        val exactKeys = listOf(key) + stems + listOfNotNull(loose.takeIf { it.isNotEmpty() }?.let { DictionaryStore.LOOSE + it })
        for (hit in dictionary.exact(exactKeys)) {
            val e = hit.entry
            val viaStem = LookupKey.exact(e.word) != key && hit.kind == MatchKind.EXACT
            add(e.id, e, catalog.byId(e.id), if (viaStem) MatchKind.STEM else hit.kind, e.rank, e.word.length)
        }
        if (key.length >= 2) {
            dictionary.prefix(key, loose = false, limit = PREFIX_LIMIT).forEach { add(it.entry.id, it.entry, catalog.byId(it.entry.id), MatchKind.PREFIX, it.entry.rank, it.entry.word.length) }
        }
        if (loose.length >= 3) {
            dictionary.prefix(loose, loose = true, limit = PREFIX_LIMIT / 2).forEach { add(it.entry.id, it.entry, catalog.byId(it.entry.id), MatchKind.PREFIX, it.entry.rank, it.entry.word.length) }
        }
        return LookupRanking.order(candidates, limit).mapNotNull { id -> found[id]?.let { (e, c) -> hit(id, e, c) } }
    }

    /** The word page for [id] (`en:happy`, `hi:ख़ुश` or its normalised form), or null when neither source has it. */
    suspend fun entry(id: String): LookupEntry? {
        val lang = Lang.entries.firstOrNull { id.startsWith("${it.code}:") } ?: return null
        val key = id.removePrefix("${lang.code}:")
        val dict = dictionary.exact(listOf(key)).firstOrNull { it.entry.lang == lang && it.kind == MatchKind.EXACT }?.entry
        val curated = catalog.byId(id) ?: catalog.byId(dict?.id ?: id)
        if (dict == null && curated == null) return null
        val word = curated?.lemma ?: dict!!.word
        val better = synonyms.book(lang).lookup(word)?.alternatives.orEmpty()
        return LookupEntry(
            id = curated?.id ?: dict!!.id, lang = lang, word = word, translit = curated?.translit ?: dict?.translit, ipa = dict?.ipa,
            curated = curated, dict = dict, better = better,
        )
    }

    /** A short row for a recent look-up. */
    suspend fun hitFor(id: String): LookupHit? = entry(id)?.let { hit(it.id, it.dict, it.curated) }

    /** Opens the dictionary (the first time, imports it), so the first search is quick. */
    suspend fun warmUp() {
        (dictionary as? DictionaryStore)?.warmUp()
    }

    suspend fun hasKey(): Boolean = keys.key(ByokProvider.ANTHROPIC) != null

    suspend fun cachedExplanation(id: String): WordExplanation? = cache.get(id)

    suspend fun explain(entry: LookupEntry): ExplainResult {
        cache.get(entry.id)?.let { return ExplainResult.Ok(it) }
        val key = keys.key(ByokProvider.ANTHROPIC) ?: return ExplainResult.NoKey
        if (usage.overSoftCap()) return ExplainResult.Failed(IdeasFailure.PAUSED)
        return when (val r = client.explainWord(key, entry.lang, entry.word)) {
            is ByokResult.Ok -> {
                usage.record(inputTokens = r.value.usage.inputTokens, outputTokens = r.value.usage.outputTokens)
                cache.put(entry.id, r.value.explanation)
                ExplainResult.Ok(r.value.explanation)
            }
            is ByokResult.Fail -> {
                MukharLog.i("lookup: anthropic failed (${r.error::class.simpleName})")
                ExplainResult.Failed(r.error.toFailure())
            }
        }
    }

    private fun hit(id: String, e: DictEntry?, c: CuratedWord?): LookupHit {
        val lang = c?.lang ?: e!!.lang
        val first = e?.senses?.firstOrNull()
        return LookupHit(
            id = id, lang = lang, word = c?.lemma ?: e!!.word, translit = c?.translit ?: e?.translit, pos = c?.pos ?: first?.pos,
            meaning = c?.meaning ?: first?.definition.orEmpty(),
            other = c?.gloss ?: e?.hindi?.takeIf { it.isNotEmpty() }?.take(OTHER_WORDS)?.joinToString(", "),
            mukhar = c != null,
        )
    }

    private fun ByokError.toFailure(): IdeasFailure = when (this) {
        ByokError.Offline -> IdeasFailure.OFFLINE
        ByokError.Unauthorized, ByokError.Forbidden, ByokError.ModelUnavailable -> IdeasFailure.KEY_REJECTED
        ByokError.Refused -> IdeasFailure.REFUSED
        is ByokError.RateLimited, is ByokError.Server -> IdeasFailure.BUSY
        ByokError.Truncated, is ByokError.BadResponse -> IdeasFailure.BAD_RESPONSE
    }

    companion object {
        const val LIMIT = 30
        private const val PREFIX_LIMIT = 30
        private const val MAX_QUERY = 60
        private const val OTHER_WORDS = 3

        /** The id a typed word would have, for "Explain with AI" on a word the dictionary doesn't know. */
        fun idFor(lang: Lang, word: String) = "${lang.code}:${Normalizer.norm(word)}"
    }
}
