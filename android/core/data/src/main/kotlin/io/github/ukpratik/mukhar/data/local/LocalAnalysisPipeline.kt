package io.github.ukpratik.mukhar.data.local

import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.util.IdGenerator
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.FeedbackRequest
import io.github.ukpratik.mukhar.data.work.FeedbackResult
import io.github.ukpratik.mukhar.data.work.ReportRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckResult
import io.github.ukpratik.mukhar.data.work.TranscribeRequest
import io.github.ukpratik.mukhar.data.work.TranscribeResult
import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.coach.RuleCoach
import io.github.ukpratik.mukhar.domain.metrics.EngineReliability
import io.github.ukpratik.mukhar.domain.words.TargetWordMatcher
import java.io.File

/**
 * "On this phone" (ADR-008): speech-to-text by [LocalTranscriber], notes by [RuleCoach]. No server, no quota: the
 * session id is made here, reports stay on the phone, and the sentence check is only "word found and ≥ 5 words".
 */
class LocalAnalysisPipeline(
    private val transcriber: LocalTranscriber,
    private val synonyms: SynonymSource,
    private val ids: IdGenerator,
) : AnalysisPipeline {

    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> =
        when (val r = transcriber.transcribe(audio, request.lang, request.durationMs)) {
            is Outcome.Err -> r
            is Outcome.Ok -> Outcome.Ok(
                TranscribeResult(
                    serverSessionId = LOCAL_PREFIX + ids.newId(),
                    provider = EngineReliability.PROVIDER_ON_DEVICE,
                    model = transcriber.modelId(request.lang),
                    durationMs = request.durationMs,
                    words = r.value,
                ),
            )
        }

    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> = Outcome.Ok(coach(request, emptyList()))

    /** The offline coach's notes, with [notes] explaining why they were used (own-key fallbacks). */
    suspend fun coach(request: FeedbackRequest, notes: List<AnalysisNote>): FeedbackResult {
        val feedback = RuleCoach.coach(
            RuleCoach.Input(
                words = request.words,
                lang = request.lang,
                metrics = request.metrics,
                average = request.averages,
                durationMs = request.durationMs.takeIf { it > 0 } ?: request.speechMs,
                stutterFriendly = request.stutterFriendly,
                synonyms = synonyms.book(request.lang),
                notes = notes,
            ),
        )
        return FeedbackResult(RuleCoach.VERSION, RuleCoach.MODEL, feedback, 0, fallbackGlow = false, fallbackGrow = false, validatedOnDevice = true)
    }

    /** Reports stay on this phone in local modes (TalkRepository marks the talk as reported). */
    override suspend fun report(request: ReportRequest): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult> =
        when (val r = transcriber.transcribe(audio, request.lang, request.durationMs)) {
            is Outcome.Err -> r
            is Outcome.Ok -> Outcome.Ok(sentenceVerdict(request, r.value.map { it.w }))
        }

    companion object {
        const val LOCAL_PREFIX = "local-"
        const val MIN_SENTENCE_WORDS = 5

        /** No grammar check offline: `ok` when the target word was heard in a sentence of ≥ 5 words, else `no`. */
        fun sentenceVerdict(request: SentenceCheckRequest, words: List<String>): SentenceCheckResult {
            val found = TargetWordMatcher.found(request.targetWord, words)
            val ok = found && words.count { it.isNotBlank() } >= MIN_SENTENCE_WORDS
            return SentenceCheckResult(
                transcript = words.joinToString(" "),
                wordFound = found,
                result = if (ok) "ok" else "no",
                corrected = "",
                example = "",
                note = "",
            )
        }
    }
}
