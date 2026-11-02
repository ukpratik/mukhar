package com.vaak.data.work

import com.vaak.data.net.Outcome
import com.vaak.domain.feedback.Feedback
import com.vaak.domain.metrics.Metrics
import com.vaak.domain.model.Lang
import com.vaak.domain.model.Word
import java.io.File

/**
 * Speech-to-text and coach's notes for one talk (LLD §4.9: the same code runs in the foreground path and in
 * [AnalysisWorker]). [RemoteAnalysisPipeline] calls the Worker; debug builds default to a canned fake
 * (build-plan "Debug hooks").
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
)

data class FeedbackResult(
    val promptVersion: String,
    val model: String,
    val feedback: Feedback,
    val dropped: Int,
    val fallbackGlow: Boolean,
    val fallbackGrow: Boolean,
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
