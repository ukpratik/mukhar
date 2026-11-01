package com.vaak.domain.metrics

import com.vaak.domain.model.Word

/** Silence before/after each word in ms (LLD §4.2). The first word has no gap before it and the last none after it. */
internal object Gaps {
    const val NONE: Long = Long.MAX_VALUE

    fun before(words: List<Word>, i: Int): Long = if (i <= 0) NONE else words[i].startMs - words[i - 1].endMs

    fun after(words: List<Word>, i: Int): Long = if (i >= words.lastIndex) NONE else words[i + 1].startMs - words[i].endMs
}
