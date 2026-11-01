package com.vaak.app.feature.talk

import com.vaak.app.feature.talk.report.ReportMapper
import com.vaak.app.feature.talk.report.ReportUiState
import com.vaak.data.db.FeedbackEntity
import com.vaak.data.db.TalkSessionEntity
import com.vaak.data.debug.CannedTalks
import com.vaak.data.repo.Credit
import com.vaak.data.repo.StoredFeedback
import com.vaak.data.repo.TalkDetails
import com.vaak.data.repo.TalkStatus
import com.vaak.domain.feedback.GroundingValidator
import com.vaak.domain.metrics.MetricAverages
import com.vaak.domain.model.Lang
import java.time.LocalDate

/** Report states built from the debug fake's canned talks, exactly as the app would store them. */
object TalkFixtures {
    const val NOW = 1_793_590_200_000L // 2026-11-02T04:30:00Z

    fun session(id: String = "s1", lang: Lang = Lang.EN, status: String = TalkStatus.FEEDBACK_READY, errorCode: String? = null, durationMs: Int = 180_000, speechMs: Int = 150_000, snr: Double = 25.0) =
        TalkSessionEntity(
            id = id, kind = "talk", parentId = null, lang = lang.code, track = if (lang == Lang.HI) "everyday" else "professional",
            promptId = if (lang == Lang.HI) "hi-001" else "en-002", createdAt = NOW, zoneId = "Asia/Kolkata", durationMs = durationMs,
            speechMs = speechMs, snrDb = snr, status = status, leaseUntil = null, attempts = 1, errorCode = errorCode, audioPath = null,
            audioKept = false, serverSessionId = "srv-$id", quotaDay = null, updatedAt = NOW,
        )

    fun details(lang: Lang = Lang.EN, status: String = TalkStatus.FEEDBACK_READY, withFeedback: Boolean = true, errorCode: String? = null, stutter: Boolean = false, snr: Double = 25.0): TalkDetails {
        val words = CannedTalks.words(lang)
        val tokens = words.map { it.w }
        val lowConf = words.indices.filter { words[it].conf < 0.6 }.toSet()
        val validated = GroundingValidator.validate(CannedTalks.feedback(lang, tokens), tokens, lowConf, stutter)
        val stored = StoredFeedback(validated.feedback, "fb-2026-11-01a", validated.dropped, validated.fallbackGlow, validated.fallbackGrow, null, false)
        return TalkDetails(
            session = session(lang = lang, status = status, errorCode = errorCode, snr = snr),
            words = if (status in listOf(TalkStatus.RECORDED, TalkStatus.UPLOADING, TalkStatus.QUEUED, TalkStatus.FAILED)) null else words,
            metrics = null,
            feedback = if (withFeedback) stored else null,
            credit = Credit(LocalDate.of(2026, 11, 2), 13, weeklyGoalMet = true),
        )
    }

    val AVERAGES = MetricAverages(talks = 5, wpm = 130.0, fillersPerMin = 9.0, longPausesPerMin = 0.7, repetitions = 4.0, mattr = 0.66)

    fun report(lang: Lang = Lang.EN, status: String = TalkStatus.FEEDBACK_READY, withFeedback: Boolean = true, stutter: Boolean = false, errorCode: String? = null, manualRetryFailed: Boolean = false, snr: Double = 25.0): ReportUiState =
        ReportMapper.map(details(lang, status, withFeedback, errorCode, stutter, snr), AVERAGES, "Tell us about a project you're proud of.", stutter, 180, manualRetryFailed)

    @Suppress("unused")
    private fun unused(f: FeedbackEntity) = f
}
