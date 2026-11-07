package io.github.ukpratik.mukhar.data.work

import io.github.ukpratik.mukhar.data.byok.AnthropicClient
import io.github.ukpratik.mukhar.data.byok.FeedbackPrompt
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.ByokResult
import io.github.ukpratik.mukhar.data.byok.DeepgramClient
import io.github.ukpratik.mukhar.data.byok.KeySource
import io.github.ukpratik.mukhar.data.byok.SarvamClient
import io.github.ukpratik.mukhar.data.byok.SttResult
import io.github.ukpratik.mukhar.data.byok.UsageMeter
import io.github.ukpratik.mukhar.data.byok.toAppError
import io.github.ukpratik.mukhar.data.local.LocalAnalysisPipeline
import io.github.ukpratik.mukhar.data.local.LocalTranscriber
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.Stage
import io.github.ukpratik.mukhar.data.util.IdGenerator
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.feedback.GroundingValidator
import io.github.ukpratik.mukhar.domain.metrics.EngineFamily
import io.github.ukpratik.mukhar.domain.metrics.EngineReliability
import io.github.ukpratik.mukhar.domain.model.Lang
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * "Use my own keys" (ADR-008): the phone calls Deepgram or Sarvam (speech-to-text) and Anthropic (coaching) with the
 * user's keys. STT and LLM are independent:
 * - STT key only → vendor words + the offline coach;
 * - LLM key only → offline words (when the model is installed) + Claude;
 * - neither usable → fully on the phone.
 * Any own-key failure (network, 401, 429, refusal, bad output, ungrounded notes) falls back to the phone and says so
 * in the notes. Without an offline model to fall back to, the vendor error is returned and the talk stays saved.
 */
class ByokAnalysisPipeline(
    private val keys: KeySource,
    private val deepgram: DeepgramClient,
    private val sarvam: SarvamClient,
    private val anthropic: AnthropicClient,
    private val local: LocalAnalysisPipeline,
    private val transcriber: LocalTranscriber,
    private val usage: UsageMeter,
    private val ids: IdGenerator,
) : AnalysisPipeline {

    /** Notes from the transcribe step, handed to the notes step (in memory; lost only if the process dies between). */
    private val pendingNotes = ConcurrentHashMap<String, List<AnalysisNote>>()

    /** Talks already counted in this month's usage. */
    private val counted = ConcurrentHashMap.newKeySet<String>()

    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> {
        if (usage.overSoftCap()) return localTranscribe(request, audio, listOf(AnalysisNote.SOFT_CAP_REACHED))
        val (provider, key) = sttKey(request.lang) ?: return local.transcribe(request, audio)
        val result = when (provider) {
            ByokProvider.SARVAM -> sarvam.transcribe(key, audio, request.lang)
            else -> deepgram.transcribe(key, audio, request.lang)
        }
        return when (result) {
            is ByokResult.Ok -> {
                val stt = result.value
                val id = LocalAnalysisPipeline.LOCAL_PREFIX + ids.newId()
                recordStt(id, stt)
                Outcome.Ok(TranscribeResult(id, stt.provider, stt.model, request.durationMs, stt.words))
            }
            is ByokResult.Fail -> {
                MukharLog.i("byok: ${provider.id} stt failed (${result.error::class.simpleName})")
                val note = if (result.error.keyRejected) AnalysisNote.STT_KEY_REJECTED else AnalysisNote.STT_FALLBACK
                if (!transcriber.isReady(request.lang)) return Outcome.Err(result.error.toAppError(Stage.STT))
                localTranscribe(request, audio, listOf(note))
            }
        }
    }

    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> {
        val notes = pendingNotes.remove(request.serverSessionId).orEmpty().toMutableList()
        val key = if (AnalysisNote.SOFT_CAP_REACHED in notes || usage.overSoftCap()) null else keys.key(ByokProvider.ANTHROPIC)
        if (key == null) {
            if (usage.overSoftCap() && AnalysisNote.SOFT_CAP_REACHED !in notes) notes += AnalysisNote.SOFT_CAP_REACHED
            return Outcome.Ok(local.coach(request, notes))
        }
        val offlineWords = EngineReliability.family(request.transcriptProvider) == EngineFamily.ON_DEVICE
        return when (val r = anthropic.feedback(key, request)) {
            is ByokResult.Fail -> {
                MukharLog.i("byok: anthropic failed (${r.error::class.simpleName})")
                val note = if (r.error.keyRejected) AnalysisNote.LLM_KEY_REJECTED else AnalysisNote.LLM_FALLBACK
                Outcome.Ok(local.coach(request, notes + note))
            }
            is ByokResult.Ok -> {
                usage.record(talks = countOnce(request.serverSessionId), inputTokens = r.value.usage.inputTokens, outputTokens = r.value.usage.outputTokens)
                val checked = GroundingValidator.validate(r.value.feedback, request.words.map { it.w }, request.lowConf, request.stutterFriendly)
                val rules = local.coach(request, emptyList()).feedback
                if (offlineWords) notes += AnalysisNote.OFFLINE_TRANSCRIPT
                val ungrounded = checked.fallbackGlow || checked.fallbackGrow
                if (ungrounded) notes += AnalysisNote.LLM_UNGROUNDED
                val merged = checked.feedback.copy(
                    // A dropped glow or grow is replaced by the offline coach's metrics-based one.
                    glow = checked.feedback.glow ?: rules.glow.takeIf { checked.fallbackGlow },
                    grow = checked.feedback.grow ?: rules.grow.takeIf { checked.fallbackGrow },
                    wordUpgrades = rules.wordUpgrades,
                    notes = notes.distinct(),
                )
                Outcome.Ok(
                    FeedbackResult(
                        promptVersion = FeedbackPrompt.PROMPT_VERSION,
                        model = r.value.model,
                        feedback = merged,
                        dropped = checked.dropped,
                        fallbackGlow = checked.fallbackGlow,
                        fallbackGrow = checked.fallbackGrow,
                        validatedOnDevice = true,
                    ),
                )
            }
        }
    }

    /** Reports stay on the phone: the vendors never hear about them. */
    override suspend fun report(request: ReportRequest): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult> {
        val words: List<String> = run {
            val stt = sttKey(request.lang)?.let { (provider, key) ->
                when (provider) {
                    ByokProvider.SARVAM -> sarvam.transcribe(key, audio, request.lang)
                    else -> deepgram.transcribe(key, audio, request.lang)
                }
            }
            when {
                stt is ByokResult.Ok -> stt.value.words.map { it.w }.also { recordSttUsage(stt.value) }
                else -> when (val r = transcriber.transcribe(audio, request.lang, request.durationMs)) {
                    is Outcome.Ok -> r.value.map { it.w }
                    is Outcome.Err -> return if (stt is ByokResult.Fail) Outcome.Err(stt.error.toAppError(Stage.STT)) else r
                }
            }
        }
        val basic = LocalAnalysisPipeline.sentenceVerdict(request, words)
        val key = keys.key(ByokProvider.ANTHROPIC) ?: return Outcome.Ok(basic)
        return when (val r = anthropic.checkSentence(key, request.lang.code, request.targetWord, request.meaning, basic.wordFound, basic.transcript)) {
            is ByokResult.Fail -> Outcome.Ok(basic)
            is ByokResult.Ok -> {
                usage.record(inputTokens = r.value.usage.inputTokens, outputTokens = r.value.usage.outputTokens)
                Outcome.Ok(basic.copy(result = r.value.result, corrected = r.value.corrected, example = r.value.example, note = r.value.note))
            }
        }
    }

    /** Sarvam first for Hindi, Deepgram first for English; whichever key exists otherwise. */
    private suspend fun sttKey(lang: Lang): Pair<ByokProvider, String>? {
        val order = if (lang == Lang.HI) listOf(ByokProvider.SARVAM, ByokProvider.DEEPGRAM) else listOf(ByokProvider.DEEPGRAM, ByokProvider.SARVAM)
        for (p in order) keys.key(p)?.let { return p to it }
        return null
    }

    private suspend fun localTranscribe(request: TranscribeRequest, audio: File, notes: List<AnalysisNote>): Outcome<TranscribeResult> {
        val r = local.transcribe(request, audio)
        if (r is Outcome.Ok && notes.isNotEmpty()) pendingNotes[r.value.serverSessionId] = notes
        return r
    }

    private suspend fun recordStt(sessionId: String, stt: SttResult) = recordSttUsage(stt, countOnce(sessionId))

    private suspend fun recordSttUsage(stt: SttResult, talks: Int = 0) = when (stt.provider) {
        SarvamClient.PROVIDER -> usage.record(talks = talks, sarvamMs = stt.audioMs)
        else -> usage.record(talks = talks, deepgramMs = stt.audioMs)
    }

    private fun countOnce(sessionId: String): Int = if (counted.add(sessionId)) 1 else 0
}
