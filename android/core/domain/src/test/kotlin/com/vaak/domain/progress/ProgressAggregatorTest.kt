package com.vaak.domain.progress

import com.google.common.truth.Truth.assertThat
import com.vaak.domain.metrics.WpmBand
import com.vaak.domain.model.Lang
import org.junit.Test
import java.time.LocalDate

/** US-050: the WPM band %, weekly buckets, directions, talks a week and totals. */
class ProgressAggregatorTest {
    private val today = LocalDate.of(2026, 11, 6) // a Friday; its week starts Mon 2 Nov

    private fun talk(day: LocalDate, band: WpmBand = WpmBand.COMFORTABLE, fillers: Double = 3.0, mattr: Double? = 0.6, lang: Lang = Lang.EN) =
        TalkPoint(lang, day, 60_000, band, fillers, mattr)

    @Test
    fun bandShareIsThePercentOfTalksInTheComfortableBand() {
        val talks = listOf(talk(today), talk(today, WpmBand.FAST), talk(today, WpmBand.SLOW), talk(today))
        assertThat(ProgressAggregator.bandShare(talks)).isWithin(1e-9).of(50.0)
        assertThat(ProgressAggregator.bandShare(emptyList())).isNull()
        assertThat(ProgressAggregator.bandShare(listOf(talk(today, WpmBand.FAST)))).isWithin(1e-9).of(0.0)
    }

    @Test
    fun weeksRunMondayToTodayForFourTwelveAndAll() {
        assertThat(ProgressAggregator.weeks(TrendRange.FOUR, today, null))
            .containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)).inOrder()
        assertThat(ProgressAggregator.weeks(TrendRange.TWELVE, today, null)).hasSize(12)
        // "All" starts at the first talk's week.
        assertThat(ProgressAggregator.weeks(TrendRange.ALL, today, LocalDate.of(2026, 10, 21)))
            .containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)).inOrder()
        assertThat(ProgressAggregator.weeks(TrendRange.ALL, today, null)).containsExactly(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun trendsBucketByWeekAndSayWhichWayIsGood() {
        val w1 = LocalDate.of(2026, 10, 13)
        val w4 = LocalDate.of(2026, 11, 3)
        val talks = listOf(
            talk(w1, WpmBand.FAST, fillers = 6.0, mattr = 0.55),
            talk(w1, WpmBand.COMFORTABLE, fillers = 5.0, mattr = 0.57),
            talk(w4, WpmBand.COMFORTABLE, fillers = 2.0, mattr = 0.70),
            talk(w4, WpmBand.COMFORTABLE, fillers = 2.4, mattr = null),
        )
        val t = ProgressAggregator.trends(talks, Lang.EN, TrendRange.FOUR, today, emptySet(), goal = 3)
        assertThat(t.pace.points).containsExactly(50.0, null, null, 100.0).inOrder()
        assertThat(t.pace.direction).isEqualTo(Direction.UP)
        assertThat(t.pace.improved).isTrue()
        assertThat(t.fillers.first).isWithin(1e-9).of(5.5)
        assertThat(t.fillers.current).isWithin(1e-9).of(2.2)
        assertThat(t.fillers.direction).isEqualTo(Direction.DOWN)
        assertThat(t.fillers.improved).isTrue() // lower is better
        assertThat(t.variety.current).isWithin(1e-9).of(0.70) // talks under 100 tokens have no MATTR
        assertThat(t.variety.improved).isTrue()
    }

    @Test
    fun smallChangesAreSteadyAndWorseIsNotImproved() {
        val a = LocalDate.of(2026, 10, 27)
        val b = LocalDate.of(2026, 11, 3)
        val talks = listOf(talk(a, fillers = 3.0, mattr = 0.70), talk(b, fillers = 3.2, mattr = 0.71), talk(b, WpmBand.FAST, fillers = 3.2, mattr = null))
        val t = ProgressAggregator.trends(talks, Lang.EN, TrendRange.FOUR, today, emptySet(), goal = 3)
        assertThat(t.fillers.direction).isEqualTo(Direction.STEADY)
        assertThat(t.variety.direction).isEqualTo(Direction.STEADY)
        assertThat(t.pace.direction).isEqualTo(Direction.DOWN) // 100% → 50%
        assertThat(t.pace.improved).isFalse()
    }

    @Test
    fun metricsStayWithinOneLanguageButTalksAWeekCountsAll() {
        val talks = listOf(talk(today, lang = Lang.EN, fillers = 2.0), talk(today, lang = Lang.HI, fillers = 9.0), talk(today, lang = Lang.HI, fillers = 7.0))
        val hi = ProgressAggregator.trends(talks, Lang.HI, TrendRange.FOUR, today, emptySet(), goal = 3)
        assertThat(hi.fillers.current).isWithin(1e-9).of(8.0)
        assertThat(hi.talks.thisWeek).isEqualTo(3)
    }

    @Test
    fun goalMetCountsPractisedDaysPerWeek() {
        val days = setOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 2))
        val t = ProgressAggregator.trends(listOf(talk(today)), Lang.EN, TrendRange.FOUR, today, days, goal = 3)
        assertThat(t.talks.goalMet).containsExactly(false, false, true, false).inOrder()
        assertThat(t.talks.metWeeks).isEqualTo(1)
        assertThat(t.talks.counts).containsExactly(0, 0, 0, 1).inOrder()
    }

    @Test
    fun totalsRoundMinutesSpoken() {
        assertThat(ProgressAggregator.totals(46, 104 * 60_000L + 20_000, 128)).isEqualTo(Totals(46, 104, 128))
        assertThat(ProgressAggregator.totals(1, 90_000, 3).minutesSpoken).isEqualTo(2)
    }
}
