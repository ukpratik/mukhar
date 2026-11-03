package io.github.ukpratik.mukhar.app.feature.progress

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.db.InsightEntity
import io.github.ukpratik.mukhar.domain.billing.EntitlementSnapshot
import io.github.ukpratik.mukhar.domain.billing.SubscriptionStatus
import io.github.ukpratik.mukhar.domain.progress.Direction
import io.github.ukpratik.mukhar.domain.progress.TrendRange
import org.junit.Test
import java.time.Instant

/** S16 mapping: empty vs. trends, the insight card copy inputs, then-and-now deltas and the plan row. */
class ProgressMapperTest {
    private val now = Instant.parse("2026-11-06T14:00:00Z")

    private fun map(data: io.github.ukpratik.mukhar.data.repo.ProgressData, ent: EntitlementSnapshot = EntitlementSnapshot.FREE) =
        ProgressMapper.map(data, null, TrendRange.TWELVE, ent, now, ProgressFixtures.zone, talksEnabled = true)

    @Test
    fun oneTalkShowsTotalsButNoTrends() {
        val s = map(ProgressFixtures.one)
        assertThat(s.showTrends).isFalse()
        assertThat(s.trends).isNull()
        assertThat(s.totals.talks).isEqualTo(1)
        assertThat(s.totals.wordsLearned).isEqualTo(3)
        assertThat(s.showBaselineCta).isTrue()
        assertThat(s.compare).isNull()
        assertThat(s.plan.status).isEqualTo(SubscriptionStatus.FREE)
    }

    @Test
    fun manyTalksShowTrendsAndThenAndNow() {
        val s = map(ProgressFixtures.many)
        assertThat(s.showTrends).isTrue()
        val t = s.trends!!
        assertThat(t.fillers.direction).isEqualTo(Direction.DOWN)
        assertThat(t.fillers.improved).isTrue()
        assertThat(t.pace.current).isWithin(1e-9).of(100.0)
        val c = s.compare!!
        assertThat(c.rows.map { it.kind }).containsExactly(CompareKind.PACE, CompareKind.FILLERS, CompareKind.PAUSES, CompareKind.VARIETY).inOrder()
        assertThat(c.rows[0].delta).isEqualTo(CompareDelta.IntoBand)
        assertThat((c.rows[1].delta as CompareDelta.Percent).percent).isEqualTo(66) // 5.8 → 2.0
        assertThat(c.rows[1].delta.good).isTrue()
        assertThat(s.canShare).isTrue()
        assertThat(s.showBaselineCta).isFalse()
    }

    @Test
    fun theOldestUnseenInsightBecomesTheCard() {
        val data = ProgressFixtures.many.copy(
            insights = listOf(InsightEntity("talks_10", 1, seen = true), InsightEntity("fillers_down_25_en", 2, seen = false), InsightEntity("streak_7", 3, false)),
        )
        val i = ProgressMapper.insight(data)!!
        assertThat(i.kind).isEqualTo(InsightKind.FILLERS_DOWN)
        assertThat(i.first).isWithin(1e-9).of((6.0 + 5.2 + 4.4) / 3)
        assertThat(i.last).isWithin(1e-9).of((3.6 + 2.8 + 2.0) / 3)
        assertThat(i.percent).isEqualTo(46)
        assertThat(ProgressMapper.insight(data.copy(insights = listOf(InsightEntity("streak_7", 3, false))))!!.count).isEqualTo(7)
    }

    @Test
    fun aWorseMetricIsMutedNotRed() {
        val worse = ProgressFixtures.data(listOf(ProgressFixtures.baseline, ProgressFixtures.record("t", ProgressFixtures.today, fillers = 7.0, mattr = 0.50)))
        val rows = ProgressMapper.compare(worse)!!.rows
        val fillers = rows.first { it.kind == CompareKind.FILLERS }.delta as CompareDelta.Percent
        assertThat(fillers.down).isFalse()
        assertThat(fillers.good).isFalse()
        assertThat(rows.first { it.kind == CompareKind.VARIETY }.delta.good).isFalse()
    }

    @Test
    fun planRowFollowsTheEntitlement() {
        val trial = EntitlementSnapshot(premium = true, expiryMs = now.plusSeconds(86_400).toEpochMilli(), isTrial = true, autoRenewing = true)
        val s = map(ProgressFixtures.one, trial)
        assertThat(s.plan.status).isEqualTo(SubscriptionStatus.TRIAL)
        assertThat(s.plan.expiry).isEqualTo(java.time.LocalDate.of(2026, 11, 7))
    }
}
