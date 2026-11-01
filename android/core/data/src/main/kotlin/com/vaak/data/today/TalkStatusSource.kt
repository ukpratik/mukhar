package com.vaak.data.today

import com.vaak.data.db.TalkDao
import com.vaak.data.db.VaakDatabase
import com.vaak.data.repo.TalkCodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** Talks recorded but not analysed yet (offline queue, US-072). Their day is already counted (US-031). */
data class WaitingTalks(val count: Int, val latestRecordedAt: Instant, val latestDurationMs: Int)

/** A talk saved after the app closed mid-recording (S09 state 8). */
data class RecoveredTalk(val sessionId: String, val recordedAt: Instant, val durationMs: Int)

/**
 * What Today shows about the talk pipeline, read from Room. The recovered talk is the row crash recovery
 * (`TalkRepository.recoverOnStart`) left as `recorded` + `RECOVERED` until the user acts on it (S09 state 8).
 */
interface TalkStatusSource {
    val waiting: Flow<WaitingTalks?>
    val recovered: Flow<RecoveredTalk?>
}

class RoomTalkStatusSource @Inject constructor(
    private val db: VaakDatabase,
    private val talkDao: TalkDao,
) : TalkStatusSource {
    override val waiting: Flow<WaitingTalks?> = db.invalidationTracker.createFlow("talk_session")
        .map {
            talkDao.sessionsWithStatus(WAITING).filter { it.kind != "readaloud" && it.errorCode != TalkCodes.RECOVERED }.let { rows ->
                rows.maxByOrNull { it.createdAt }?.let { WaitingTalks(rows.size, Instant.ofEpochMilli(it.createdAt), it.durationMs) }
            }
        }
        .distinctUntilChanged()

    override val recovered: Flow<RecoveredTalk?> = talkDao.observeRecovered()
        .map { s -> s?.let { RecoveredTalk(it.id, Instant.ofEpochMilli(it.createdAt), it.durationMs) } }
        .distinctUntilChanged()

    private companion object {
        val WAITING = listOf("recorded", "queued", "uploading", "failed")
    }
}
