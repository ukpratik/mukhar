package io.github.ukpratik.mukhar.domain.share

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test
import java.time.LocalDate

/** US-071: improved or steady metrics only (max 3), worse ones left off; weeks between baseline and latest. */
class ShareCardRulesTest {
    @Test
    fun improvedAndSteadyMetricsAreListedInOrder() {
        val rows = ShareCardRules.metrics(CardMetrics(162.0, 5.8, 0.58), CardMetrics(138.0, 2.1, 0.71), Lang.EN)
        assertThat(rows.map { it.kind }).containsExactly(ShareMetricKind.FILLERS, ShareMetricKind.PACE, ShareMetricKind.VARIETY).inOrder()
        assertThat(rows.first().before).isEqualTo(5.8)
        assertThat(rows.first().after).isEqualTo(2.1)
    }

    @Test
    fun aMetricThatGotWorseIsLeftOff() {
        // More fillers, pace further from the band, less variety: nothing to brag about.
        assertThat(ShareCardRules.metrics(CardMetrics(165.0, 2.0, 0.70), CardMetrics(185.0, 4.0, 0.60), Lang.EN)).isEmpty()
        val rows = ShareCardRules.metrics(CardMetrics(140.0, 2.0, null), CardMetrics(175.0, 1.0, 0.7), Lang.EN)
        assertThat(rows.map { it.kind }).containsExactly(ShareMetricKind.FILLERS)
    }

    @Test
    fun hindiUsesItsOwnBand() {
        assertThat(ShareCardRules.paceOk(160.0, 145.0, Lang.HI)).isTrue()
        assertThat(ShareCardRules.paceOk(145.0, 158.0, Lang.HI)).isFalse()
    }

    @Test
    fun weeksAreWholeWeeksAtLeastOne() {
        assertThat(ShareCardRules.weeks(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 6))).isEqualTo(4)
        assertThat(ShareCardRules.weeks(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6))).isEqualTo(1)
    }
}
