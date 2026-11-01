package com.vaak.domain.text

/** Splits free text into normalised tokens (whitespace-separated, punctuation-only tokens dropped). */
object Tokenizer {
    private val WHITESPACE = Regex("\\s+")

    fun tokenize(text: String): List<String> =
        text.split(WHITESPACE).map(Normalizer::norm).filter { it.isNotEmpty() }
}
