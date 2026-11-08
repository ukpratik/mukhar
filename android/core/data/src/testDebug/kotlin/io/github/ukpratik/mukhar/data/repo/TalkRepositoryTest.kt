package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.tempSettingsStore
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.audio.AdtsWriter
import io.github.ukpratik.mukhar.data.audio.RecordingFiles
import io.github.ukpratik.mukhar.data.audio.RecordingResult
import io.github.ukpratik.mukhar.data.content.PromptRepository
import io.github.ukpratik.mukhar.data.db.TalkSessionEntity
import io.github.ukpratik.mukhar.data.db.MukharDatabase
import io.github.ukpratik.mukhar.data.debug.CannedTalks
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.Stage
import io.github.ukpratik.mukhar.data.work.FeedbackResult
import io.github.ukpratik.mukhar.data.work.TranscribeResult
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.feedback.Glow
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.domain.talk.StorageLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

/** Talk session state transitions, the offline queue and "no recording lost" (US-011, US-018, US-072) on Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TalkRepositoryTest {
    private lateinit var db: MukharDatabase
    private val clock = FixedClock(Instant.parse("2026-11-02T04:30:00Z"), ZoneId.of("Asia/Kolkata")) // 10:00 IST
    private val pipeline = ScriptedPipeline()
    private val scheduler = RecordingScheduler()
    private val settings = MemorySettings()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir: File = Files.createTempDirectory("mukhar").toFile()
    private var freeBytes = 500L * 1024 * 1024
    private var nextId = 0
    private lateinit var repo: TalkRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MukharDatabase::class.java)
            .allowMainThreadQueries().build()
        val prompts = PromptRepository({ File("src/main/assets/prompts.json").readText() }, db.habitDao(), clock, json)
        repo = TalkRepository(
            db.talkDao(), db.wordDao(), HabitRepository(db.habitDao(), tempSettingsStore(), clock), settings, pipeline, prompts, scheduler,
            RecordingFiles(dir), { freeBytes }, clock, { "talk-${++nextId}" }, json, TestScope(),
            testWordRepository(db, clock),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun recorded(speechMs: Long = 45_000, lang: Lang = Lang.EN): String {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.TALK, lang, Track.PROFESSIONAL, "en-002"))
        file.writeBytes(ByteArray(128))
        val out = repo.finishRecording(id, RecordingResult(file, 60_000, speechMs, 25.0, false))
        assertThat(out).isInstanceOf(StopOutcome.Saved::class.java)
        return id
    }

    private fun transcript(lang: Lang = Lang.EN) =
        Outcome.Ok(TranscribeResult("srv-1", "fake", "canned", 60_000, CannedTalks.words(lang)))

    private fun cannedFeedback(lang: Lang = Lang.EN): (io.github.ukpratik.mukhar.data.work.FeedbackRequest) -> Outcome<FeedbackResult> = { req ->
        Outcome.Ok(FeedbackResult("fb-1", "haiku", CannedTalks.feedback(lang, req.words.map { it.w }), 0, false, false))
    }

    private suspend fun status(id: String) = db.talkDao().session(id)!!

    @Test
    fun recordingRowIsWrittenBeforeTheFirstSample() = runTest {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.TALK, Lang.EN, Track.EVERYDAY, "en-017"))
        assertThat(status(id).status).isEqualTo(TalkStatus.RECORDING)
        assertThat(status(id).audioPath).isEqualTo(file.path)
        repo.updateProgress(id, 12_000, 9_000)
        assertThat(status(id).speechMs).isEqualTo(9_000)
    }

    @Test
    fun underTenSecondsOfSpeechIsDiscardedWithNoCallAndNoCredit() = runTest {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.TALK, Lang.EN, Track.EVERYDAY, "en-017"))
        file.writeBytes(ByteArray(10))
        val out = repo.finishRecording(id, RecordingResult(file, 20_000, 6_000, 20.0, false))
        assertThat(out).isEqualTo(StopOutcome.TooShort(6_000))
        assertThat(status(id).status).isEqualTo(TalkStatus.DISCARDED)
        assertThat(file.exists()).isFalse()
        assertThat(db.habitDao().streakDays()).isEmpty()
        assertThat(scheduler.enqueued).isEmpty()
    }

    @Test
    fun thirtySecondsOfSpeechCreditsTheStreakAtStop() = runTest {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.TALK, Lang.EN, Track.EVERYDAY, "en-017"))
        val out = repo.finishRecording(id, RecordingResult(file, 60_000, 31_000, 20.0, false)) as StopOutcome.Saved
        assertThat(out.credit!!.day.toString()).isEqualTo("2026-11-02")
        assertThat(out.credit!!.streak).isEqualTo(1)
        assertThat(status(id).status).isEqualTo(TalkStatus.RECORDED)

        val (id2, file2) = repo.startSession(TalkSpec(TalkKind.TALK, Lang.EN, Track.EVERYDAY, "en-018"))
        val second = repo.finishRecording(id2, RecordingResult(file2, 30_000, 20_000, 20.0, false)) as StopOutcome.Saved
        assertThat(second.credit).isNull() // 10–30 s: analysed but no credit
    }

    @Test
    fun happyPathStoresTranscriptMetricsAndRevalidatedNotes() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)

        val s = status(id)
        assertThat(s.status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(s.serverSessionId).isEqualTo("srv-1")
        assertThat(s.leaseUntil).isNull()
        assertThat(s.audioPath).isNull() // audio deleted after analysis
        assertThat(File(dir, "recordings/$id.aac").exists()).isFalse()
        val metrics = db.talkDao().metrics(id)!!
        assertThat(metrics.fillerCount).isAtLeast(4)
        assertThat(metrics.mattr).isNotNull()

        val details = repo.observeTalk(id).first()!!
        val fb = details.feedback!!
        assertThat(fb.feedback.glow).isNotNull()
        assertThat(fb.feedback.grow).isNotNull()
        assertThat(fb.feedback.betterVersions).hasSize(3)
        // The canned notes quote the low-confidence "Zuora": the client validator drops that mistake.
        assertThat(fb.feedback.mistakes.map { it.fix }).containsExactly("from day one")
        assertThat(fb.dropped).isEqualTo(1)
        assertThat(details.credit!!.streak).isEqualTo(1)
        assertThat(pipeline.feedbackCalls.single().prompt).startsWith("Tell us about a project")
    }

    @Test
    fun offlineAtStopIsQueuedThenAnalysedWithoutLosingTheRecording() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.Offline)
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Retry)
        assertThat(status(id).status).isEqualTo(TalkStatus.QUEUED)
        assertThat(File(status(id).audioPath!!).exists()).isTrue()

        pipeline.transcribeAnswers.clear()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        assertThat(pipeline.transcribeCalls.last().queued).isTrue()
        assertThat(status(id).status).isEqualTo(TalkStatus.FEEDBACK_READY)
    }

    @Test
    fun busyStaysQueuedAndRetriesIn30Minutes() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.Busy(null))
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.RetryAfter(30 * 60_000L))
        assertThat(status(id).status).isEqualTo(TalkStatus.QUEUED)
        assertThat(status(id).errorCode).isEqualTo("BUSY")
    }

    @Test
    fun quotaWaitsUntilTheReset() = runTest {
        val id = recorded()
        val reset = clock.now().toEpochMilli() + 3_600_000
        pipeline.transcribeAnswers += Outcome.Err(AppError.Quota(reset))
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.RetryAfter(3_600_000))
        assertThat(status(id).status).isEqualTo(TalkStatus.QUEUED)
    }

    @Test
    fun sttFailureKeepsTheRecordingAndRetries() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.Upstream(Stage.STT, "STT_FAILED"))
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Retry)
        val s = status(id)
        assertThat(s.status).isEqualTo(TalkStatus.FAILED)
        assertThat(s.errorCode).isEqualTo("STT_FAILED")
        assertThat(File(s.audioPath!!).exists()).isTrue()
    }

    @Test
    fun tooLittleSpeechOnTheServerIsTerminal() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.TooLittleSpeech)
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        assertThat(status(id).errorCode).isEqualTo("TOO_LITTLE_SPEECH")
    }

    @Test
    fun llmFailureKeepsTranscriptAndMetricsAndRetryOnlyAsksForNotes() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += { Outcome.Err(AppError.Upstream(Stage.LLM, "LLM_FAILED")) }
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        assertThat(pipeline.feedbackCalls).hasSize(3) // 1 + 2 silent retries
        val s = status(id)
        assertThat(s.status).isEqualTo(TalkStatus.FEEDBACK_FAILED)
        assertThat(db.talkDao().metrics(id)).isNotNull()
        assertThat(db.talkDao().transcript(id)).isNotNull()

        pipeline.feedbackAnswers.clear()
        pipeline.feedbackAnswers += cannedFeedback()
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        assertThat(pipeline.transcribeCalls).hasSize(1) // STT isn't paid for twice
        assertThat(status(id).status).isEqualTo(TalkStatus.FEEDBACK_READY)
    }

    @Test
    fun feedbackOfflineIsRetriedByTheQueue() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += { Outcome.Err(AppError.Offline) }
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Retry)
        assertThat(status(id).status).isEqualTo(TalkStatus.FEEDBACK_FAILED)
        assertThat(status(id).errorCode).isEqualTo("OFFLINE")
    }

    @Test
    fun ungroundedGlowFallsBackAndIsCounted() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += {
            Outcome.Ok(FeedbackResult("fb-1", "haiku", Feedback("s", Glow(0, 3, "words nobody said", "n"), null, emptyList(), emptyList(), Safety.NONE), 0, false, true))
        }
        repo.analyze(id)
        val fb = repo.observeTalk(id).first()!!.feedback!!
        assertThat(fb.feedback.glow).isNull()
        assertThat(fb.fallbackGlow).isTrue()
        assertThat(fb.fallbackGrow).isTrue() // the server's flag is kept
        assertThat(fb.dropped).isEqualTo(1)
    }

    @Test
    fun onlyOnePathWinsTheLease() = runTest {
        val id = recorded()
        assertThat(db.talkDao().claimLease(id, clock.now().toEpochMilli(), 90_000)).isEqualTo(1)
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.LeaseHeld)
        assertThat(pipeline.transcribeCalls).isEmpty()
        clock.advanceBy(java.time.Duration.ofSeconds(91)) // a killed upload's lease expires
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done) // already finished: nothing to do
        assertThat(pipeline.transcribeCalls).hasSize(1)
    }

    @Test
    fun crashMidRecordingIsRecoveredCreditedAndOffered() = runTest {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.TALK, Lang.EN, Track.EVERYDAY, "en-017"))
        AdtsWriter(file).use { w -> repeat(1_000) { w.writeFrame(ByteArray(40)) } } // 64 s of frames
        file.appendBytes(AdtsWriter.header(40) + ByteArray(10)) // half a frame when the process died
        repo.updateProgress(id, 60_000, 40_000)
        val queued = recorded()
        db.talkDao().updateStatus(queued, TalkStatus.QUEUED, 0)

        repo.recoverOnStart()

        val s = status(id)
        assertThat(s.status).isEqualTo(TalkStatus.RECORDED)
        assertThat(s.errorCode).isEqualTo(TalkCodes.RECOVERED)
        assertThat(s.durationMs).isEqualTo(64_000)
        assertThat(file.length()).isEqualTo(1_000L * 47)
        assertThat(repo.observeRecovered().first()!!.id).isEqualTo(id)
        // Today's recovered card reads the same row (S07); it isn't also counted as waiting.
        val today = io.github.ukpratik.mukhar.data.today.RoomTalkStatusSource(db, db.talkDao())
        assertThat(today.recovered.first()!!.sessionId).isEqualTo(id)
        assertThat(today.recovered.first()!!.durationMs).isEqualTo(64_000)
        assertThat(today.waiting.first()!!.count).isEqualTo(1) // only the queued talk
        assertThat(scheduler.enqueued).containsExactly(queued) // the recovered talk waits for the user
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        assertThat(pipeline.transcribeCalls).isEmpty()

        repo.analyzeRecovered(id)
        assertThat(status(id).errorCode).isNull()
        assertThat(scheduler.enqueued).contains(id)
    }

    @Test
    fun recordingRowWithoutAudioIsDiscardedOnStart() = runTest {
        val (id, _) = repo.startSession(TalkSpec(TalkKind.TALK, Lang.EN, Track.EVERYDAY, "en-017"))
        repo.recoverOnStart()
        assertThat(status(id).status).isEqualTo(TalkStatus.DISCARDED)
    }

    @Test
    fun discardDeletesAudioAndCancelsTheJob() = runTest {
        val id = recorded()
        val audio = File(status(id).audioPath!!)
        repo.discard(id)
        assertThat(status(id).status).isEqualTo(TalkStatus.DISCARDED)
        assertThat(audio.exists()).isFalse()
        assertThat(scheduler.cancelled).containsExactly(id)
    }

    @Test
    fun lowStorageLevels() {
        freeBytes = 38L * 1024 * 1024
        assertThat(repo.storageLevel()).isEqualTo(StorageLevel.LOW)
        freeBytes = 6L * 1024 * 1024
        assertThat(repo.storageLevel()).isEqualTo(StorageLevel.BLOCKED)
    }

    @Test
    fun wordsAndLinesAreCollectedAndUndone() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyze(id)
        val bv = repo.observeTalk(id).first()!!.feedback!!.feedback.betterVersions.first()
        val word = bv.upgradedWords.first()
        assertThat(repo.toggleWord(id, Lang.EN, "professional", word, bv.rewrite)).isInstanceOf(HarvestResult.Added::class.java)
        assertThat(repo.observeCollectedWords(id).first()).containsExactly("en:articulate")
        assertThat(db.wordDao().cardsForWord("en:articulate").map { it.type }).containsExactly("word_meaning", "meaning_word")
        assertThat(repo.toggleWord(id, Lang.EN, "professional", word, bv.rewrite)).isNull() // undo
        assertThat(repo.observeCollectedWords(id).first()).isEmpty()
        assertThat(db.wordDao().cardsForWord("en:articulate")).isEmpty()
        assertThat(repo.toggleSavedLine(id, Lang.EN, 0, bv.quote, bv.rewrite)).isTrue()
        assertThat(repo.observeSavedLines(id).first()).containsExactly("line:$id:0")
    }

    @Test
    fun mistakesBecomeFixCardsOnceAndUngroundedOnesAreDropped() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyze(id)
        repo.analyze(id) // a second run doesn't duplicate cards
        val fixes = db.wordDao().reviewCandidates(Long.MAX_VALUE).filter { it.type == "mistake_fix" }
        // The canned notes have 2 mistakes; the one quoting a low-confidence word is dropped by the validator.
        assertThat(fixes.map { it.front to it.back }).containsExactly("from day one itself" to "from day one")
    }

    // --- M4 fixes: baseline audio (US-003, D-010) ---

    @Test
    fun baselineAudioIsHeldUntilAnsweredAndDeletedWhenTheUserLeft() = runTest {
        val (id, _) = baseline()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyze(id)
        val held = status(id)
        assertThat(held.audioKept).isTrue()
        assertThat(File(held.audioPath!!).exists()).isTrue()
        // App restarted before "Keep this recording?" was answered: the default (off) applies.
        repo.recoverOnStart()
        assertThat(status(id).audioPath).isNull()
        assertThat(status(id).audioKept).isFalse()
        assertThat(File(held.audioPath!!).exists()).isFalse()
    }

    @Test
    fun anExplicitKeepSurvivesRestart() = runTest {
        val (id, _) = baseline()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyze(id)
        settings.state.value = settings.state.value.copy(keepBaselineAudio = true)
        repo.setAudioKept(id, true)
        repo.recoverOnStart()
        assertThat(status(id).audioKept).isTrue()
        assertThat(File(status(id).audioPath!!).exists()).isTrue()
    }

    @Test
    fun noWhileStillQueuedDeletesAfterTheAnalysisNotBefore() = runTest {
        val (id, _) = baseline() // offline: the transcribe answer queue is empty → Offline
        repo.analyze(id)
        assertThat(status(id).status).isEqualTo(TalkStatus.QUEUED)
        repo.setAudioKept(id, false)
        val path = status(id).audioPath!!
        assertThat(File(path).exists()).isTrue() // the queue still needs it
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyze(id)
        assertThat(status(id).status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(File(path).exists()).isFalse()
        assertThat(status(id).audioPath).isNull()
    }

    @Test
    fun recoveredBaselineIsAnalysedAsTheBaseline() = runTest {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.BASELINE, Lang.EN, Track.EVERYDAY, "baseline_intro"))
        AdtsWriter(file).use { w -> repeat(800) { w.writeFrame(ByteArray(40)) } }
        repo.updateProgress(id, 50_000, 40_000)
        repo.recoverOnStart()
        assertThat(status(id).errorCode).isEqualTo(TalkCodes.RECOVERED)
        assertThat(File(status(id).audioPath!!).exists()).isTrue() // not swept: the user hasn't acted on it yet
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyzeRecoveredNow(id)
        assertThat(pipeline.transcribeCalls.last().kind).isEqualTo("baseline")
        assertThat(status(id).status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(status(id).audioKept).isTrue() // held for the keep question
    }

    @Test
    fun reportSendsTheTranscriptOnlyWithConsent() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyze(id)
        repo.report(id, "wrong_advice", consent = false, note = "hidden")
        repo.report(id, "wrong_advice", consent = true, note = "  why  ")
        assertThat(pipeline.reports[0].words).isEmpty()
        assertThat(pipeline.reports[0].feedback).isNull()
        assertThat(pipeline.reports[0].note).isNull()
        assertThat(pipeline.reports[1].words).isNotEmpty()
        assertThat(pipeline.reports[1].note).isEqualTo("why")
        assertThat(db.talkDao().feedback(id)!!.reported).isTrue()
    }

    @Test
    fun promptShuffleRecordsHistoryAndAvoidsRepeats() = runTest {
        val prompts = PromptRepository({ File("src/main/assets/prompts.json").readText() }, db.habitDao(), clock, json)
        val seen = mutableListOf<String>()
        var current: String? = null
        repeat(8) {
            val p = prompts.next(Lang.EN, Track.PROFESSIONAL, current)!!
            assertThat(p.id).isNotIn(seen)
            seen += p.id
            current = p.id
        }
        assertThat(db.habitDao().recentPromptIds(30)).containsExactlyElementsIn(seen.reversed()).inOrder()
    }

    @Suppress("unused")
    private fun TalkSessionEntity.short() = "$id:$status:$errorCode"

    private suspend fun baseline(): Pair<String, File> {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.BASELINE, Lang.EN, Track.EVERYDAY, "baseline_intro"))
        file.writeBytes(ByteArray(128))
        repo.finishRecording(id, RecordingResult(file, 60_000, 45_000, 25.0, false))
        return id to file
    }

    @Test
    fun secondBaselineIsTerminalAndNeverRetried() = runTest {
        val (id, _) = baseline()
        pipeline.transcribeAnswers += Outcome.Err(io.github.ukpratik.mukhar.data.net.AppError.BaselineUsed)
        assertThat(repo.analyzeNow(id)).isEqualTo(AnalysisRun.Done)
        assertThat(pipeline.transcribeCalls.single().kind).isEqualTo("baseline")
        assertThat(status(id).status).isEqualTo(TalkStatus.FAILED)
        assertThat(status(id).errorCode).isEqualTo("BASELINE_USED")
        scheduler.enqueued.clear()
        repo.recoverOnStart()
        assertThat(scheduler.enqueued).doesNotContain(id) // D-031: no retry loop against the 409
    }

    @Test
    fun baselineKeepsItsAudioUntilTheKeepAnswer() = runTest {
        val (id, file) = baseline()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        assertThat(repo.analyzeNow(id)).isEqualTo(AnalysisRun.Done)
        assertThat(pipeline.feedbackCalls.single().prompt).isEqualTo(TalkRepository.BASELINE_PROMPT)
        assertThat(status(id).status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(file.exists()).isTrue() // held for "Keep this recording?" (S05)
        repo.setAudioKept(id, keep = false)
        assertThat(file.exists()).isFalse()
        assertThat(status(id).audioPath).isNull()
        assertThat(status(id).audioKept).isFalse()
    }

    @Test
    fun baselineRecorderRecordsOnTheTalkPipelineAndExplainsA409() = runTest {
        var n = 0L
        var frame = 0
        val source = object : io.github.ukpratik.mukhar.data.audio.PcmSource {
            override fun start() = true
            override suspend fun read(buffer: ShortArray): Int {
                kotlinx.coroutines.delay(20)
                val speaking = frame++ >= 30 // a quiet start sets the noise floor
                for (i in buffer.indices) {
                    buffer[i] = if (speaking) (8000 * kotlin.math.sin(2 * kotlin.math.PI * 220 * n++ / 16_000)).toInt().toShort() else ((i % 7) - 3).toShort()
                }
                return buffer.size
            }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val recorder = io.github.ukpratik.mukhar.data.audio.Recorder(
            { source },
            { f ->
                f.writeBytes(ByteArray(64))
                object : io.github.ukpratik.mukhar.data.audio.AudioSink {
                    override fun write(pcm: ShortArray, length: Int) = Unit
                    override fun finish() = Unit
                    override fun abort() = Unit
                }
            },
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )
        val baseline = io.github.ukpratik.mukhar.data.baseline.TalkBaselineRecorder(recorder, repo, backgroundScope)
        pipeline.transcribeAnswers += Outcome.Err(io.github.ukpratik.mukhar.data.net.AppError.BaselineUsed)

        assertThat(baseline.start(Lang.EN, "baseline_intro")).isTrue()
        advanceTimeBy(15_000)
        assertThat(baseline.progress.value!!.elapsedMs).isAtLeast(14_000)
        assertThat(baseline.stop()).isEqualTo(io.github.ukpratik.mukhar.data.baseline.BaselineResult.AlreadyUsed)
        assertThat(baseline.progress.value).isNull()
        val row = status("talk-1")
        assertThat(row.kind).isEqualTo("baseline")
        assertThat(row.status).isEqualTo(TalkStatus.DISCARDED) // nothing is kept from a refused baseline
        assertThat(row.audioPath).isNull()
    }

    // ---- ADR-008 (2A) ----

    @Test
    fun noEngineKeepsTheTalkSavedWithoutTimedRetriesAndAKeyReRunsIt() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.EngineUnavailable)
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done) // not Retry: nothing to wait for
        val s = status(id)
        assertThat(s.status).isEqualTo(TalkStatus.FAILED)
        assertThat(s.errorCode).isEqualTo("MODEL_NOT_INSTALLED")
        assertThat(File(s.audioPath!!).exists()).isTrue() // the recording stays
        // App start doesn't re-enqueue it on a timer.
        scheduler.enqueued.clear()
        repo.recoverOnStart()
        assertThat(scheduler.enqueued).doesNotContain(id)
        // Adding a key / a model re-runs it through the queue, from the saved audio.
        pipeline.transcribeAnswers.clear()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        assertThat(repo.retryWaitingForEngine()).isEqualTo(1)
        assertThat(scheduler.enqueued).contains(id)
        assertThat(status(id).errorCode).isNull()
    }

    @Test
    fun notesValidatedOnThePhoneAreStoredAsTheyAreWithAverages() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Ok(TranscribeResult("local-1", "vosk", "small", 60_000, CannedTalks.words(Lang.EN)))
        pipeline.feedbackAnswers += { req ->
            // Metrics-based glow (no quote): would be dropped by the grounding validator.
            val f = Feedback("s", Glow(Feedback.NO_SPAN, Feedback.NO_SPAN, "", "Your pace stayed comfortable."), null, emptyList(), emptyList(), Safety.NONE)
            assertThat(req.transcriptProvider).isEqualTo("vosk")
            assertThat(req.durationMs).isEqualTo(60_000)
            Outcome.Ok(FeedbackResult("rule-coach-v1", "rule-coach", f, 0, false, false, validatedOnDevice = true))
        }
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        val details = repo.observeTalk(id).first()!!
        assertThat(details.feedback!!.feedback.glow!!.note).isEqualTo("Your pace stayed comfortable.")
        assertThat(details.feedback!!.model).isEqualTo("rule-coach")
        assertThat(details.transcriptProvider).isEqualTo("vosk")
        assertThat(details.feedback!!.fallbackGlow).isFalse()
    }

    // ---- D-048: voice-only report when no transcript can be written ----

    /** 40 s of syllable-like bursts (4 a second) with 2 s gaps every 10 s, as a decoder would hand them over. */
    private val voiceDecoder = io.github.ukpratik.mukhar.data.audio.PcmDecoder { _, onPcm ->
        val sr = 16_000
        val random = kotlin.random.Random(3)
        repeat(4) {
            val n = 10 * sr
            val speech = ShortArray(n) { i ->
                val env = kotlin.math.sin(Math.PI * ((i * 4.0 / sr) % 1.0))
                (env * env * kotlin.math.sin(2 * Math.PI * 180 * i / sr) * 9_000 + random.nextInt(-30, 31)).toInt().toShort()
            }
            onPcm(speech, n)
            val gap = ShortArray(2 * sr) { random.nextInt(-30, 31).toShort() }
            onPcm(gap, gap.size)
        }
        true
    }

    private fun repoWith(decoder: io.github.ukpratik.mukhar.data.audio.PcmDecoder): TalkRepository {
        val prompts = PromptRepository({ File("src/main/assets/prompts.json").readText() }, db.habitDao(), clock, json)
        return TalkRepository(
            db.talkDao(), db.wordDao(), HabitRepository(db.habitDao(), tempSettingsStore(), clock), settings, pipeline, prompts, scheduler,
            RecordingFiles(dir), { freeBytes }, clock, { "talk-${++nextId}" }, json, TestScope(),
            testWordRepository(db, clock), decoder,
        )
    }

    @Test
    fun noTranscriptStillGivesAVoiceOnlyReportAndKeepsTheAudio() = runTest {
        repo = repoWith(voiceDecoder)
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.EngineUnavailable)
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        val s = status(id)
        assertThat(s.status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(s.errorCode).isEqualTo("AUDIO_ONLY:MODEL_NOT_INSTALLED")
        assertThat(TalkCodes.audioOnlyReason(s.errorCode)).isEqualTo("MODEL_NOT_INSTALLED")
        assertThat(File(s.audioPath!!).exists()).isTrue()
        val details = repo.observeTalk(id).first()!!
        assertThat(details.words).isNull()
        assertThat(details.metrics!!.algoVersion).isEqualTo(100)
        assertThat(details.metrics!!.longPauses).isEqualTo(3)
        assertThat(details.metrics!!.wpm).isGreaterThan(0.0)
        assertThat(details.feedback!!.model).isEqualTo("acoustic-coach")
        assertThat(details.feedback!!.feedback.notes.map { it.key }).contains("audio_only")
        // Estimates don't feed "your average" for later talks.
        assertThat(repo.previousAverages(Lang.EN, clock.now().toEpochMilli() + 1)).isNull()
    }

    @Test
    fun sarvamTalksTakeTheirLongPausesFromTheAudio() = runTest {
        // D-052: Sarvam's per-piece timestamps hide pauses; the recording has 3 gaps of 2 s.
        repo = repoWith(voiceDecoder)
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Ok(TranscribeResult("srv-1", "sarvam", "saaras:v3", 60_000, CannedTalks.words(Lang.EN)))
        var coachSaw: Int? = null
        pipeline.feedbackAnswers += { req -> coachSaw = req.metrics.longPauses; cannedFeedback()(req) }
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        val m = repo.observeTalk(id).first()!!.metrics!!
        assertThat(m.longPauses).isEqualTo(3)
        assertThat(m.algoVersion).isEqualTo(1) // still a transcript report
        assertThat(coachSaw).isEqualTo(3) // the coach sees the same number as the report
    }

    @Test
    fun otherEnginesKeepTheTranscriptPauses() = runTest {
        repo = repoWith(voiceDecoder)
        val id = recorded()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        repo.analyze(id)
        val expected = io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator.compute(CannedTalks.words(Lang.EN), Lang.EN).longPauses
        assertThat(repo.observeTalk(id).first()!!.metrics!!.longPauses).isEqualTo(expected)
    }

    @Test
    fun writeTranscriptLaterReplacesTheVoiceOnlyReport() = runTest {
        repo = repoWith(voiceDecoder)
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.Offline)
        repo.analyze(id)
        assertThat(TalkCodes.isAudioOnly(status(id).errorCode)).isTrue()

        pipeline.transcribeAnswers.clear()
        pipeline.transcribeAnswers += transcript()
        pipeline.feedbackAnswers += cannedFeedback()
        assertThat(repo.writeTranscript(id)).isTrue()
        assertThat(scheduler.enqueued).contains(id)
        assertThat(repo.analyze(id)).isEqualTo(AnalysisRun.Done)
        val s = status(id)
        assertThat(s.status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(s.errorCode).isNull()
        assertThat(s.audioPath).isNull() // deleted after the full analysis, as before (not kept)
        val details = repo.observeTalk(id).first()!!
        assertThat(details.words).isNotEmpty()
        assertThat(details.metrics!!.algoVersion).isEqualTo(1)
        assertThat(details.feedback!!.model).isEqualTo("haiku")
    }

    @Test
    fun addingAKeyUpgradesVoiceOnlyReports() = runTest {
        repo = repoWith(voiceDecoder)
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.EngineUnavailable)
        repo.analyze(id)
        scheduler.enqueued.clear()
        assertThat(repo.retryWaitingForEngine()).isEqualTo(1)
        assertThat(scheduler.enqueued).contains(id)
        assertThat(status(id).status).isEqualTo(TalkStatus.RECORDED)
    }

    @Test
    fun silentAudioIsTooLittleSpeechNotAnEmptyReport() = runTest {
        repo = repoWith { _, onPcm -> onPcm(ShortArray(16_000 * 30), 16_000 * 30); true }
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.EngineUnavailable)
        repo.analyze(id)
        assertThat(status(id).status).isEqualTo(TalkStatus.FAILED)
        assertThat(status(id).errorCode).isEqualTo("TOO_LITTLE_SPEECH")
    }

    @Test
    fun baselinesAreNeverVoiceOnly() = runTest {
        repo = repoWith(voiceDecoder)
        val (id, file) = repo.startSession(TalkSpec(TalkKind.BASELINE, Lang.EN, Track.EVERYDAY, "baseline"))
        file.writeBytes(ByteArray(128))
        repo.finishRecording(id, RecordingResult(file, 60_000, 45_000, 25.0, false))
        pipeline.transcribeAnswers += Outcome.Err(AppError.EngineUnavailable)
        repo.analyze(id)
        assertThat(status(id).status).isEqualTo(TalkStatus.FAILED)
        assertThat(status(id).errorCode).isEqualTo("MODEL_NOT_INSTALLED")
    }
}
