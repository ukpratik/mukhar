package io.github.ukpratik.mukhar.data.analytics

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.repo.RecordingSink
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

/** D-026/D-028: nothing is recorded unless "Share usage and crash reports" is on (off by default). */
class AnalyticsTest {
    @Test
    fun noOpWhenConsentIsOffAndRecordsWhenOn() {
        val consent = MutableStateFlow(false)
        val sink = RecordingSink()
        val analytics = ConsentAnalytics(consent, sink)
        analytics.log(AnalyticsEvent.talkStarted("en", "interviews"))
        analytics.log(AnalyticsEvent.paywallViewed("quota"))
        assertThat(sink.events).isEmpty()

        consent.value = true
        analytics.log(AnalyticsEvent.paywallViewed("quota"))
        assertThat(sink.events).containsExactly(AnalyticsEvent("paywall_viewed", mapOf("source" to "quota")))

        consent.value = false
        analytics.log(AnalyticsEvent.dataDeleted)
        assertThat(sink.events).hasSize(1)
    }

    @Test
    fun eventsCarryNoContent() {
        // Parameter values are codes and numbers only (ADR-007).
        val events = listOf(
            AnalyticsEvent.talkStopped(118, 96), AnalyticsEvent.feedbackRated(3), AnalyticsEvent.wordAdded("talk"),
            AnalyticsEvent.sentenceCheck("almost"), AnalyticsEvent.purchaseCompleted("yearly"), AnalyticsEvent.reviewSessionCompleted(7),
        )
        events.flatMap { it.params.values }.forEach { v ->
            assertThat(v is Number || (v is String && v.length <= 20 && v.matches(Regex("[a-z_-]+")))).isTrue()
        }
    }
}
