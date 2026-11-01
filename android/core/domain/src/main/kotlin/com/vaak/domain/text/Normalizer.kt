package com.vaak.domain.text

import java.text.Normalizer as JNormalizer
import java.util.Locale

/**
 * `norm(t)` from LLD §4.2, shared with the Worker (vectors in `shared/test-vectors/normalize.json`):
 * NFC → lowercase (Latin) → remove nukta U+093C, ZWNJ U+200C and ZWJ U+200D → candrabindu ँ → anusvara ं
 * → strip Unicode punctuation (incl. । and ॥) → collapse whitespace → trim.
 */
object Normalizer {
    private const val NUKTA = '़'
    private const val ZWNJ = '‌'
    private const val ZWJ = '‍'
    private const val CANDRABINDU = 'ँ'
    private const val ANUSVARA = 'ं'

    fun norm(text: String): String {
        val nfc = JNormalizer.normalize(text, JNormalizer.Form.NFC).lowercase(Locale.ROOT)
        val out = StringBuilder(nfc.length)
        var pendingSpace = false
        for (ch in nfc) {
            when {
                ch == NUKTA || ch == ZWNJ || ch == ZWJ -> Unit
                isPunctuation(ch) -> Unit
                ch.isWhitespace() -> pendingSpace = out.isNotEmpty()
                else -> {
                    if (pendingSpace) out.append(' ')
                    pendingSpace = false
                    out.append(if (ch == CANDRABINDU) ANUSVARA else ch)
                }
            }
        }
        return out.toString()
    }

    private fun isPunctuation(ch: Char): Boolean = when (Character.getType(ch).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION -> true
        else -> false
    }
}
