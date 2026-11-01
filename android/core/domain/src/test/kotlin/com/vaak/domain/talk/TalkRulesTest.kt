package com.vaak.domain.talk

import com.google.common.truth.Truth.assertThat
import com.vaak.domain.metrics.Metrics
import com.vaak.domain.metrics.WpmBand
import org.junit.Test

class TalkRulesTest {
    @Test
    fun storageWarnsBelow50MbAndBlocksBelow10Mb() {
        val mb = 1024L * 1024
        assertThat(StoragePolicy.level(200 * mb)).isEqualTo(StorageLevel.OK)
        assertThat(StoragePolicy.level(50 * mb)).isEqualTo(StorageLevel.OK)
        assertThat(StoragePolicy.level(38 * mb)).isEqualTo(StorageLevel.LOW)
        assertThat(StoragePolicy.level(10 * mb)).isEqualTo(StorageLevel.LOW)
        assertThat(StoragePolicy.level(6 * mb)).isEqualTo(StorageLevel.BLOCKED)
    }

    @Test
    fun retakeCapIsAbout25PercentLessWithA45sMinimum() {
        assertThat(SayItAgain.capSeconds(180_000, 180)).isEqualTo(135) // 3:00 → 2:15
        assertThat(SayItAgain.capSeconds(160_000, 180)).isEqualTo(120) // 2:40 → 2:00
        assertThat(SayItAgain.capSeconds(120_000, 120)).isEqualTo(90) // 2:00 → 1:30
        assertThat(SayItAgain.capSeconds(40_000, 120)).isEqualTo(45) // minimum 0:45
        assertThat(SayItAgain.capSeconds(400_000, 120)).isEqualTo(120) // never above the plan's cap
    }

    private fun m(wpm: Double, band: WpmBand, fillers: Double, pauses: Double?) =
        Metrics(wpm, band, 100, 0, fillers, null, pauses, null, null, 60_000, emptySet(), emptySet())

    @Test
    fun celebratesWhenAMetricImprovesByTenPercent() {
        val first = m(138.0, WpmBand.COMFORTABLE, 3.2, 0.7)
        val better = SayItAgain.deltas(first, m(146.0, WpmBand.COMFORTABLE, 1.4, 0.4), 110.0, 160.0)
        assertThat(better.fillersImproved).isTrue()
        assertThat(better.pausesImproved).isTrue()
        assertThat(better.celebrate).isTrue()
        val same = SayItAgain.deltas(first, m(151.0, WpmBand.COMFORTABLE, 3.6, 1.1), 110.0, 160.0)
        assertThat(same.celebrate).isFalse()
    }

    @Test
    fun paceImprovesWhenItMovesIntoTheBand() {
        val fast = m(180.0, WpmBand.FAST, 2.0, 0.5)
        assertThat(SayItAgain.paceImproved(fast, m(150.0, WpmBand.COMFORTABLE, 2.0, 0.5), 110.0, 160.0)).isTrue()
        assertThat(SayItAgain.paceImproved(fast, m(178.0, WpmBand.FAST, 2.0, 0.5), 110.0, 160.0)).isFalse()
        assertThat(SayItAgain.paceImproved(m(140.0, WpmBand.COMFORTABLE, 2.0, 0.5), m(150.0, WpmBand.COMFORTABLE, 2.0, 0.5), 110.0, 160.0)).isFalse()
    }

    @Test
    fun stutterFriendlyPausesNeverCelebrate() {
        assertThat(SayItAgain.improvedDown(null, 0.1)).isFalse()
        assertThat(SayItAgain.aboutSame(3.2, 3.4)).isTrue()
        assertThat(SayItAgain.aboutSame(0.0, 0.0)).isTrue()
    }
}
