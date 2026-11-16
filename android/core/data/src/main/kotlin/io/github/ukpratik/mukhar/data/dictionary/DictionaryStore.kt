package io.github.ukpratik.mukhar.data.dictionary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.domain.dictionary.DictEntry
import io.github.ukpratik.mukhar.domain.dictionary.DictSense
import io.github.ukpratik.mukhar.domain.dictionary.LookupKey
import io.github.ukpratik.mukhar.domain.dictionary.MatchKind
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException

/** One line of `dict_<lang>.jsonl` (tools/dictionary/build_dictionary.py). A line with [of] is an inflected form. */
@Serializable
internal data class DictLineDto(
    val w: String,
    val t: String? = null,
    val ipa: String? = null,
    val r: Int = Int.MAX_VALUE,
    val s: List<DictSenseDto> = emptyList(),
    val hi: List<String> = emptyList(),
    val f: List<String> = emptyList(),
    val of: String? = null,
)

@Serializable
internal data class DictSenseDto(
    val p: String,
    val d: String,
    val x: String? = null,
    @SerialName("xe") val xe: String? = null,
    val syn: List<String> = emptyList(),
)

/** A dictionary entry found by a search, with how it matched (for ranking). */
data class DictHit(val entry: DictEntry, val kind: MatchKind)

/** The dictionary behind search (D-056); a seam so tests can use a small in-memory one. */
interface DictionarySource {
    /** Entries whose word, form or romanisation is exactly one of [keys] (built with [LookupKey]). */
    suspend fun exact(keys: List<String>): List<DictHit>

    /** Entries whose word (or Hindi romanisation) starts with [key], most common first. */
    suspend fun prefix(key: String, loose: Boolean, limit: Int): List<DictHit>
}

/**
 * The offline dictionary in `dictionary.db` (D-056), imported once from the bundled `dict_en.jsonl` and
 * `dict_hi.jsonl` (about 50,000 lines, a few seconds on a phone) and re-imported when [VERSION] changes. It's
 * derived data: not backed up (the backup rules list only `mukhar.db`) and safe to delete.
 *
 * Tables: `entry` (one row per word, the senses as JSON) and `term` (the keys a word is found by: its word, its
 * forms, and for Hindi its loose romanisation, prefixed with [LOOSE] so it never meets an English word).
 */
class DictionaryStore(
    private val context: Context,
    private val json: Json,
    private val open: (String) -> BufferedReader = { name -> context.assets.open(name).bufferedReader() },
) : DictionarySource {
    private val mutex = Mutex()
    private var db: SQLiteDatabase? = null

    private suspend fun db(): SQLiteDatabase = mutex.withLock {
        db ?: withContext(Dispatchers.IO) {
            val helper = object : SQLiteOpenHelper(context, NAME, null, VERSION) {
                override fun onCreate(db: SQLiteDatabase) = create(db)
                override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
                    db.execSQL("DROP TABLE IF EXISTS term")
                    db.execSQL("DROP TABLE IF EXISTS entry")
                    create(db)
                }
                override fun onDowngrade(db: SQLiteDatabase, old: Int, new: Int) = onUpgrade(db, old, new)
            }
            helper.writableDatabase.also { d ->
                if (count(d) == 0L) import(d)
                db = d
            }
        }
    }

    /** Opens (and on first use imports) the dictionary, so the first search is quick. */
    suspend fun warmUp() {
        db()
    }

    private fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entry (id INTEGER PRIMARY KEY, lang TEXT NOT NULL, word TEXT NOT NULL, rank INTEGER NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE INDEX entry_word ON entry (lang, word)")
        db.execSQL("CREATE TABLE term (key TEXT NOT NULL, entry INTEGER NOT NULL, kind INTEGER NOT NULL)")
    }

    private fun count(db: SQLiteDatabase): Long = db.rawQuery("SELECT COUNT(*) FROM entry", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    /** One transaction: a failed read (IOException) rolls everything back, and the next app start tries again. */
    private fun import(db: SQLiteDatabase) {
        val started = System.currentTimeMillis()
        try {
            db.transaction {
                execSQL("DROP INDEX IF EXISTS term_key")
                for (lang in Lang.entries) {
                    val ids = HashMap<String, Long>()
                    val forms = mutableListOf<Pair<String, String>>()
                    open(asset(lang)).use { reader ->
                        reader.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            val dto = try {
                                json.decodeFromString(DictLineDto.serializer(), line)
                            } catch (e: SerializationException) {
                                return@forEach
                            }
                            if (dto.of != null) {
                                forms += dto.w to dto.of
                                return@forEach
                            }
                            val id = insert("entry", null, ContentValues().apply {
                                put("lang", lang.code)
                                put("word", dto.w)
                                put("rank", dto.r)
                                put("data", line)
                            })
                            ids[dto.w] = id
                            term(this, LookupKey.exact(dto.w), id, MatchKind.EXACT)
                            dto.f.forEach { term(this, LookupKey.exact(it), id, MatchKind.FORM) }
                            dto.t?.let { term(this, LOOSE + LookupKey.loose(it), id, MatchKind.TRANSLIT) }
                        }
                    }
                    forms.forEach { (form, lemma) -> ids[lemma]?.let { term(this, LookupKey.exact(form), it, MatchKind.FORM) } }
                }
                execSQL("CREATE INDEX term_key ON term (key)")
            }
            MukharLog.i("dictionary: imported in ${System.currentTimeMillis() - started} ms")
        } catch (e: IOException) {
            MukharLog.i("dictionary: import failed")
        }
    }

    private fun term(db: SQLiteDatabase, key: String, entry: Long, kind: MatchKind) {
        if (key.isBlank()) return
        db.execSQL("INSERT INTO term (key, entry, kind) VALUES (?, ?, ?)", arrayOf<Any>(key, entry, kind.ordinal))
    }

    override suspend fun exact(keys: List<String>): List<DictHit> {
        val distinct = keys.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyList()
        val d = db()
        return withContext(Dispatchers.IO) {
            val marks = distinct.joinToString(",") { "?" }
            d.rawQuery(
                "SELECT e.lang, e.word, e.data, t.kind FROM term t JOIN entry e ON e.id = t.entry WHERE t.key IN ($marks)",
                distinct.toTypedArray(),
            ).use { c -> hits(c) }
        }
    }

    override suspend fun prefix(key: String, loose: Boolean, limit: Int): List<DictHit> {
        if (key.isBlank()) return emptyList()
        val k = if (loose) LOOSE + key else key
        val d = db()
        return withContext(Dispatchers.IO) {
            d.rawQuery(
                "SELECT e.lang, e.word, e.data, ${MatchKind.PREFIX.ordinal} FROM term t JOIN entry e ON e.id = t.entry " +
                    "WHERE t.key >= ? AND t.key < ? AND t.kind != ${MatchKind.FORM.ordinal} GROUP BY e.id ORDER BY e.rank LIMIT ?",
                arrayOf(k, k + '￿', limit.toString()),
            ).use { c -> hits(c) }
        }
    }

    private fun hits(c: Cursor): List<DictHit> {
        val out = mutableListOf<DictHit>()
        while (c.moveToNext()) {
            val lang = Lang.fromCode(c.getString(0))
            val entry = parse(lang, c.getString(2)) ?: continue
            out += DictHit(entry, MatchKind.entries[c.getInt(3)])
        }
        return out
    }

    private fun parse(lang: Lang, line: String): DictEntry? = try {
        json.decodeFromString(DictLineDto.serializer(), line).toEntry(lang)
    } catch (e: SerializationException) {
        null
    }

    companion object {
        const val NAME = "dictionary.db"

        /** Bump when the bundled dictionary files change, so the phone re-imports them. */
        const val VERSION = 1

        /** Prefix of a romanised-Hindi key in `term`, so it never meets an English word. */
        const val LOOSE = "~"

        fun asset(lang: Lang) = "dict_${lang.code}.jsonl"

        internal fun DictLineDto.toEntry(lang: Lang) = DictEntry(
            lang = lang, word = w, translit = t, ipa = ipa, rank = r,
            senses = s.map { DictSense(it.p, it.d, it.x, it.xe, it.syn) }, hindi = hi, forms = f,
        )
    }
}
