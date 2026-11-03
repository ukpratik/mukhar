package io.github.ukpratik.mukhar.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import io.github.ukpratik.mukhar.ui.theme.NotoDeva

/**
 * Mixed English + Hindi text (NFR-012, M6). A bundled font can't name a custom fallback below API 29, so a Devanagari
 * run inside Manrope text would otherwise fall back to whatever the system ships. [mixedScript] puts each Devanagari
 * run in Noto Sans Devanagari with `localeList = hi` (TalkBack switches to a Hindi voice) and leaves Latin alone.
 *
 * Runs never split a cluster: combining signs (nukta, matras, virama) are in the Devanagari block, and ZWJ/ZWNJ
 * stay with the run they sit in. Spaces between two Devanagari words stay inside one run.
 */
object MixedScript {
    val DevaSpan = SpanStyle(fontFamily = NotoDeva, localeList = LocaleList("hi"))

    fun isDevanagari(c: Char): Boolean =
        c in 'ऀ'..'ॿ' || c in '꣠'..'ꣿ' || c in '᳐'..'᳿'

    private fun isJoiner(c: Char) = c == '‌' || c == '‍'

    /** Half-open [start, end) ranges of the Devanagari runs in [text], in order. */
    fun devanagariRuns(text: String): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            if (!isDevanagari(text[i])) {
                i++
                continue
            }
            val start = i
            var end = i + 1
            while (end < text.length) {
                val c = text[end]
                if (isDevanagari(c) || isJoiner(c)) {
                    end++
                    continue
                }
                // Whitespace bridges two Devanagari words; trailing whitespace is left out.
                var j = end
                while (j < text.length && text[j].isWhitespace()) j++
                if (j > end && j < text.length && isDevanagari(text[j])) end = j else break
            }
            runs += start until end
            i = end
        }
        return runs
    }

    fun hasDevanagari(text: String): Boolean = text.any(::isDevanagari)
}

/** [text] with every Devanagari run styled by [deva] (Noto Sans Devanagari, locale hi). */
fun mixedScript(text: String, deva: SpanStyle = MixedScript.DevaSpan): AnnotatedString = buildAnnotatedString {
    append(text)
    for (r in MixedScript.devanagariRuns(text)) addStyle(deva, r.first, r.last + 1)
}
