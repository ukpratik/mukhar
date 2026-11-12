package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.audio.RecordingFiles
import io.github.ukpratik.mukhar.data.db.TalkDao
import io.github.ukpratik.mukhar.data.db.TalkSessionEntity
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.domain.talk.RecordingRetention
import io.github.ukpratik.mukhar.domain.talk.StoredRecording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One recording on the phone, for Settings → Recordings (US-080). */
data class RecordingItem(
    val sessionId: String,
    val createdAt: Long,
    val zoneId: String,
    val lang: Lang,
    val track: Track,
    val promptId: String,
    val kind: String,
    val durationMs: Long,
    val bytes: Long,
    val path: String,
    /** Still being recorded or waiting for its analysis: deleting it loses the talk's report. */
    val pending: Boolean,
    /** A voice-only report (D-048) whose transcript can still be written from this recording. */
    val awaitingTranscript: Boolean,
)

/**
 * Settings → Recordings (US-080): every recording kept on the phone, its size, delete one or all, and the size limit
 * (default 500 MB) that deletes the oldest recordings first. Talk rows, numbers and notes always stay; only audio goes.
 */
class RecordingsRepository(
    private val talkDao: TalkDao,
    private val files: RecordingFiles,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    val capMb: Flow<Int> get() = settings.recordingsCapMb

    fun observe(): Flow<List<RecordingItem>> = talkDao.observeWithAudio().map { rows -> withContext(Dispatchers.IO) { rows.mapNotNull(::item) } }

    suspend fun setCapMb(mb: Int) {
        settings.setRecordingsCapMb(mb)
        enforceCap()
    }

    /** Deletes one recording. A talk still waiting for its analysis can't be analysed afterwards (it says so). */
    suspend fun delete(sessionId: String) = mutex.withLock {
        val s = talkDao.session(sessionId) ?: return@withLock
        if (s.status == TalkStatus.RECORDING) return@withLock
        release(s)
    }

    /** Deletes every recording except one being recorded right now. Returns how many went. */
    suspend fun deleteAll(): Int = mutex.withLock {
        var n = 0
        for (s in talkDao.withAudio()) {
            if (s.status == TalkStatus.RECORDING) continue
            release(s)
            n++
        }
        n + deleteOrphans(olderThanMs = 0)
    }

    /**
     * Keeps the recordings under the limit, oldest first. Talks being recorded or waiting for analysis are never
     * deleted here. Runs after every talk, at app start and when the limit changes.
     */
    suspend fun enforceCap(): Int = mutex.withLock {
        val cap = RecordingRetention.capBytes(settings.recordingsCapMb.first())
        deleteOrphans(olderThanMs = ORPHAN_AGE_MS)
        val rows = talkDao.withAudio()
        val stored = withContext(Dispatchers.IO) {
            rows.mapNotNull { s ->
                val f = File(s.audioPath ?: return@mapNotNull null)
                if (!f.exists()) return@mapNotNull null
                StoredRecording(s.id, f.length(), s.createdAt, protected = s.status == TalkStatus.RECORDING || s.status in TalkStatus.PENDING)
            }
        }
        val ids = RecordingRetention.toDelete(stored, cap).toSet()
        for (s in rows) if (s.id in ids) release(s)
        if (ids.isNotEmpty()) MukharLog.i("recordings: deleted ${ids.size} over the ${cap / RecordingRetention.MB} MB limit")
        ids.size
    }

    private suspend fun release(s: TalkSessionEntity) {
        s.audioPath?.let { withContext(Dispatchers.IO) { File(it).delete() } }
        val now = clock.now().toEpochMilli()
        val stillPending = s.status in TalkStatus.PENDING && talkDao.transcript(s.id) == null
        talkDao.updateSession(
            s.copy(
                audioPath = null,
                audioKept = false,
                // A talk that hadn't been transcribed can't be any more; a voice-only report keeps its numbers.
                status = if (stillPending) TalkStatus.FAILED else s.status,
                errorCode = when {
                    stillPending -> TalkCodes.AUDIO_MISSING
                    TalkCodes.isAudioOnly(s.errorCode) -> TalkCodes.AUDIO_ONLY_PREFIX + TalkCodes.AUDIO_MISSING
                    else -> s.errorCode
                },
                leaseUntil = if (stillPending) null else s.leaseUntil,
                updatedAt = now,
            ),
        )
    }

    /** Files in the recordings folder that no talk points at (an interrupted delete, an old crash). */
    private suspend fun deleteOrphans(olderThanMs: Long): Int = withContext(Dispatchers.IO) {
        val known = talkDao.withAudio().mapNotNull { it.audioPath }.map { File(it).absolutePath }.toSet()
        val cutoff = clock.now().toEpochMilli() - olderThanMs
        files.dir.listFiles().orEmpty().count { f ->
            // olderThanMs = 0 (Delete all) takes every orphan; the age check compares the injected clock with the
            // file system's, so it only applies to the automatic clean-up.
            f.isFile && f.absolutePath !in known && (olderThanMs == 0L || f.lastModified() <= cutoff) && f.delete()
        }
    }

    private fun item(s: TalkSessionEntity): RecordingItem? {
        val path = s.audioPath ?: return null
        val f = File(path)
        if (!f.exists()) return null
        return RecordingItem(
            sessionId = s.id, createdAt = s.createdAt, zoneId = s.zoneId, lang = Lang.fromCode(s.lang), track = Track.fromKey(s.track),
            promptId = s.promptId, kind = s.kind, durationMs = s.durationMs.toLong(), bytes = f.length(), path = path,
            pending = s.status == TalkStatus.RECORDING || s.status in TalkStatus.PENDING,
            awaitingTranscript = TalkCodes.isAudioOnly(s.errorCode),
        )
    }

    private companion object {
        /** An orphan younger than this may be a recording whose row is being written. */
        const val ORPHAN_AGE_MS = 24 * 60 * 60_000L
    }
}
