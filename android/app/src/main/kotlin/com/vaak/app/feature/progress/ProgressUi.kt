package com.vaak.app.feature.progress

import com.vaak.data.repo.ProgressData
import com.vaak.data.repo.TalkRecord
import com.vaak.domain.billing.EntitlementRules
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.domain.billing.SubscriptionStatus
import com.vaak.domain.insight.InsightRules
import com.vaak.domain.metrics.WpmBand
import com.vaak.domain.model.Lang
import com.vaak.domain.progress.ProgressAggregator
import com.vaak.domain.progress.Totals
import com.vaak.domain.progress.TrendRange
import com.vaak.domain.progress.Trends
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The milestone behind an insight card (US-050 R1 rules). */
enum class InsightKind { FILLERS_DOWN, TALKS, FIRST_100_TOKENS, STREAK, WORDS }

data class InsightUi(
    val key: String,
    val kind: InsightKind,
    val count: Int = 0,
    val percent: Int = 0,
    val first: Double = 0.0,
    val last: Double = 0.0,
)

enum class CompareKind { PACE, FILLERS, PAUSES, VARIETY }

/** Plain-words deltas for "Then and now" (S16 state 4). [good] draws it in glow, otherwise muted (never red). */
sealed interface CompareDelta {
    val good: Boolean

    data object IntoBand : CompareDelta { override val good = true }
    data object StillInBand : CompareDelta { override val good = true }
    data class Percent(val percent: Int, val down: Boolean, override val good: Boolean) : CompareDelta
    data class Amount(val amount: Double, val down: Boolean, override val good: Boolean) : CompareDelta
    data object Same : CompareDelta { override val good = false }
}

data class CompareRowUi(val kind: CompareKind, val before: Double, val after: Double, val delta: CompareDelta)

data class CompareUi(
    val lang: Lang,
    val baselineDate: LocalDate,
    val latestDate: LocalDate,
    val rows: List<CompareRowUi>,
    /** Baseline audio, only if "Keep baseline audio" kept it; latest audio only if that talk was saved (US-051). */
    val baselineAudio: File?,
    val latestAudio: File?,
    val baselineDurationMs: Long,
    val latestDurationMs: Long,
)

/** The plan card at the bottom of Progress and the Plan row in Settings. */
data class PlanRowUi(val status: SubscriptionStatus, val expiry: LocalDate?)

data class ProgressUiState(
    val loading: Boolean = true,
    val totals: Totals = Totals(0, 0, 0),
    val showTrends: Boolean = false,
    val langs: List<Lang> = emptyList(),
    val lang: Lang = Lang.EN,
    val range: TrendRange = TrendRange.TWELVE,
    val trends: Trends? = null,
    val insight: InsightUi? = null,
    val compare: CompareUi? = null,
    val plan: PlanRowUi = PlanRowUi(SubscriptionStatus.FREE, null),
    val showBaselineCta: Boolean = false,
    val canShare: Boolean = false,
)

/** Pure S16 mapping (unit-tested). */
object ProgressMapper {
    fun map(
        data: ProgressData,
        lang: Lang?,
        range: TrendRange,
        entitlement: EntitlementSnapshot,
        now: Instant,
        zone: ZoneId,
        talksEnabled: Boolean,
    ): ProgressUiState {
        val talks = data.talks
        val langs = Lang.entries.filter { l -> talks.any { it.lang == l } }
        val chosen = lang?.takeIf { it in langs } ?: talks.lastOrNull()?.lang ?: Lang.EN
        val showTrends = talks.size >= ProgressAggregator.MIN_TALKS_FOR_TRENDS
        val compare = compare(data)
        return ProgressUiState(
            loading = false,
            totals = ProgressAggregator.totals(
                talkCount = data.records.count { it.kind != com.vaak.data.repo.TalkKind.BASELINE },
                speechMs = data.records.sumOf { it.speechMs },
                wordsLearned = data.wordsLearned,
            ),
            showTrends = showTrends,
            langs = langs,
            lang = chosen,
            range = range,
            trends = if (showTrends) {
                ProgressAggregator.trends(talks.map(TalkRecord::toPoint), chosen, range, data.today, data.practisedDays, data.weeklyGoal)
            } else {
                null
            },
            insight = insight(data),
            compare = compare,
            plan = PlanRowUi(
                EntitlementRules.status(entitlement, now),
                entitlement.expiryMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
            ),
            showBaselineCta = talksEnabled && data.baseline == null,
            canShare = compare != null && compare.rows.isNotEmpty(),
        )
    }

    /** The oldest unseen insight (at most one card at a time). */
    fun insight(data: ProgressData): InsightUi? {
        val key = data.insights.firstOrNull { !it.seen }?.ruleKey ?: return null
        return when {
            key.startsWith("fillers_down_25_") -> {
                val lang = Lang.entries.firstOrNull { key.endsWith("_" + it.code) } ?: return null
                val talks = data.talks.filter { it.lang == lang }
                if (talks.size < 2 * InsightRules.FILLER_GROUP) return null
                val first = talks.take(InsightRules.FILLER_GROUP).map { it.fillersPerMin }.average()
                val last = talks.takeLast(InsightRules.FILLER_GROUP).map { it.fillersPerMin }.average()
                val pct = if (first > 0) ((first - last) / first * 100).roundToInt() else 0
                InsightUi(key, InsightKind.FILLERS_DOWN, percent = pct, first = first, last = last)
            }
            key == InsightRules.FIRST_100_TOKENS -> InsightUi(key, InsightKind.FIRST_100_TOKENS)
            key.startsWith("talks_") -> InsightUi(key, InsightKind.TALKS, count = key.substringAfter("talks_").toIntOrNull() ?: return null)
            key.startsWith("streak_") -> InsightUi(key, InsightKind.STREAK, count = key.substringAfter("streak_").toIntOrNull() ?: return null)
            key.startsWith("words_") -> InsightUi(key, InsightKind.WORDS, count = key.substringAfter("words_").toIntOrNull() ?: return null)
            else -> null
        }
    }

    /** US-051: shown when a baseline and a later talk in the same language exist. */
    fun compare(data: ProgressData): CompareUi? {
        val b = data.baseline ?: return null
        val l = data.latestComparable ?: return null
        val rows = buildList {
            add(CompareRowUi(CompareKind.PACE, b.wpm, l.wpm, paceDelta(b.band, l.band, b.wpm, l.wpm)))
            add(CompareRowUi(CompareKind.FILLERS, b.fillersPerMin, l.fillersPerMin, lowerIsBetter(b.fillersPerMin, l.fillersPerMin)))
            // Stutter-friendly mode stores no pause rate: the row is left out, never shown as a gap.
            val bp = b.longPausesPerMin
            val lp = l.longPausesPerMin
            if (bp != null && lp != null) add(CompareRowUi(CompareKind.PAUSES, bp, lp, lowerIsBetter(bp, lp)))
            val bm = b.mattr
            val lm = l.mattr
            if (bm != null && lm != null) {
                val d = lm - bm
                val delta = if (kotlin.math.abs(d) < VARIETY_SAME) CompareDelta.Same else CompareDelta.Amount(kotlin.math.abs(d), d < 0, good = d > 0)
                add(CompareRowUi(CompareKind.VARIETY, bm, lm, delta))
            }
        }
        return CompareUi(b.lang, b.day, l.day, rows, b.audio, l.audio, b.durationMs, l.durationMs)
    }

    private fun paceDelta(before: WpmBand, after: WpmBand, b: Double, a: Double): CompareDelta = when {
        after == WpmBand.COMFORTABLE && before != WpmBand.COMFORTABLE -> CompareDelta.IntoBand
        after == WpmBand.COMFORTABLE -> CompareDelta.StillInBand
        kotlin.math.abs(a - b) < 1 -> CompareDelta.Same
        // Outside the band: closer to it is good.
        else -> CompareDelta.Amount(kotlin.math.abs(a - b), a < b, good = (after == WpmBand.FAST && a < b) || (after == WpmBand.SLOW && a > b))
    }

    private fun lowerIsBetter(before: Double, after: Double): CompareDelta {
        if (before <= 0.0) return if (after <= 0.0) CompareDelta.Same else CompareDelta.Amount(after, down = false, good = false)
        val pct = ((before - after) / before * 100).roundToInt()
        return when {
            kotlin.math.abs(pct) < SAME_PERCENT -> CompareDelta.Same
            pct > 0 -> CompareDelta.Percent(pct, down = true, good = true)
            else -> CompareDelta.Percent(-pct, down = false, good = false)
        }
    }

    private const val SAME_PERCENT = 5
    private const val VARIETY_SAME = 0.01
}
