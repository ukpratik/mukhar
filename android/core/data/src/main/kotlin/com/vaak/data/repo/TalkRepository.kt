package com.vaak.data.repo

import com.vaak.data.audio.AdtsWriter
import com.vaak.data.audio.RecordingFiles
import com.vaak.data.audio.RecordingResult
import com.vaak.data.audio.StorageInfo
import com.vaak.data.content.PromptRepository
import com.vaak.data.db.CardEntity
import com.vaak.data.db.FeedbackEntity
import com.vaak.data.db.MetricsEntity
import com.vaak.data.db.TalkDao
import com.vaak.data.db.TalkSessionEntity
import com.vaak.data.db.TranscriptEntity
import com.vaak.data.db.WordDao
import com.vaak.data.net.AppError
import com.vaak.data.net.Outcome
import com.vaak.data.net.errorCode
import com.vaak.data.net.isTerminal
import com.vaak.data.net.toDomain
import com.vaak.data.net.toDto
import com.vaak.data.net.FeedbackDto
import com.vaak.data.net.WordDto
import com.vaak.data.net.Stage
import com.vaak.data.prefs.UserSettings
import com.vaak.data.util.IdGenerator
import com.vaak.data.util.VaakLog
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.AnalysisScheduler
import com.vaak.data.work.FeedbackRequest
import com.vaak.data.work.ReportRequest
import com.vaak.data.work.TranscribeRequest
import com.vaak.domain.Clock
import com.vaak.domain.feedback.GroundingValidator
import com.vaak.domain.feedback.UpgradedWord
import com.vaak.domain.metrics.MetricAverages
import com.vaak.domain.metrics.MetricsCalculator
import com.vaak.domain.metrics.WpmBand
import com.vaak.domain.model.Lang
import com.vaak.domain.model.Word
import com.vaak.domain.talk.StorageLevel
import com.vaak.domain.talk.StoragePolicy
import com.vaak.domain.talk.TalkThresholds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Talk sessions end to end (HLD §5.1, US-011 to US-018, US-072). The talk row is written when recording starts, so
 * nothing is ever lost silently; analysis claims a Room lease so the foreground path and [AnalysisWorker] never run
 * the same talk twice. Metrics are computed on the device as soon as the transcript arrives (progressive reveal),
 * and the coach's notes are re-validated against the transcript before they're stored (defence in depth, LLD §4.4).
 */
class TalkRepository(
    private val talkDao: TalkDao,
    private val wordDao: WordDao,
    private val habits: HabitRepository,
    private val settings: UserSettings,
    private val pipeline: AnalysisPipeline,
    private val prompts: PromptRepository,
    private val scheduler: AnalysisScheduler,
    private val files: RecordingFiles,
    private val storage: StorageInfo,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val json: Json,
    private val appScope: CoroutineScope,
    private val wordRepo: WordRepository,
) {
    // ---- Recording ----

    fun storageLevel(): StorageLevel = StoragePolicy.level(storage.availableBytes())
    fun availableBytes(): Long = storage.availableBytes()

    /** Writes the `recording` row before the first sample (crash recovery, US-018). */
    suspend fun startSession(spec: TalkSpec): Pair<String, File> {
        val id = ids.newId()
        val file = files.fileFor(id)
        val now = clock.now().toEpochMilli()
        talkDao.insertSession(
            TalkSessionEntity(
                id = id, kind = spec.kind.key, parentId = spec.parentId, lang = spec.lang.code, track = spec.track.key,
                promptId = spec.promptId, createdAt = now, zoneId = clock.zone().id, durationMs = 0, speechMs = 0,
                snrDb = 0.0, status = TalkStatus.RECORDING, leaseUntil = null, attempts = 0, errorCode = null,
                // A baseline's audio is held until S05's "Keep this recording?" answer ([setAudioKept]).
                audioPath = file.path, audioKept = spec.kind == TalkKind.BASELINE, serverSessionId = null, quotaDay = null,
                updatedAt = now,
            ),
        )
        return id to file
    }

    suspend fun updateProgress(id: String, durationMs: Long, speechMs: Long) =
        talkDao.updateProgress(id, durationMs.toInt(), speechMs.toInt(), clock.now().toEpochMilli())

    /**
     * Stop (US-011, US-018, US-031): under 10 s of speech → discarded with no call and no credit; otherwise saved as
     * `recorded`, and ≥ 30 s credits the streak day now. [replaces] is a recovered talk the user chose to re-record.
     */
    suspend fun finishRecording(id: String, result: RecordingResult, replaces: String? = null): StopOutcome {
        val session = talkDao.session(id) ?: return StopOutcome.TooShort(result.speechMs)
        val now = clock.now()
        val base = session.copy(
            durationMs = result.durationMs.toInt(), speechMs = result.speechMs.toInt(), snrDb = result.snrDb ?: 0.0,
            updatedAt = now.toEpochMilli(),
        )
        if (result.speechMs < TalkThresholds.MIN_ANALYSE_SPEECH_MS) {
            result.file.delete()
            talkDao.updateSession(base.copy(status = TalkStatus.DISCARDED, audioPath = null))
            return StopOutcome.TooShort(result.speechMs)
        }
        talkDao.updateSession(base.copy(status = TalkStatus.RECORDED))
        val credit = habits.creditTalk(now, clock.zone(), result.speechMs)
        if (replaces != null) discard(replaces)
        return StopOutcome.Saved(id, credit)
    }

    /** Enqueues the queue job (the fallback) and runs the foreground path now (LLD §4.9). */
    fun startAnalysis(id: String) {
        scheduler.enqueue(id)
        appScope.launch { analyzeForeground(id) }
    }

    /** [startAnalysis], awaited: the baseline (S05) waits on the analysing view for its result. */
    suspend fun analyzeNow(id: String): AnalysisRun {
        scheduler.enqueue(id)
        return analyzeForeground(id)
    }

    private suspend fun analyzeForeground(id: String): AnalysisRun {
        val run = analyze(id)
        if (run is AnalysisRun.RetryAfter) scheduler.enqueueAfter(id, run.delayMs)
        return run
    }

    /** "Get feedback on this part" on a recovered talk (S09 state 8). */
    suspend fun analyzeRecovered(id: String) {
        val s = talkDao.session(id) ?: return
        talkDao.updateSession(s.copy(errorCode = null, updatedAt = clock.now().toEpochMilli()))
        startAnalysis(id)
    }

    /**
     * The keep-audio answer (US-003, off by default). `false` deletes the audio now, or right after the analysis
     * when it still needs the file (an offline-queued baseline); the numbers stay.
     */
    suspend fun setAudioKept(id: String, keep: Boolean) {
        val s = talkDao.session(id) ?: return
        val stillNeeded = !keep && s.status in TalkStatus.PENDING && talkDao.transcript(id) == null
        if (!keep && !stillNeeded) s.audioPath?.let { File(it).delete() }
        talkDao.updateSession(
            s.copy(
                audioKept = keep && s.audioPath != null,
                audioPath = if (keep || stillNeeded) s.audioPath else null,
                updatedAt = clock.now().toEpochMilli(),
            ),
        )
    }

    /**
     * A baseline's audio is held until S05's "Keep this recording?" answer. If the app died or the user left before
     * answering, the default (off) applies: the held audio is deleted (US-003, D-010). Runs at process start.
     */
    suspend fun releaseUnansweredBaselineAudio() {
        if (settings.currentPrefs().keepBaselineAudio) return
        for (s in talkDao.sessionsWithStatus(ALL_STATUSES)) {
            if (s.kind == TalkKind.BASELINE.key && s.audioKept && s.errorCode != TalkCodes.RECOVERED) setAudioKept(s.id, false)
        }
    }

    /** Recovered baseline (S09 state 8 for a baseline): clear the recovered mark and analyse it as the baseline now. */
    suspend fun analyzeRecoveredNow(id: String): AnalysisRun {
        val s = talkDao.session(id) ?: return AnalysisRun.Gone
        talkDao.updateSession(s.copy(errorCode = null, updatedAt = clock.now().toEpochMilli()))
        return analyzeNow(id)
    }

    /** User-confirmed discard: the row stays as `discarded` (history), the audio is deleted. */
    suspend fun discard(id: String) {
        val s = talkDao.session(id) ?: return
        s.audioPath?.let { File(it).delete() }
        scheduler.cancel(id)
        talkDao.updateSession(
            s.copy(status = TalkStatus.DISCARDED, audioPath = null, leaseUntil = null, updatedAt = clock.now().toEpochMilli()),
        )
    }

    // ---- Analysis (foreground path and AnalysisWorker) ----

    suspend fun analyze(id: String): AnalysisRun {
        val before = talkDao.session(id) ?: return AnalysisRun.Gone
        if (before.errorCode == TalkCodes.RECOVERED) return AnalysisRun.Done // waits for the user (S09 state 8)
        val now = clock.now().toEpochMilli()
        if (talkDao.claimLease(id, now, LEASE_MS) == 0) {
            val current = talkDao.session(id) ?: return AnalysisRun.Gone
            return if (current.status == TalkStatus.UPLOADING) AnalysisRun.LeaseHeld else AnalysisRun.Done
        }
        val prefs = settings.currentPrefs()
        val wasQueued = before.status == TalkStatus.QUEUED || before.errorCode != null
        val lang = Lang.fromCode(before.lang)

        val words: List<Word> = talkDao.transcript(id)?.let(::parseWords) ?: run {
            val audio = before.audioPath?.let(::File)
            if (audio == null || !audio.exists()) {
                update(id) { it.copy(status = TalkStatus.FAILED, errorCode = TalkCodes.AUDIO_MISSING, leaseUntil = null) }
                return AnalysisRun.Done
            }
            val parentServerId = before.parentId?.let { talkDao.session(it)?.serverSessionId }
            val request = TranscribeRequest(id, before.kind, parentServerId, lang, before.durationMs.toLong(), wasQueued)
            when (val r = pipeline.transcribe(request, audio)) {
                is Outcome.Err -> return onError(id, r.error, Stage.STT)
                is Outcome.Ok -> {
                    val t = r.value
                    val at = clock.now().toEpochMilli()
                    talkDao.upsertTranscript(TranscriptEntity(id, encodeWords(t.words), t.provider, t.model, at))
                    val metrics = MetricsCalculator.compute(t.words, lang, prefs.stutterFriendly)
                    talkDao.upsertMetrics(
                        MetricsEntity(
                            sessionId = id, wpm = metrics.wpm, wpmBand = metrics.wpmBand.key,
                            lexicalTokens = metrics.lexicalTokens, fillerCount = metrics.fillerCount,
                            fillersPerMin = metrics.fillersPerMin, longPauses = metrics.longPauses,
                            longPausesPerMin = metrics.longPausesPerMin, repetitions = metrics.repetitions,
                            mattr = metrics.mattr, spanMs = metrics.spanMs.toInt(), algoVersion = metrics.algoVersion,
                        ),
                    )
                    update(id) { it.copy(status = TalkStatus.TRANSCRIBED, serverSessionId = t.serverSessionId, errorCode = null) }
                    t.words
                }
            }
        }
        return requestFeedback(id, words, lang, prefs.stutterFriendly)
    }

    private suspend fun requestFeedback(id: String, words: List<Word>, lang: Lang, stutterFriendly: Boolean): AnalysisRun {
        val s = talkDao.session(id) ?: return AnalysisRun.Gone
        val serverId = s.serverSessionId ?: return onError(id, AppError.Unknown("no_server_session"), Stage.LLM)
        val lowConf = lowConfidence(words)
        val metrics = MetricsCalculator.compute(words, lang, stutterFriendly)
        val prompt = prompts.byId(s.promptId)?.text ?: if (s.kind == TalkKind.BASELINE.key) BASELINE_PROMPT else ""
        val request = FeedbackRequest(serverId, lang, s.track, prompt, stutterFriendly, words, lowConf, metrics, s.speechMs.toLong())
        var outcome = pipeline.feedback(request)
        // Two silent automatic retries for an LLM failure before the user sees "Try again" (S10 state 4 notes).
        var retries = 0
        while (outcome is Outcome.Err && outcome.error is AppError.Upstream && retries < SILENT_LLM_RETRIES) {
            retries++
            delay(SILENT_RETRY_DELAY_MS * retries)
            outcome = pipeline.feedback(request)
        }
        return when (outcome) {
            is Outcome.Err -> onError(id, outcome.error, Stage.LLM)
            is Outcome.Ok -> {
                val r = outcome.value
                val checked = GroundingValidator.validate(r.feedback, words.map { it.w }, lowConf, stutterFriendly)
                if (checked.dropped > 0) VaakLog.w("grounding: client dropped ${checked.dropped} item(s)")
                talkDao.upsertFeedback(
                    FeedbackEntity(
                        sessionId = id,
                        json = json.encodeToString(FeedbackDto.serializer(), checked.feedback.toDto()),
                        promptVersion = r.promptVersion,
                        model = r.model,
                        dropped = r.dropped + checked.dropped,
                        fallbackGlow = r.fallbackGlow || checked.fallbackGlow,
                        fallbackGrow = r.fallbackGrow || checked.fallbackGrow,
                        safety = checked.feedback.safety.key,
                        rating = null,
                        reported = false,
                        createdAt = clock.now().toEpochMilli(),
                    ),
                )
                // Mistake → fix cards for revision (US-022, US-040).
                wordRepo.harvestMistakes(id, lang, checked.feedback.mistakes)
                // Audio is deleted after analysis unless the user chose to keep it (PRD, S09 "Private by default").
                // Re-read the row: the keep answer may have changed while the notes were being written.
                val fresh = talkDao.session(id) ?: s
                val audio = fresh.audioPath
                if (audio != null && !fresh.audioKept) File(audio).delete()
                update(id) {
                    it.copy(
                        status = TalkStatus.FEEDBACK_READY, errorCode = null, leaseUntil = null,
                        audioPath = if (fresh.audioKept) audio else null,
                    )
                }
                AnalysisRun.Done
            }
        }
    }

    /** Error policy (LLD §6, HLD §8.1). Nothing here deletes the recording. */
    private suspend fun onError(id: String, error: AppError, stage: Stage): AnalysisRun {
        VaakLog.i("analysis: ${stage.name} ${error.errorCode}")
        val afterTranscript = stage == Stage.LLM
        return when {
            error is AppError.Offline -> {
                update(id) { it.copy(status = if (afterTranscript) TalkStatus.FEEDBACK_FAILED else TalkStatus.QUEUED, errorCode = error.errorCode, leaseUntil = null) }
                AnalysisRun.Retry
            }
            error is AppError.Busy -> {
                update(id) { it.copy(status = if (afterTranscript) TalkStatus.FEEDBACK_FAILED else TalkStatus.QUEUED, errorCode = error.errorCode, leaseUntil = null) }
                AnalysisRun.RetryAfter((error.retryAfterS?.times(1000)) ?: BUSY_RETRY_MS)
            }
            error is AppError.Quota -> {
                update(id) { it.copy(status = TalkStatus.QUEUED, errorCode = error.errorCode, leaseUntil = null) }
                val wait = error.resetsAtMs?.let { it - clock.now().toEpochMilli() }?.coerceAtLeast(MIN_RETRY_MS)
                AnalysisRun.RetryAfter(wait ?: BUSY_RETRY_MS)
            }
            error.isTerminal -> {
                update(id) { it.copy(status = TalkStatus.FAILED, errorCode = error.errorCode, leaseUntil = null) }
                AnalysisRun.Done
            }
            afterTranscript -> {
                // Transcript and metrics stay; "Try again" re-requests only the notes (S10 state 4).
                update(id) { it.copy(status = TalkStatus.FEEDBACK_FAILED, errorCode = error.errorCode, leaseUntil = null) }
                AnalysisRun.Done
            }
            else -> {
                // STT failed: the recording stays; the queue retries with backoff (S10 state 5).
                update(id) { it.copy(status = TalkStatus.FAILED, errorCode = error.errorCode, leaseUntil = null) }
                AnalysisRun.Retry
            }
        }
    }

    // ---- App start ----

    /**
     * Crash recovery and queue resume (US-018, US-072). A `recording` row means the process died mid-talk: the ADTS
     * file is trimmed to its last whole frame and offered to the user; ≥ 30 s of speech credits the day it was
     * recorded. Unfinished analyses are re-enqueued.
     */
    suspend fun recoverOnStart() {
        for (s in talkDao.sessionsWithStatus(listOf(TalkStatus.RECORDING))) {
            val file = s.audioPath?.let(::File)
            val frames = file?.let { runCatching { AdtsWriter.truncateToLastFrame(it) }.getOrDefault(0L) } ?: 0L
            if (file == null || frames == 0L) {
                file?.delete()
                update(s.id) { it.copy(status = TalkStatus.DISCARDED, audioPath = null) }
                continue
            }
            val duration = maxOf(s.durationMs.toLong(), AdtsWriter.durationMs(frames))
            update(s.id) { it.copy(status = TalkStatus.RECORDED, errorCode = TalkCodes.RECOVERED, durationMs = duration.toInt()) }
            habits.creditTalk(Instant.ofEpochMilli(s.createdAt + duration), ZoneId.of(s.zoneId), s.speechMs.toLong())
        }
        for (s in talkDao.sessionsWithStatus(TalkStatus.PENDING)) {
            if (s.errorCode == TalkCodes.RECOVERED || s.errorCode in NO_AUTO_RETRY) continue
            scheduler.enqueue(s.id)
        }
        releaseUnansweredBaselineAudio()
    }

    fun observeRecovered(): Flow<TalkSessionEntity?> = talkDao.observeRecovered()

    // ---- Reading ----

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTalk(id: String): Flow<TalkDetails?> = talkDao.observeSession(id).flatMapLatest { session ->
        if (session == null) {
            flowOf(null)
        } else {
            val stopAt = Instant.ofEpochMilli(session.createdAt + session.durationMs)
            combine(
                talkDao.observeTranscript(id),
                talkDao.observeMetrics(id),
                talkDao.observeFeedback(id),
                habits.observeCredit(stopAt, ZoneId.of(session.zoneId)),
            ) { t, m, f, credit ->
                TalkDetails(session, t?.let(::parseWords), m, f?.let(::parseFeedback), credit)
            }
        }
    }

    suspend fun session(id: String): TalkSessionEntity? = talkDao.session(id)
    suspend fun latestRetake(parentId: String): TalkSessionEntity? = talkDao.latestRetake(parentId)

    /** The previous 5 talks in the same language (US-013: never other users, never the other language). */
    suspend fun previousAverages(lang: Lang, before: Long): MetricAverages? {
        val rows = talkDao.recentMetrics(lang.code, before, 5)
        if (rows.isEmpty()) return null
        fun avg(v: List<Double>) = if (v.isEmpty()) null else v.average()
        return MetricAverages(
            talks = rows.size,
            wpm = rows.map { it.wpm }.average(),
            fillersPerMin = rows.map { it.fillersPerMin }.average(),
            longPausesPerMin = avg(rows.mapNotNull { it.longPausesPerMin }),
            repetitions = avg(rows.mapNotNull { it.repetitions?.toDouble() }),
            mattr = avg(rows.mapNotNull { it.mattr }),
        )
    }

    // ---- Results actions ----

    suspend fun rate(id: String, value: Int) = talkDao.rateFeedback(id, value.coerceIn(1, 3))

    /** US-016: the transcript and notes are sent only with [consent]. */
    suspend fun report(id: String, reason: String, consent: Boolean, note: String?): Outcome<Unit> {
        val s = talkDao.session(id) ?: return Outcome.Err(AppError.Unknown("no_session"))
        val serverId = s.serverSessionId ?: return Outcome.Err(AppError.Unknown("no_server_session"))
        val words = talkDao.transcript(id)?.let(::parseWords).orEmpty()
        val feedback = talkDao.feedback(id)
        val r = pipeline.report(
            ReportRequest(
                serverSessionId = serverId, reason = reason, consent = consent, lang = Lang.fromCode(s.lang),
                promptVersion = feedback?.promptVersion, words = if (consent) words else emptyList(),
                lowConf = lowConfidence(words), feedback = if (consent) feedback?.let(::parseFeedback)?.feedback else null,
                note = note?.trim()?.takeIf { consent && it.isNotEmpty() },
            ),
        )
        if (r is Outcome.Ok) talkDao.markReported(id)
        return r
    }

    fun observeCollectedWords(sessionId: String): Flow<List<String>> = wordDao.observeTalkWordIds(sessionId)
    fun observeSavedLines(sessionId: String): Flow<List<String>> = wordDao.observeSavedLineIds(sessionId)

    /**
     * Tapping an upgraded word adds it to the Word Gym with its cards (US-015/021, deduplicated, ≤ 5 new a day).
     * Tapping a word this talk added removes it again (Undo) and returns null.
     */
    suspend fun toggleWord(sessionId: String, lang: Lang, track: String, word: UpgradedWord, example: String): HarvestResult? {
        if (wordId(lang, word.word) in wordDao.talkWordIds(sessionId)) {
            wordRepo.unharvest(sessionId, lang, word.word)
            return null
        }
        return wordRepo.harvest(sessionId, lang, track, word, example)
    }

    /** "Save line" adds the better version to revision as a new self-graded card (US-015/040). */
    suspend fun toggleSavedLine(sessionId: String, lang: Lang, index: Int, original: String, better: String): Boolean {
        val id = "line:$sessionId:$index"
        if (id in wordDao.savedLineIds(sessionId)) {
            wordDao.deleteCard(id)
            return false
        }
        val now = clock.now().toEpochMilli()
        wordDao.upsertCard(
            CardEntity(
                id = id, type = "saved_line", wordId = null, sessionId = sessionId, front = original, back = better,
                lang = lang.code, due = now, stability = null, difficulty = null, elapsedDays = 0, scheduledDays = 0,
                reps = 0, lapses = 0, state = 0, step = 0, lastReview = null, createdAt = now, suspended = false,
            ),
        )
        return true
    }

    // ---- helpers ----

    private suspend fun update(id: String, f: (TalkSessionEntity) -> TalkSessionEntity) {
        val s = talkDao.session(id) ?: return
        talkDao.updateSession(f(s).copy(updatedAt = clock.now().toEpochMilli()))
    }

    private fun parseWords(t: TranscriptEntity): List<Word> =
        json.decodeFromString(ListSerializer(WordDto.serializer()), t.tokensJson).map { it.toDomain() }

    private fun encodeWords(words: List<Word>): String =
        json.encodeToString(ListSerializer(WordDto.serializer()), words.map { it.toDto() })

    private fun parseFeedback(f: FeedbackEntity) = StoredFeedback(
        feedback = json.decodeFromString(FeedbackDto.serializer(), f.json).toDomain(),
        promptVersion = f.promptVersion,
        dropped = f.dropped,
        fallbackGlow = f.fallbackGlow,
        fallbackGrow = f.fallbackGrow,
        rating = f.rating,
        reported = f.reported,
    )

    companion object {
        const val LEASE_MS = 90_000L
        const val BUSY_RETRY_MS = 30 * 60_000L
        const val MIN_RETRY_MS = 60_000L
        const val SILENT_LLM_RETRIES = 2
        const val SILENT_RETRY_DELAY_MS = 1_000L
        private val ALL_STATUSES = listOf(
            TalkStatus.RECORDED, TalkStatus.QUEUED, TalkStatus.UPLOADING, TalkStatus.TRANSCRIBED, TalkStatus.FEEDBACK_READY,
            TalkStatus.FEEDBACK_FAILED, TalkStatus.FAILED,
        )
        private val NO_AUTO_RETRY = setOf("TOO_LITTLE_SPEECH", "AUDIO_TOO_LONG", TalkCodes.AUDIO_MISSING, "LLM_FAILED", "BASELINE_USED")

        /** The S05 prompt (not in `prompts.json`; the baseline has one fixed prompt). */
        const val BASELINE_PROMPT = "Tell us who you are, and one thing you'd like to get better at when you speak."

        fun lowConfidence(words: List<Word>): Set<Int> =
            words.indices.filter { words[it].conf < MetricsCalculator.LOW_CONF }.toSet()

        fun wordId(lang: Lang, word: String): String = WordRepository.wordId(lang, word)

        fun bandLimits(lang: Lang): Pair<Double, Double> = if (lang == Lang.HI) 100.0 to 150.0 else 110.0 to 160.0

        fun band(key: String): WpmBand = WpmBand.entries.firstOrNull { it.key == key } ?: WpmBand.COMFORTABLE
    }
}
