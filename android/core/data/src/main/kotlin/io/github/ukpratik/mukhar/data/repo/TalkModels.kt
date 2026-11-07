package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.db.MetricsEntity
import io.github.ukpratik.mukhar.data.db.TalkSessionEntity
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import io.github.ukpratik.mukhar.domain.prompt.Track
import java.time.LocalDate

/** `talk_session.status` (LLD §2.1). */
object TalkStatus {
    const val RECORDING = "recording"
    const val RECORDED = "recorded"
    const val QUEUED = "queued"
    const val UPLOADING = "uploading"
    const val TRANSCRIBED = "transcribed"
    const val FEEDBACK_READY = "feedback_ready"
    const val FEEDBACK_FAILED = "feedback_failed"
    const val FAILED = "failed"
    const val DISCARDED = "discarded"

    /** Rows the queue still owes an analysis (re-enqueued at app start). */
    val PENDING = listOf(RECORDED, QUEUED, UPLOADING, TRANSCRIBED, FAILED, FEEDBACK_FAILED)
}

/** Extra `error_code` values set by the app (server codes come from `AppError.code`). */
object TalkCodes {
    /** Process died while recording; the saved part waits for the user (S09 state 8). */
    const val RECOVERED = "RECOVERED"
    const val AUDIO_MISSING = "AUDIO_MISSING"
}

/** `talk_session.kind` written by the recorder. [BASELINE] is the free one-time starting point (US-003, D-031). */
enum class TalkKind(val key: String) { TALK("talk"), RETAKE("retake"), BASELINE("baseline") }

data class TalkSpec(
    val kind: TalkKind,
    val lang: Lang,
    val track: Track,
    val promptId: String,
    val parentId: String? = null,
)

/** Streak credit at stop time (US-031): "Day 13 counted · weekly goal met". */
data class Credit(val day: LocalDate, val streak: Int, val weeklyGoalMet: Boolean)

sealed interface StopOutcome {
    /** Under 10 s of speech: no API call, no credit, audio discarded (US-018). */
    data class TooShort(val speechMs: Long) : StopOutcome

    data class Saved(val sessionId: String, val credit: Credit?) : StopOutcome
}

/** What one analysis attempt achieved; [AnalysisWorker] maps it to a WorkManager result. */
sealed interface AnalysisRun {
    data object Done : AnalysisRun
    data object Retry : AnalysisRun
    data class RetryAfter(val delayMs: Long) : AnalysisRun
    data object LeaseHeld : AnalysisRun
    data object Gone : AnalysisRun
}

data class StoredFeedback(
    val feedback: Feedback,
    val promptVersion: String,
    val dropped: Int,
    val fallbackGlow: Boolean,
    val fallbackGrow: Boolean,
    val rating: Int?,
    val reported: Boolean,
    /** Who wrote the notes (`rule-coach` for the offline coach, a Claude model id, or the Worker's model). */
    val model: String = "",
)

/** Everything the Analysing/Results screens need, straight from Room so it survives process death (LLD §5). */
data class TalkDetails(
    val session: TalkSessionEntity,
    val words: List<Word>?,
    val metrics: MetricsEntity?,
    val feedback: StoredFeedback?,
    val credit: Credit?,
    /** `talk_transcript.provider` (`vosk`, `deepgram`, `sarvam`, …): decides the per-metric reliability (ADR-008). */
    val transcriptProvider: String? = null,
) {
    val lang: Lang get() = Lang.fromCode(session.lang)
}
