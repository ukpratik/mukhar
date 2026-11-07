package io.github.ukpratik.mukhar.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.audio.RecordingFiles
import io.github.ukpratik.mukhar.data.db.MukharDatabase
import io.github.ukpratik.mukhar.data.db.TalkSessionEntity
import io.github.ukpratik.mukhar.data.tempSettingsStore
import io.github.ukpratik.mukhar.domain.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

/** Settings → Recordings and the size limit (US-080). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordingsRepositoryTest {
    private lateinit var db: MukharDatabase
    private val clock = FixedClock(Instant.parse("2026-11-08T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val files = RecordingFiles(Files.createTempDirectory("rec").toFile())
    private val settings = tempSettingsStore()
    private lateinit var repo: RecordingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MukharDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = RecordingsRepository(db.talkDao(), files, settings, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun talk(id: String, ageDays: Long, mb: Int, status: String = TalkStatus.FEEDBACK_READY, error: String? = null): File {
        val f = files.fileFor(id)
        f.writeBytes(ByteArray(mb * 1024 * 1024))
        val at = clock.now().minusSeconds(ageDays * 86_400).toEpochMilli()
        db.talkDao().insertSession(
            TalkSessionEntity(
                id = id, kind = "talk", parentId = null, lang = "en", track = "everyday", promptId = "en-001", createdAt = at,
                zoneId = "Asia/Kolkata", durationMs = 60_000, speechMs = 40_000, snrDb = 20.0, status = status, leaseUntil = null,
                attempts = 0, errorCode = error, audioPath = f.path, audioKept = true, serverSessionId = null, quotaDay = null, updatedAt = at,
            ),
        )
        return f
    }

    @Test
    fun theDefaultLimitIs500MbAndListsNewestFirst() = runTest {
        talk("a", 3, 1)
        talk("b", 1, 2)
        assertThat(repo.capMb.first()).isEqualTo(500)
        val items = repo.observe().first()
        assertThat(items.map { it.sessionId }).containsExactly("b", "a").inOrder()
        assertThat(items.first().bytes).isEqualTo(2L * 1024 * 1024)
    }

    @Test
    fun overTheLimitTheOldestGoFirstAndPendingTalksStay() = runTest {
        val pending = talk("pending", 9, 60, status = TalkStatus.QUEUED)
        val old = talk("old", 5, 30)
        val mid = talk("mid", 3, 30)
        val new = talk("new", 1, 30, error = "AUDIO_ONLY:MODEL_NOT_INSTALLED")
        repo.setCapMb(100) // 150 MB stored
        assertThat(pending.exists()).isTrue()
        assertThat(old.exists()).isFalse()
        assertThat(mid.exists()).isFalse()
        assertThat(new.exists()).isTrue()
        val oldRow = db.talkDao().session("old")!!
        assertThat(oldRow.audioPath).isNull()
        assertThat(oldRow.status).isEqualTo(TalkStatus.FEEDBACK_READY) // the report stays
    }

    @Test
    fun deletingAPendingTalksAudioMarksItMissingAndVoiceOnlyCantBeTranscribed() = runTest {
        talk("pending", 1, 1, status = TalkStatus.FAILED, error = "MODEL_NOT_INSTALLED")
        talk("voice", 1, 1, error = "AUDIO_ONLY:OFFLINE")
        repo.delete("pending")
        repo.delete("voice")
        assertThat(db.talkDao().session("pending")!!.errorCode).isEqualTo(TalkCodes.AUDIO_MISSING)
        val voice = db.talkDao().session("voice")!!
        assertThat(voice.status).isEqualTo(TalkStatus.FEEDBACK_READY)
        assertThat(voice.errorCode).isEqualTo("AUDIO_ONLY:AUDIO_MISSING")
        assertThat(repo.observe().first()).isEmpty()
    }

    @Test
    fun deleteAllKeepsARecordingInProgressAndClearsOrphans() = runTest {
        val live = talk("live", 0, 1, status = TalkStatus.RECORDING)
        talk("done", 2, 1)
        File(files.dir, "orphan.aac").writeBytes(ByteArray(10))
        assertThat(repo.deleteAll()).isEqualTo(2)
        assertThat(live.exists()).isTrue()
        assertThat(files.dir.listFiles()!!.map { it.name }).containsExactly("live.aac")
    }
}
