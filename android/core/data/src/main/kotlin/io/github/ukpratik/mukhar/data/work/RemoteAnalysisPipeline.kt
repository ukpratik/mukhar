package io.github.ukpratik.mukhar.data.work

import io.github.ukpratik.mukhar.data.net.CheckSentenceMetaDto
import io.github.ukpratik.mukhar.data.net.ErrorMapper
import io.github.ukpratik.mukhar.data.net.FeedbackMetricsDto
import io.github.ukpratik.mukhar.data.net.FeedbackRequestDto
import io.github.ukpratik.mukhar.data.net.FeedbackTokenDto
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.ReportPayloadDto
import io.github.ukpratik.mukhar.data.net.ReportRequestDto
import io.github.ukpratik.mukhar.data.net.Stage
import io.github.ukpratik.mukhar.data.net.TranscribeMetaDto
import io.github.ukpratik.mukhar.data.net.MukharApi
import io.github.ukpratik.mukhar.data.net.toDomain
import io.github.ukpratik.mukhar.data.net.toDto
import io.github.ukpratik.mukhar.domain.model.Word
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

/** [AnalysisPipeline] over the Worker API v1 (LLD §3). Not yet run against a real server. */
class RemoteAnalysisPipeline @Inject constructor(
    private val api: MukharApi,
    private val errors: ErrorMapper,
) : AnalysisPipeline {

    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> = call(Stage.STT) {
        val meta = TranscribeMetaDto(
            clientSessionId = request.clientSessionId,
            kind = request.kind,
            parentSessionId = request.parentServerSessionId,
            lang = request.lang.code,
            durationMs = request.durationMs,
            queued = request.queued,
        )
        val part = MultipartBody.Part.createFormData("audio", "talk.aac", audio.asRequestBody(AAC))
        val r = api.transcribe(meta, part)
        TranscribeResult(r.sessionId, r.provider, r.model, r.durationMs, r.words.map { it.toDomain() })
    }

    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> = call(Stage.LLM) {
        val m = request.metrics
        val body = FeedbackRequestDto(
            sessionId = request.serverSessionId,
            lang = request.lang.code,
            track = request.track,
            prompt = request.prompt,
            stutterFriendly = request.stutterFriendly,
            tokens = tokens(request.words, request.lowConf),
            metrics = FeedbackMetricsDto(
                wpm = m.wpm,
                wpmBand = m.wpmBand.key,
                fillersPerMin = m.fillersPerMin,
                fillerCount = m.fillerCount,
                longPausesPerMin = m.longPausesPerMin,
                repetitions = m.repetitions,
                mattr = m.mattr,
                speechS = request.speechMs / 1000,
            ),
        )
        val r = api.feedback(body)
        FeedbackResult(r.promptVersion, r.model, r.feedback.toDomain(), r.dropped, r.fallback.glow, r.fallback.grow)
    }

    override suspend fun report(request: ReportRequest): Outcome<Unit> = call(Stage.SERVER) {
        val payload = if (request.consent) {
            ReportPayloadDto(tokens(request.words, request.lowConf), request.feedback?.toDto(), request.note)
        } else {
            null
        }
        api.report(
            ReportRequestDto(
                sessionId = request.serverSessionId,
                reason = request.reason,
                consent = request.consent,
                lang = request.lang.code,
                promptVersion = request.promptVersion,
                payload = payload,
            ),
        )
        Unit
    }

    override suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult> = call(Stage.LLM) {
        val meta = CheckSentenceMetaDto(
            clientSessionId = request.clientSessionId,
            lang = request.lang.code,
            targetWord = request.targetWord,
            meaning = request.meaning,
            durationMs = request.durationMs,
            keyterms = listOf(request.targetWord),
        )
        val part = MultipartBody.Part.createFormData("audio", "sentence.aac", audio.asRequestBody(AAC))
        val r = api.checkSentence(meta, part)
        SentenceCheckResult(r.transcript, r.wordFound, r.result, r.corrected, r.example, r.note)
    }

    private suspend fun <T> call(stage: Stage, block: suspend () -> T): Outcome<T> = try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Outcome.Err(errors.map(e, stage))
    }

    companion object {
        private val AAC = "audio/aac".toMediaType()

        /** Indexed tokens for `/feedback`; low-confidence ones are marked `lc` (LLD §4.3). */
        fun tokens(words: List<Word>, lowConf: Set<Int>): List<FeedbackTokenDto> =
            words.mapIndexed { i, w -> FeedbackTokenDto(i = i, w = w.w, lc = i in lowConf) }
    }
}
