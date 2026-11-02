package com.vaak.data.work

import com.vaak.data.tempSettingsStore
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.vaak.data.audio.RecordingFiles
import com.vaak.data.audio.RecordingResult
import com.vaak.data.content.PromptRepository
import com.vaak.data.db.VaakDatabase
import com.vaak.data.debug.CannedTalks
import com.vaak.data.net.AppError
import com.vaak.data.net.Outcome
import com.vaak.data.repo.HabitRepository
import com.vaak.data.repo.MemorySettings
import com.vaak.data.repo.testWordRepository
import com.vaak.data.repo.RecordingScheduler
import com.vaak.data.repo.ScriptedPipeline
import com.vaak.data.repo.TalkKind
import com.vaak.data.repo.TalkRepository
import com.vaak.data.repo.TalkSpec
import com.vaak.data.repo.TalkStatus
import com.vaak.domain.FixedClock
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Track
import kotlinx.coroutines.test.TestScope
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

/** The queue job (LLD §4.9, US-072) mapped to WorkManager results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalysisWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: VaakDatabase
    private val pipeline = ScriptedPipeline()
    private val scheduler = RecordingScheduler()
    private val notified = mutableListOf<String>()
    private lateinit var repo: TalkRepository
    private val clock = FixedClock(Instant.parse("2026-11-02T04:30:00Z"), ZoneId.of("Asia/Kolkata"))

    private val notifier = object : TalkNotifier {
        override fun resultsReady(sessionId: String) { notified += sessionId }
        override fun talkPaused() = Unit
        override fun clearPaused() = Unit
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, VaakDatabase::class.java).allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val prompts = PromptRepository({ File("src/main/assets/prompts.json").readText() }, db.habitDao(), clock, json)
        var n = 0
        repo = TalkRepository(
            db.talkDao(), db.wordDao(), HabitRepository(db.habitDao(), tempSettingsStore(), clock), MemorySettings(), pipeline, prompts, scheduler,
            RecordingFiles(Files.createTempDirectory("w").toFile()), { Long.MAX_VALUE }, clock, { "t${++n}" }, json, TestScope(),
            testWordRepository(db, clock),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun worker(id: String, attempt: Int = 0) = TestListenableWorkerBuilder<AnalysisWorker>(context)
        .setInputData(workDataOf(AnalysisWorker.KEY_SESSION_ID to id))
        .setRunAttemptCount(attempt)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                AnalysisWorker(appContext, workerParameters, repo, scheduler, notifier)
        })
        .build()

    private suspend fun recorded(): String {
        val (id, file) = repo.startSession(TalkSpec(TalkKind.TALK, Lang.HI, Track.EVERYDAY, "hi-001"))
        file.writeBytes(ByteArray(64))
        repo.finishRecording(id, RecordingResult(file, 120_000, 90_000, 22.0, false))
        return id
    }

    @Test
    fun analysesAQueuedTalkAndNotifies() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Ok(TranscribeResult("srv", "fake", "m", 120_000, CannedTalks.words(Lang.HI)))
        pipeline.feedbackAnswers += { r -> Outcome.Ok(FeedbackResult("fb", "m", CannedTalks.feedback(Lang.HI, r.words.map { it.w }), 0, false, false)) }
        assertThat(worker(id).doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(db.talkDao().session(id)!!.status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(notified).containsExactly(id)
    }

    @Test
    fun offlineRetriesWithBackoff() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.Offline)
        assertThat(worker(id).doWork()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(db.talkDao().session(id)!!.status).isEqualTo(TalkStatus.QUEUED)
    }

    @Test
    fun busyReschedulesInsteadOfSpinning() = runTest {
        val id = recorded()
        pipeline.transcribeAnswers += Outcome.Err(AppError.Busy(120))
        assertThat(worker(id).doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(scheduler.delayed).containsExactly(id to 120_000L)
    }

    @Test
    fun givesUpAfterEightAttemptsButKeepsTheTalk() = runTest {
        val id = recorded()
        assertThat(worker(id, attempt = AnalysisWorker.MAX_ATTEMPTS).doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(pipeline.transcribeCalls).isEmpty()
        assertThat(db.talkDao().session(id)!!.status).isEqualTo(TalkStatus.RECORDED)
    }

    @Test
    fun missingSessionIdFails() = runTest {
        val w = TestListenableWorkerBuilder<AnalysisWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    AnalysisWorker(appContext, workerParameters, repo, scheduler, notifier)
            }).build()
        assertThat(w.doWork()).isEqualTo(ListenableWorker.Result.failure())
    }
}
