package com.vaak.data.baseline

import com.vaak.data.audio.Recorder
import com.vaak.data.audio.RecorderState
import com.vaak.data.repo.StopOutcome
import com.vaak.data.repo.TalkKind
import com.vaak.data.repo.TalkRepository
import com.vaak.data.repo.TalkSpec
import com.vaak.data.repo.TalkStatus
import com.vaak.data.util.ApplicationScope
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The S05 baseline on the talk pipeline (US-003, D-031): the same [Recorder] (capped at 1:00, no pauses) and the
 * same `talk_session` row, so crash recovery and the offline queue cover it. Stop saves the row (`kind = baseline`)
 * and waits for the analysis, as the design's analysing view does. The server analyses one baseline per account and
 * device for free; a second one comes back 409 BASELINE_USED, which is reported as [BaselineResult.AlreadyUsed].
 */
@Singleton
class TalkBaselineRecorder @Inject constructor(
    private val recorder: Recorder,
    private val talks: TalkRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) : BaselineRecorder {
    private val _progress = MutableStateFlow<BaselineProgress?>(null)
    override val isAvailable: Boolean = true
    override val progress: StateFlow<BaselineProgress?> = _progress.asStateFlow()

    private var sessionId: String? = null
    private var watcher: Job? = null

    override suspend fun start(lang: Lang, prompt: String): Boolean {
        if (sessionId != null) return false
        val (id, file) = talks.startSession(TalkSpec(TalkKind.BASELINE, lang, Track.EVERYDAY, prompt))
        if (!recorder.start(file, BaselineRecorder.MAX_MS)) {
            talks.discard(id)
            return false
        }
        sessionId = id
        _progress.value = BaselineProgress(0, 0f)
        watcher = scope.launch { watch(id) }
        return true
    }

    override suspend fun stop(): BaselineResult {
        val id = sessionId ?: return BaselineResult.Failed
        val result = recorder.stop()
        clear()
        recorder.reset()
        if (result == null) {
            talks.discard(id)
            return BaselineResult.Failed
        }
        return when (val outcome = talks.finishRecording(id, result)) {
            is StopOutcome.TooShort -> BaselineResult.TooShort
            is StopOutcome.Saved -> analyse(id, result.durationMs)
        }
    }

    override suspend fun discard() {
        val id = sessionId
        recorder.discard()
        clear()
        if (id != null) talks.discard(id)
    }

    /** The baseline row holds its audio through analysis; this answer keeps or deletes it (US-003). */
    override suspend fun keepAudio(sessionId: String, keep: Boolean) = talks.setAudioKept(sessionId, keep)

    override suspend fun resume(sessionId: String): BaselineResult {
        val row = talks.session(sessionId) ?: return BaselineResult.Failed
        if (row.kind != TalkKind.BASELINE.key) return BaselineResult.Failed
        talks.analyzeRecoveredNow(sessionId)
        return result(sessionId, row.durationMs.toLong())
    }

    /** Runs on the app scope: the screen (and its ViewModel) is already gone. */
    override fun abandon(sessionId: String) {
        scope.launch { talks.setAudioKept(sessionId, false) }
    }

    /** A queued analysis (offline, busy) still counts as saved: the queue finishes it later. */
    private suspend fun analyse(id: String, durationMs: Long): BaselineResult {
        talks.analyzeNow(id)
        return result(id, durationMs)
    }

    private suspend fun result(id: String, durationMs: Long): BaselineResult {
        val row = talks.session(id) ?: return BaselineResult.Failed
        return when {
            row.errorCode == BASELINE_USED -> {
                talks.discard(id)
                BaselineResult.AlreadyUsed
            }
            row.status == TalkStatus.FAILED && row.errorCode != null && row.errorCode in TERMINAL -> BaselineResult.Failed
            else -> BaselineResult.Saved(id, durationMs)
        }
    }

    /** Mirrors the recorder for the S05 timer and persists progress for crash recovery (as S09 does). */
    private suspend fun watch(id: String) {
        var lastPersist = 0L
        recorder.state.collect { s ->
            when (s) {
                is RecorderState.Active -> {
                    _progress.value = BaselineProgress(s.elapsedMs, s.waveform.lastOrNull() ?: 0f)
                    if (s.elapsedMs - lastPersist >= PERSIST_EVERY_MS) {
                        lastPersist = s.elapsedMs
                        talks.updateProgress(id, s.elapsedMs, s.speechMs)
                    }
                }
                // Auto-stop at 1:00 or a mic failure: S05 sees progress end and calls stop().
                is RecorderState.Finished, is RecorderState.Failed -> _progress.value = null
                RecorderState.Idle -> Unit
            }
        }
    }

    private fun clear() {
        watcher?.cancel()
        watcher = null
        sessionId = null
        _progress.value = null
    }

    private companion object {
        const val PERSIST_EVERY_MS = 5_000L
        const val BASELINE_USED = "BASELINE_USED"
        val TERMINAL = setOf("TOO_LITTLE_SPEECH", "AUDIO_TOO_LONG", "AUDIO_MISSING")
    }
}
