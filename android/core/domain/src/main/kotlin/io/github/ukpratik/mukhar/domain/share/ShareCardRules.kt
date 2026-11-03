package io.github.ukpratik.mukhar.domain.share

import io.github.ukpratik.mukhar.domain.metrics.Wpm
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The numbers of one talk the share card may use. No text from the talk ever reaches the card (US-071). */
data class CardMetrics(val wpm: Double, val fillersPerMin: Double, val mattr: Double?)

enum class ShareMetricKind { FILLERS, PACE, VARIETY }

data class ShareMetric(val kind: ShareMetricKind, val before: Double, val after: Double)

/**
 * S21 rules (US-071): baseline vs. latest, same language. Only improved or steady metrics are listed (at most 3, in
 * a fixed order); a metric that got worse is left off rather than shown in red.
 */
object ShareCardRules {
    const val MAX_ROWS = 3
    private const val FILLER_STEADY = 0.10
    private const val VARIETY_STEADY = 0.02

    fun metrics(baseline: CardMetrics, latest: CardMetrics, lang: Lang): List<ShareMetric> {
        val rows = mutableListOf<ShareMetric>()
        if (latest.fillersPerMin <= baseline.fillersPerMin * (1 + FILLER_STEADY) + 1e-9) {
            rows += ShareMetric(ShareMetricKind.FILLERS, baseline.fillersPerMin, latest.fillersPerMin)
        }
        if (paceOk(baseline.wpm, latest.wpm, lang)) rows += ShareMetric(ShareMetricKind.PACE, baseline.wpm, latest.wpm)
        val before = baseline.mattr
        val after = latest.mattr
        if (before != null && after != null && after >= before - VARIETY_STEADY) {
            rows += ShareMetric(ShareMetricKind.VARIETY, before, after)
        }
        return rows.take(MAX_ROWS)
    }

    /** Pace counts when the latest talk is in the comfortable band, or closer to it than the baseline was. */
    fun paceOk(before: Double, after: Double, lang: Lang): Boolean {
        if (Wpm.band(after, lang) == WpmBand.COMFORTABLE) return true
        val (low, high) = if (lang == Lang.HI) 100.0 to 150.0 else 110.0 to 160.0
        fun distance(w: Double) = if (w < low) low - w else if (w > high) w - high else 0.0
        return distance(after) <= distance(before)
    }

    /** "4 weeks of speaking practice": whole weeks from baseline to latest, at least 1. */
    fun weeks(baseline: LocalDate, latest: LocalDate): Int =
        ((ChronoUnit.DAYS.between(baseline, latest).coerceAtLeast(0) + 6) / 7).toInt().coerceAtLeast(1)
}
