package io.github.ukpratik.mukhar.data.work

import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import java.io.File

/** The user's analysis mode, read per call (a seam for tests). */
fun interface AnalysisModeSource {
    suspend fun mode(): AnalysisMode
}

/**
 * THE [AnalysisPipeline] (ADR-008 "Pipeline modes"): each call goes to the pipeline for the user's current mode.
 * Mukhar Cloud is off in R1 ([AnalysisMode.CLOUD_ENABLED]), so a stored CLOUD runs on the phone. Own-key failures
 * fall back to the phone inside [ByokAnalysisPipeline].
 */
class PipelineRouter(
    private val mode: AnalysisModeSource,
    private val local: AnalysisPipeline,
    private val byok: AnalysisPipeline,
    private val cloud: AnalysisPipeline,
) : AnalysisPipeline {

    suspend fun target(): AnalysisPipeline = when (mode.mode().effective) {
        AnalysisMode.ON_DEVICE -> local
        AnalysisMode.OWN_KEYS -> byok
        AnalysisMode.CLOUD -> cloud
    }

    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> = target().transcribe(request, audio)
    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> = target().feedback(request)
    override suspend fun report(request: ReportRequest): Outcome<Unit> = target().report(request)
    override suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult> =
        target().checkSentence(request, audio)
}
