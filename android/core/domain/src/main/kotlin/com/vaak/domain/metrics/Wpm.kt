package com.vaak.domain.metrics

import com.vaak.domain.model.Lang
import com.vaak.domain.model.Word

enum class WpmBand(val key: String) { SLOW("slow"), COMFORTABLE("comfortable"), FAST("fast") }

object Wpm {
    /** Think-breaks longer than this are capped when measuring speaking time. */
    const val THINK_BREAK_CAP_MS = 5_000L

    /** `spanMs = last.e − first.s − Σ max(0, gap − 5000)` (LLD §4.2). */
    fun spanMs(words: List<Word>): Long {
        if (words.isEmpty()) return 0
        var excess = 0L
        for (i in 1 until words.size) {
            val gap = words[i].startMs - words[i - 1].endMs
            if (gap > THINK_BREAK_CAP_MS) excess += gap - THINK_BREAK_CAP_MS
        }
        return (words.last().endMs - words.first().startMs - excess).coerceAtLeast(0)
    }

    fun perMinute(count: Int, spanMs: Long): Double = if (spanMs <= 0) 0.0 else count / (spanMs / 60_000.0)

    /** EN: < 110 slow, 110–160 comfortable, > 160 fast. HI (provisional): < 100, 100–150, > 150. */
    fun band(wpm: Double, lang: Lang): WpmBand {
        val (low, high) = when (lang) {
            Lang.EN -> 110.0 to 160.0
            Lang.HI -> 100.0 to 150.0
        }
        return when {
            wpm < low -> WpmBand.SLOW
            wpm > high -> WpmBand.FAST
            else -> WpmBand.COMFORTABLE
        }
    }
}
