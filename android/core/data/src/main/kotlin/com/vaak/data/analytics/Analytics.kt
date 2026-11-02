package com.vaak.data.analytics

import kotlinx.coroutines.flow.StateFlow

/**
 * PRD §8 events (ADR-007). Names and parameters only: never transcript text, words, names or prompts. Parameter
 * values are short codes, numbers or booleans.
 */
data class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    companion object {
        fun talkStarted(lang: String, track: String) = AnalyticsEvent("talk_started", mapOf("lang" to lang, "track" to track))
        fun talkStopped(durationS: Long, speechS: Long) = AnalyticsEvent("talk_stopped", mapOf("duration_s" to durationS, "speech_s" to speechS))
        val feedbackShown = AnalyticsEvent("feedback_shown")
        fun feedbackRated(value: Int) = AnalyticsEvent("feedback_rated", mapOf("value" to value))
        fun feedbackReported(reason: String) = AnalyticsEvent("feedback_reported", mapOf("reason" to reason))
        fun wordAdded(source: String) = AnalyticsEvent("word_added", mapOf("source" to source))
        fun reviewSessionCompleted(cards: Int) = AnalyticsEvent("review_session_completed", mapOf("cards" to cards))
        fun sentenceCheck(result: String) = AnalyticsEvent("sentence_check", mapOf("result" to result))
        val streakCredited = AnalyticsEvent("streak_credited")
        val reminderScheduled = AnalyticsEvent("reminder_scheduled")
        val reminderShown = AnalyticsEvent("reminder_shown")
        val reminderOpened = AnalyticsEvent("reminder_opened")
        val quotaHit = AnalyticsEvent("quota_hit")
        fun paywallViewed(source: String) = AnalyticsEvent("paywall_viewed", mapOf("source" to source))
        val trialStarted = AnalyticsEvent("trial_started")
        fun purchaseCompleted(plan: String) = AnalyticsEvent("purchase_completed", mapOf("plan" to plan))
        val subscriptionCancelled = AnalyticsEvent("subscription_cancelled")
        val shareCardShared = AnalyticsEvent("share_card_shared")
        val dataDeleted = AnalyticsEvent("data_deleted")
    }
}

/** The app's one analytics entry point. Call sites never check consent; the implementation does. */
interface Analytics {
    fun log(event: AnalyticsEvent)

    /** For tests and previews. */
    object None : Analytics {
        override fun log(event: AnalyticsEvent) = Unit
    }
}

/** Where consented events go. Firebase Analytics will be one (ADR-007); it isn't added yet (no google-services.json). */
fun interface AnalyticsSink {
    fun record(event: AnalyticsEvent)
}

/** Release builds until Firebase lands: events are dropped even with consent. */
object NoOpSink : AnalyticsSink {
    override fun record(event: AnalyticsEvent) = Unit
}

/**
 * The thin wrapper (D-026, D-028): records only while the user's "Share usage and crash reports" switch is on
 * (off by default, S04/S19). [consent] mirrors the `analytics_consent` setting.
 */
class ConsentAnalytics(private val consent: StateFlow<Boolean>, private val sink: AnalyticsSink) : Analytics {
    override fun log(event: AnalyticsEvent) {
        if (consent.value) sink.record(event)
    }
}
