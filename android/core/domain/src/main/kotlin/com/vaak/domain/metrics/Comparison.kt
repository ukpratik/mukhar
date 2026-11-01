package com.vaak.domain.metrics

import com.vaak.domain.model.Lang
import java.time.Instant

/** A past talk's stored metrics, as needed for comparisons and insights. */
data class TalkSummary(
    val lang: Lang,
    val createdAt: Instant,
    val wpm: Double,
    val fillersPerMin: Double,
    val longPausesPerMin: Double?,
    val repetitions: Int?,
    val mattr: Double?,
    val lexicalTokens: Int,
)

data class MetricAverages(
    val talks: Int,
    val wpm: Double,
    val fillersPerMin: Double,
    val longPausesPerMin: Double?,
    val repetitions: Double?,
    val mattr: Double?,
)

/** "Compared with your previous 5-talk average", within the same language only (US-013). */
object Comparison {
    const val WINDOW = 5

    fun previousAverage(history: List<TalkSummary>, lang: Lang, before: Instant): MetricAverages? {
        val last = history.filter { it.lang == lang && it.createdAt < before }.sortedBy { it.createdAt }.takeLast(WINDOW)
        if (last.isEmpty()) return null
        fun avg(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()
        return MetricAverages(
            talks = last.size,
            wpm = last.map { it.wpm }.average(),
            fillersPerMin = last.map { it.fillersPerMin }.average(),
            longPausesPerMin = avg(last.mapNotNull { it.longPausesPerMin }),
            repetitions = avg(last.mapNotNull { it.repetitions?.toDouble() }),
            mattr = avg(last.mapNotNull { it.mattr }),
        )
    }
}
