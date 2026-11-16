package io.github.ukpratik.mukhar.domain.dictionary

import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.text.Normalizer
import java.text.Normalizer as JNormalizer

/** One meaning of a dictionary word (D-056). [exampleGloss] is the English of a Hindi example. */
data class DictSense(
    val pos: String,
    val definition: String,
    val example: String? = null,
    val exampleGloss: String? = null,
    val synonyms: List<String> = emptyList(),
)

/**
 * A word in the offline dictionary (D-056): English from Open English WordNet, Hindi from Wiktionary.
 * [hindi] are Hindi words for an English entry; [rank] is its frequency rank (1 = most common).
 */
data class DictEntry(
    val lang: Lang,
    val word: String,
    val translit: String?,
    val ipa: String?,
    val rank: Int,
    val senses: List<DictSense>,
    val hindi: List<String> = emptyList(),
    val forms: List<String> = emptyList(),
) {
    val id: String get() = "${lang.code}:${Normalizer.norm(word)}"
}

/** Search keys: what a typed query and a dictionary word are compared by. */
object LookupKey {
    fun isDevanagari(text: String): Boolean = text.any { it in 'ऀ'..'ॿ' }

    /** Exact key: [Normalizer.norm] (lowercase, NFC, no nukta, candrabindu → anusvara, no punctuation). */
    fun exact(text: String): String = Normalizer.norm(text)

    /**
     * A loose key for romanised Hindi, so "khush", "xuś" and "khoosh" meet: no accents, aspirates folded
     * (kh → k, sh → s…), long vowels folded (aa → a, ee → i, oo → u), and a few letters merged (w → v, z → j, f → p, q → k,
     * x → k). Only used between romanisations, never for English words.
     */
    fun loose(text: String): String {
        val plain = JNormalizer.normalize(exact(text), JNormalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        var s = plain.filter { it.isLetter() }
        for ((from, to) in DIGRAPHS) s = s.replace(from, to)
        s = s.map { MERGE[it] ?: it }.joinToString("")
        // Doubled letters (aa, ee → i via the vowel map, kk) count once.
        val out = StringBuilder()
        for (c in s) if (out.isEmpty() || out.last() != c) out.append(c)
        return out.toString()
    }

    private val DIGRAPHS = listOf(
        "ee" to "i", "oo" to "u", "ou" to "au",
        "chh" to "c", "kh" to "k", "gh" to "g", "ch" to "c", "jh" to "j", "th" to "t", "dh" to "d", "ph" to "p", "bh" to "b", "sh" to "s",
    )
    private val MERGE = mapOf('w' to 'v', 'z' to 'j', 'f' to 'p', 'q' to 'k', 'x' to 'k', 'y' to 'y')

    /**
     * Base forms an English word may come from, most likely first (running → run, studies → study, made has its own
     * dictionary form). Only regular endings; irregular forms come from the dictionary's own form list.
     */
    fun englishStems(word: String): List<String> {
        val w = exact(word)
        if (w.length < 4 || isDevanagari(w)) return emptyList()
        val out = LinkedHashSet<String>()
        fun undouble(s: String) {
            if (s.length >= 3 && s[s.length - 1] == s[s.length - 2] && s.last() !in "aeiouls") out += s.dropLast(1)
        }
        fun stem(suffix: String) {
            if (!w.endsWith(suffix) || w.length - suffix.length < 2) return
            val base = w.dropLast(suffix.length)
            undouble(base)
            out += base
            out += base + "e"
        }
        if (w.endsWith("ies") || w.endsWith("ied")) out += w.dropLast(3) + "y"
        if (w.endsWith("ier")) out += w.dropLast(3) + "y"
        if (w.endsWith("iest")) out += w.dropLast(4) + "y"
        if (w.endsWith("ily")) out += w.dropLast(3) + "y"
        stem("ing")
        stem("ed")
        if (w.endsWith("es")) out += w.dropLast(2)
        if (w.endsWith("s") && !w.endsWith("ss")) out += w.dropLast(1)
        stem("est")
        stem("er")
        if (w.endsWith("ly")) out += w.dropLast(2)
        if (w.endsWith("ness")) out += w.dropLast(4)
        return out.filter { it.length >= 2 && it != w }
    }
}

/**
 * Where a search hit came from: the word itself, one of its forms, its romanisation, an English ending removed, or
 * the start of a word. [tier] orders them: a romanisation match ranks with prefixes, so typing "happ" doesn't put a
 * rare Hindi word spelt "hap" above happy.
 */
enum class MatchKind(val tier: Int) { EXACT(0), FORM(1), TRANSLIT(2), STEM(1), PREFIX(2) }

/** Orders search hits: exact matches first, then forms and stems, then romanisations and prefixes; within each, Mukhar's own words, then common words. */
object LookupRanking {
    data class Candidate(val id: String, val kind: MatchKind, val rank: Int, val curated: Boolean, val length: Int)

    fun order(candidates: List<Candidate>, limit: Int): List<String> = candidates
        .groupBy { it.id }
        .map { (_, cs) -> cs.minWith(COMPARATOR) }
        .sortedWith(COMPARATOR)
        .take(limit)
        .map { it.id }

    private val COMPARATOR = compareBy<Candidate> { it.kind.tier }.thenBy { !it.curated }.thenBy { it.rank }.thenBy { it.length }
}
