package com.vaak.domain.metrics

import com.vaak.domain.model.Word

object Pauses {
    const val LONG_PAUSE_MS = 1_500L

    /** Count of gaps strictly longer than 1.5 s between consecutive words. */
    fun countLong(words: List<Word>): Int =
        (1 until words.size).count { words[it].startMs - words[it - 1].endMs > LONG_PAUSE_MS }
}
