package io.github.ukpratik.mukhar.domain.progress

import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** One analysed talk as Progress needs it (S16). [day] is its streak day (device-local, 03:00 cutoff). */
data class TalkPoint(
    val lang: Lang,
    val day: LocalDate,
    val speechMs: Long,
    val band: WpmBand,
    val fillersPerMin: Double,
    val mattr: Double?,
)

/** The 4 / 12 / all-weeks switch (US-050). */
enum class TrendRange(val weeks: Int?) { FOUR(4), TWELVE(12), ALL(null) }

enum class Direction { UP, DOWN, STEADY }

/**
 * One sparkline: a value per week in the range (null = no talk that week, drawn as a gap), the latest and the
 * earliest value in the range, and which way it moved. [lowerIsBetter] says which direction is good (fillers).
 */
data class Trend(
    val points: List<Double?>,
    val current: Double?,
    val first: Double?,
    val direction: Direction,
    val lowerIsBetter: Boolean = false,
) {
    /** Up for pace and variety, down for fillers. A worse trend is shown muted, never red (S16 notes). */
    val improved: Boolean
        get() = when (direction) {
            Direction.STEADY -> false
            Direction.UP -> !lowerIsBetter
            Direction.DOWN -> lowerIsBetter
        }
    val hasData: Boolean get() = points.count { it != null } >= 1
}

/** "Talks a week" bars: talks per week, and whether the weekly goal (days practised) was met that week. */
data class TalksTrend(val counts: List<Int>, val goalMet: List<Boolean>, val goal: Int) {
    val thisWeek: Int get() = counts.lastOrNull() ?: 0
    val metWeeks: Int get() = goalMet.count { it }
    val weeks: Int get() = counts.size
}

data class Trends(
    val range: TrendRange,
    val weekStarts: List<LocalDate>,
    /** % of talks in the comfortable WPM band, 0–100. */
    val pace: Trend,
    val fillers: Trend,
    /** MATTR; null weeks when no talk had ≥ 100 tokens. Hindi is provisional (US-013). */
    val variety: Trend,
    val talks: TalksTrend,
)

data class Totals(val talks: Int, val minutesSpoken: Int, val wordsLearned: Int)

/** Pure Progress maths (US-050), table-tested. Metrics compare within one language only (US-013). */
object ProgressAggregator {
    /** Trends need a second talk (S16 state 1). */
    const val MIN_TALKS_FOR_TRENDS = 2

    /** Under these, a change reads as "Steady": 5 percentage points of pace share, 10% of fillers, 0.02 variety. */
    const val STEADY_PACE_POINTS = 5.0
    const val STEADY_FILLERS_RATIO = 0.10
    const val STEADY_VARIETY = 0.02

    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Share of [talks] in the comfortable band, 0–100, or null with no talks. */
    fun bandShare(talks: List<TalkPoint>): Double? =
        if (talks.isEmpty()) null else 100.0 * talks.count { it.band == WpmBand.COMFORTABLE } / talks.size

    /** Mondays from the range start to [today]'s week. "All" starts at the first talk's week. */
    fun weeks(range: TrendRange, today: LocalDate, firstDay: LocalDate?): List<LocalDate> {
        val end = weekStart(today)
        val start = when (val n = range.weeks) {
            null -> weekStart(firstDay ?: today).coerceAtMost(end)
            else -> end.minusWeeks((n - 1).toLong())
        }
        val count = ChronoUnit.WEEKS.between(start, end).toInt() + 1
        return (0 until count).map { start.plusWeeks(it.toLong()) }
    }

    /**
     * Trends for [lang] over [range]. [allTalks] is every analysed talk (any language): talks a week counts them all,
     * the metric lines use only [lang]. [practisedDays] are credited streak days (not freezes), for "goal met".
     */
    fun trends(
        allTalks: List<TalkPoint>,
        lang: Lang,
        range: TrendRange,
        today: LocalDate,
        practisedDays: Set<LocalDate>,
        goal: Int,
    ): Trends {
        val talks = allTalks.filter { it.lang == lang }
        val weeks = weeks(range, today, allTalks.minOfOrNull { it.day })
        val byWeek = weeks.map { start -> talks.filter { weekStart(it.day) == start } }

        val pace = byWeek.map(::bandShare)
        val fillers = byWeek.map { w -> if (w.isEmpty()) null else w.map { it.fillersPerMin }.average() }
        val variety = byWeek.map { w -> w.mapNotNull { it.mattr }.takeIf { it.isNotEmpty() }?.average() }

        val counts = weeks.map { start -> allTalks.count { weekStart(it.day) == start } }
        val met = weeks.map { start -> practisedDays.count { weekStart(it) == start } >= goal }

        return Trends(
            range = range,
            weekStarts = weeks,
            pace = trend(pace, lowerIsBetter = false) { a, b -> abs(b - a) < STEADY_PACE_POINTS },
            fillers = trend(fillers, lowerIsBetter = true) { a, b -> if (a == 0.0) b == 0.0 else abs(b - a) / a < STEADY_FILLERS_RATIO },
            variety = trend(variety, lowerIsBetter = false) { a, b -> abs(b - a) < STEADY_VARIETY },
            talks = TalksTrend(counts, met, goal),
        )
    }

    fun totals(talkCount: Int, speechMs: Long, wordsLearned: Int): Totals =
        Totals(talkCount, ((speechMs + 30_000) / 60_000).toInt(), wordsLearned)

    private fun trend(points: List<Double?>, lowerIsBetter: Boolean, steady: (Double, Double) -> Boolean): Trend {
        val first = points.firstOrNull { it != null }
        val current = points.lastOrNull { it != null }
        val direction = when {
            first == null || current == null || points.count { it != null } < 2 -> Direction.STEADY
            steady(first, current) -> Direction.STEADY
            current > first -> Direction.UP
            else -> Direction.DOWN
        }
        return Trend(points, current, first, direction, lowerIsBetter)
    }
}
