package io.github.ukpratik.mukhar.data.work

import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.metrics.MetricAverages
import io.github.ukpratik.mukhar.domain.metrics.Metrics
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import java.io.File

/**
 * Speech-to-text and coach's notes for one talk (LLD §4.9: the same code runs in the foreground path and in
 * [AnalysisWorker]). [PipelineRouter] picks one per call from the user's analysis mode (ADR-008): on-device
 * ([io.github.ukpratik.mukhar.data.local.LocalAnalysisPipeline]), own keys ([ByokAnalysisPipeline]) or the Worker
 * ([RemoteAnalysisPipeline], off in R1). Debug builds can force a canned fake (build-plan "Debug hooks").
 */
interface AnalysisPipeline {
    suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult>
    suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult>
    suspend fun report(request: ReportRequest): Outcome<Unit>

    /** US-023 "Say it in a sentence": a ≤ 15 s clip → `/check-sentence` (the target word goes to STT as a key term). */
    suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult>
}

data class SentenceCheckRequest(
    val clientSessionId: String,
    val lang: Lang,
    val targetWord: String,
    val meaning: String,
    val durationMs: Long,
)

/** The Worker's answer (LLD §3): [result] is `ok`, `almost` or `no`. */
data class SentenceCheckResult(
    val transcript: String,
    val wordFound: Boolean,
    val result: String,
    val corrected: String,
    val example: String,
    val note: String,
)

data class TranscribeRequest(
    val clientSessionId: String,
    val kind: String,
    /** The parent talk's server session id (re-takes, US-017). */
    val parentServerSessionId: String?,
    val lang: Lang,
    val durationMs: Long,
    val queued: Boolean,
)

data class TranscribeResult(
    val serverSessionId: String,
    val provider: String,
    val model: String,
    val durationMs: Long,
    val words: List<Word>,
)

data class FeedbackRequest(
    val serverSessionId: String,
    val lang: Lang,
    val track: String,
    val prompt: String,
    val stutterFriendly: Boolean,
    val words: List<Word>,
    val lowConf: Set<Int>,
    val metrics: Metrics,
    val speechMs: Long,
    /** The user's previous 5 talks in this language (the offline coach compares against them). */
    val averages: MetricAverages? = null,
    val durationMs: Long = 0,
    /** Who made the transcript (`talk_transcript.provider`): own-key notes say when it was the offline engine. */
    val transcriptProvider: String? = null,
)

data class FeedbackResult(
    val promptVersion: String,
    val model: String,
    val feedback: Feedback,
    val dropped: Int,
    val fallbackGlow: Boolean,
    val fallbackGrow: Boolean,
    /**
     * True when the notes were checked on the phone already (the offline coach's notes, or own-key AI notes after
     * the grounding validator): TalkRepository doesn't validate them again, since metrics-based items have no quote.
     */
    val validatedOnDevice: Boolean = false,
)

data class ReportRequest(
    val serverSessionId: String,
    val reason: String,
    val consent: Boolean,
    val lang: Lang,
    val promptVersion: String?,
    val words: List<Word>,
    val lowConf: Set<Int>,
    val feedback: Feedback?,
    val note: String?,
)
