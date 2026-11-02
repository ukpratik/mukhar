package com.vaak.data.baseline

import com.vaak.domain.model.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Live recorder state for the S05 recorder UI. */
data class BaselineProgress(val elapsedMs: Long, val level: Float)

sealed interface BaselineResult {
    /** Analysed (free once, D-031) and stored as day 0. */
    data class Saved(val sessionId: String, val durationMs: Long) : BaselineResult

    /** Under 10 s of speech: "Let's try a few more seconds" (S05). */
    data object TooShort : BaselineResult

    /** The recording couldn't be saved or analysed; the user can try later from Today. */
    data object Failed : BaselineResult

    /** 409 BASELINE_USED: this account or phone already had its free baseline (D-031). Nothing is kept. */
    data object AlreadyUsed : BaselineResult
}

/**
 * The optional 1-minute baseline (US-003, S05). [TalkBaselineRecorder] implements it on the talk pipeline (same
 * recorder and analysis as a talk, `kind = baseline`); [UnavailableBaselineRecorder] makes S05 take the "Later" path.
 */
interface BaselineRecorder {
    /** False while no recorder is wired: S05's Record button behaves like Later. */
    val isAvailable: Boolean

    /** Non-null while recording. */
    val progress: StateFlow<BaselineProgress?>

    /** Starts recording (the mic permission is already granted). Returns false if it couldn't start. */
    suspend fun start(lang: Lang, prompt: String): Boolean

    /** Stops, analyses and stores the baseline. Auto-stop at [MAX_MS] is the recorder's job. */
    suspend fun stop(): BaselineResult

    /** Back during recording, after "Discard this recording?". */
    suspend fun discard()

    /** Asked once after analysis, off by default (design §10 rule 8). `false` deletes the audio now. */
    suspend fun keepAudio(sessionId: String, keep: Boolean)

    /**
     * A baseline saved when the app closed mid-recording (S09 state 8): analyse it as the baseline and return to the
     * "Keep this recording?" question, as if Stop had been tapped.
     */
    suspend fun resume(sessionId: String): BaselineResult = BaselineResult.Failed

    /** The user left S05 before answering "Keep this recording?": the default (off) applies, the audio is deleted. */
    fun abandon(sessionId: String) = Unit

    companion object {
        const val MAX_MS = 60_000L
        const val MIN_SPEECH_MS = 10_000L
    }
}

/** Never records: S05's Record behaves like Later (tests, and a fallback if the mic stack is unavailable). */
class UnavailableBaselineRecorder @Inject constructor() : BaselineRecorder {
    private val idle = MutableStateFlow<BaselineProgress?>(null)
    override val isAvailable: Boolean = false
    override val progress: StateFlow<BaselineProgress?> = idle.asStateFlow()
    override suspend fun start(lang: Lang, prompt: String): Boolean = false
    override suspend fun stop(): BaselineResult = BaselineResult.Failed
    override suspend fun discard() = Unit
    override suspend fun keepAudio(sessionId: String, keep: Boolean) = Unit
}
