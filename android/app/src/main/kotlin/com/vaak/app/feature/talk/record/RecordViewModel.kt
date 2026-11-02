package com.vaak.app.feature.talk.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vaak.app.nav.RecordRoute
import com.vaak.data.audio.MicCheck
import com.vaak.data.audio.Recorder
import com.vaak.data.audio.RecorderState
import com.vaak.data.audio.RecordingResult
import com.vaak.data.content.PromptRepository
import com.vaak.data.prefs.UserSettings
import com.vaak.data.analytics.Analytics
import com.vaak.data.analytics.AnalyticsEvent
import com.vaak.data.repo.Entitlements
import com.vaak.data.repo.TalkQuota
import com.vaak.data.repo.StopOutcome
import com.vaak.data.repo.TalkKind
import com.vaak.data.repo.TalkRepository
import com.vaak.data.repo.TalkSpec
import com.vaak.data.repo.capSeconds
import com.vaak.data.work.TalkNotifier
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Prompt
import com.vaak.domain.prompt.Track
import com.vaak.domain.talk.StorageLevel
import com.vaak.domain.talk.TalkThresholds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S09 states (design final/s09-recording.html). Think time lives here; audio states come from [Recorder]. */
enum class RecordPhase { LOADING, PERMISSION, READY, THINK, RECORDING, PAUSED, TOO_SHORT, SAVING }

/** Low-storage sheet (D-028): warn below 50 MB, block below 10 MB. */
enum class StorageSheet { NONE, WARN, BLOCK }

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.LOADING,
    val prompt: Prompt? = null,
    val lang: Lang = Lang.EN,
    val track: Track = Track.EVERYDAY,
    val isRetake: Boolean = false,
    val capMs: Long = TalkThresholds.FREE_CAP_S * 1000L,
    val permissionDenied: Boolean = false,
    val micCheck: MicCheck? = null,
    val checking: Boolean = false,
    val thinkTime: Boolean = false,
    val thinkLeft: Int = TalkThresholds.THINK_TIME_S,
    val elapsedMs: Long = 0,
    val pausesLeft: Int = TalkThresholds.MAX_PAUSES,
    val autoPaused: Boolean = false,
    val waveform: List<Float> = emptyList(),
    val storageSheet: StorageSheet = StorageSheet.NONE,
    val availableMb: Int = 0,
    val discardSheet: Boolean = false,
    val tooShortSpeechS: Int = 0,
    val structureHint: Boolean = true,
)

sealed interface RecordEvent {
    data class PermissionResult(val granted: Boolean) : RecordEvent
    data object CheckAgain : RecordEvent
    data class SetThinkTime(val on: Boolean) : RecordEvent
    data object Start : RecordEvent
    data object StartNow : RecordEvent
    data object Stop : RecordEvent
    data object Pause : RecordEvent
    data object Resume : RecordEvent
    data object AppBackgrounded : RecordEvent
    data object Close : RecordEvent
    data object KeepTalking : RecordEvent
    data object ConfirmDiscard : RecordEvent
    data object RecordAnyway : RecordEvent
    data object DismissStorage : RecordEvent
    data object TryAgain : RecordEvent
}

sealed interface RecordEffect {
    data object Leave : RecordEffect
    data class OpenReport(val sessionId: String) : RecordEffect
    data class OpenSayAgain(val retakeId: String) : RecordEffect
    data object HapticConfirm : RecordEffect
    data object HapticTick : RecordEffect
    data object MicUnavailable : RecordEffect
    data object OpenStorageSettings : RecordEffect

    /** Today's free talk is used (client mirror, US-060): S17 instead of the recorder. Never mid-recording. */
    data object OpenQuota : RecordEffect
}

@HiltViewModel
class RecordViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val recorder: Recorder,
    private val talks: TalkRepository,
    private val prompts: PromptRepository,
    private val settings: UserSettings,
    private val entitlements: Entitlements,
    private val notifier: TalkNotifier,
    private val quota: TalkQuota,
    private val analytics: Analytics,
) : ViewModel() {
    private val route = savedState.toRoute<RecordRoute>()
    private val _state = MutableStateFlow(RecordUiState(isRetake = route.parentId != null))
    val state: StateFlow<RecordUiState> = _state.asStateFlow()
    private val effects = Channel<RecordEffect>(Channel.BUFFERED)
    val effect: Flow<RecordEffect> = effects.receiveAsFlow()

    private var sessionId: String? = null
    private var thinkJob: Job? = null
    private var checkJob: Job? = null
    private var finishing = false
    private var lastPersistMs = 0L
    private var markersPassed = 0

    init {
        viewModelScope.launch {
            val prefs = settings.currentPrefs()
            val lang = route.lang?.let { runCatching { Lang.fromCode(it) }.getOrNull() } ?: prefs.lastLang
            val track = route.track?.let(Track::fromKey) ?: prefs.trackFor(lang)
            val prompt = route.promptId?.let { prompts.byId(it) } ?: prompts.next(lang, track, null)
            val cap = route.capS?.toLong() ?: capSeconds(entitlements.isPremium.first()).toLong()
            // Re-takes and re-recording a recovered talk don't use the daily talk (US-017, D-031).
            if (route.parentId == null && route.replaceId == null && quota.status().exhausted) {
                effects.send(RecordEffect.OpenQuota)
                return@launch
            }
            _state.update {
                it.copy(
                    phase = RecordPhase.PERMISSION, prompt = prompt, lang = prompt?.lang ?: lang, track = track,
                    capMs = cap * 1000, thinkTime = prefs.thinkTime, structureHint = prefs.structureHint,
                )
            }
        }
        viewModelScope.launch { recorder.state.collect(::onRecorder) }
    }

    fun onEvent(e: RecordEvent) {
        when (e) {
            is RecordEvent.PermissionResult -> onPermission(e.granted)
            RecordEvent.CheckAgain -> runMicCheck()
            is RecordEvent.SetThinkTime -> {
                _state.update { it.copy(thinkTime = e.on) }
                viewModelScope.launch { settings.setThinkTime(e.on) }
            }
            RecordEvent.Start -> onStart()
            RecordEvent.StartNow -> {
                thinkJob?.cancel()
                beginRecording()
            }
            RecordEvent.Stop -> stop()
            RecordEvent.Pause -> if (recorder.pause(auto = false)) effects.trySend(RecordEffect.HapticTick)
            RecordEvent.Resume -> {
                recorder.resume()
                notifier.clearPaused()
            }
            RecordEvent.AppBackgrounded -> {
                thinkJob?.cancel()
                if (_state.value.phase == RecordPhase.THINK) _state.update { it.copy(phase = RecordPhase.READY) }
                if (recorder.pause(auto = true)) notifier.talkPaused()
            }
            RecordEvent.Close -> when (_state.value.phase) {
                RecordPhase.RECORDING, RecordPhase.PAUSED -> _state.update { it.copy(discardSheet = true) }
                else -> {
                    thinkJob?.cancel()
                    effects.trySend(RecordEffect.Leave)
                }
            }
            RecordEvent.KeepTalking -> _state.update { it.copy(discardSheet = false) }
            RecordEvent.ConfirmDiscard -> viewModelScope.launch {
                _state.update { it.copy(discardSheet = false) }
                finishing = true
                recorder.discard()
                sessionId?.let { talks.discard(it) }
                notifier.clearPaused()
                effects.send(RecordEffect.Leave)
            }
            RecordEvent.RecordAnyway -> {
                _state.update { it.copy(storageSheet = StorageSheet.NONE) }
                onStart(storageChecked = true)
            }
            RecordEvent.DismissStorage -> _state.update { it.copy(storageSheet = StorageSheet.NONE) }
            RecordEvent.TryAgain -> {
                sessionId = null
                finishing = false
                recorder.reset()
                _state.update { it.copy(phase = RecordPhase.READY, elapsedMs = 0, pausesLeft = TalkThresholds.MAX_PAUSES, waveform = emptyList()) }
            }
        }
    }

    fun openStorageSettings() {
        effects.trySend(RecordEffect.OpenStorageSettings)
    }

    private fun onPermission(granted: Boolean) {
        if (!granted) {
            _state.update { it.copy(phase = RecordPhase.PERMISSION, permissionDenied = true) }
            return
        }
        if (_state.value.phase != RecordPhase.PERMISSION && _state.value.phase != RecordPhase.LOADING) return
        _state.update { it.copy(phase = RecordPhase.READY, permissionDenied = false) }
        // US-018 / S09 state 7: checked when the recorder opens; a warning sheet, a block only under 10 MB.
        showStorageSheetIfNeeded()
        runMicCheck()
    }

    private fun showStorageSheetIfNeeded(): Boolean {
        val level = talks.storageLevel()
        val mb = (talks.availableBytes() / (1024 * 1024)).toInt()
        val sheet = when (level) {
            StorageLevel.OK -> StorageSheet.NONE
            StorageLevel.LOW -> StorageSheet.WARN
            StorageLevel.BLOCKED -> StorageSheet.BLOCK
        }
        _state.update { it.copy(storageSheet = sheet, availableMb = mb) }
        return sheet != StorageSheet.NONE
    }

    private fun runMicCheck() {
        checkJob?.cancel()
        _state.update { it.copy(checking = true) }
        checkJob = viewModelScope.launch {
            val check = recorder.micCheck()
            _state.update { it.copy(micCheck = check, checking = false) }
        }
    }

    private fun onStart(storageChecked: Boolean = false) {
        // The check repeats once at Start; there is no second warning sheet, only the < 10 MB block.
        if (!storageChecked && talks.storageLevel() == StorageLevel.BLOCKED) {
            showStorageSheetIfNeeded()
            return
        }
        checkJob?.cancel()
        if (_state.value.thinkTime) startThinkTime() else beginRecording()
    }

    private fun startThinkTime() {
        _state.update { it.copy(phase = RecordPhase.THINK, thinkLeft = TalkThresholds.THINK_TIME_S) }
        thinkJob = viewModelScope.launch {
            for (left in TalkThresholds.THINK_TIME_S downTo 1) {
                _state.update { it.copy(thinkLeft = left) }
                if (left <= 3) effects.send(RecordEffect.HapticTick)
                delay(1_000)
            }
            beginRecording()
        }
    }

    private fun beginRecording() {
        if (_state.value.phase == RecordPhase.RECORDING) return
        val s = _state.value
        val prompt = s.prompt ?: return
        viewModelScope.launch {
            val kind = if (route.parentId != null) TalkKind.RETAKE else TalkKind.TALK
            val (id, file) = talks.startSession(TalkSpec(kind, prompt.lang, s.track, prompt.id, route.parentId))
            sessionId = id
            finishing = false
            markersPassed = 0
            lastPersistMs = 0
            if (!recorder.start(file, s.capMs)) {
                talks.discard(id)
                sessionId = null
                _state.update { it.copy(phase = RecordPhase.READY) }
                effects.send(RecordEffect.MicUnavailable)
                return@launch
            }
            effects.send(RecordEffect.HapticConfirm)
            analytics.log(AnalyticsEvent.talkStarted(prompt.lang.code, s.track.key))
        }
    }

    private fun stop() {
        if (finishing) return
        viewModelScope.launch {
            val result = recorder.stop() ?: return@launch
            finish(result)
        }
    }

    private suspend fun onRecorder(rs: RecorderState) {
        when (rs) {
            is RecorderState.Active -> {
                val id = sessionId ?: return
                val marker = (rs.elapsedMs / MARKER_MS).toInt()
                if (marker > markersPassed && rs.elapsedMs < rs.capMs) {
                    markersPassed = marker
                    effects.send(RecordEffect.HapticTick)
                }
                if (rs.elapsedMs - lastPersistMs >= PERSIST_EVERY_MS) {
                    lastPersistMs = rs.elapsedMs
                    talks.updateProgress(id, rs.elapsedMs, rs.speechMs)
                }
                _state.update {
                    it.copy(
                        phase = if (rs.paused) RecordPhase.PAUSED else RecordPhase.RECORDING,
                        elapsedMs = rs.elapsedMs, pausesLeft = rs.pausesLeft, autoPaused = rs.autoPaused,
                        waveform = rs.waveform,
                    )
                }
            }
            is RecorderState.Finished -> if (sessionId != null) finish(rs.result)
            is RecorderState.Failed -> rs.result?.let { if (sessionId != null) finish(it) }
            RecorderState.Idle -> Unit
        }
    }

    /** Stop or cap (CONFIRM haptic): < 10 s → "couldn't hear much"; otherwise save, credit and analyse. */
    private suspend fun finish(result: RecordingResult) {
        if (finishing) return
        finishing = true
        val id = sessionId ?: return
        effects.send(RecordEffect.HapticConfirm)
        notifier.clearPaused()
        _state.update { it.copy(phase = RecordPhase.SAVING) }
        when (val outcome = talks.finishRecording(id, result, replaces = route.replaceId)) {
            is StopOutcome.TooShort -> {
                recorder.reset()
                _state.update { it.copy(phase = RecordPhase.TOO_SHORT, tooShortSpeechS = (outcome.speechMs / 1000).toInt()) }
            }
            is StopOutcome.Saved -> {
                recorder.reset()
                analytics.log(AnalyticsEvent.talkStopped(result.durationMs / 1000, result.speechMs / 1000))
                if (outcome.credit != null) analytics.log(AnalyticsEvent.streakCredited)
                talks.startAnalysis(id)
                effects.send(if (route.parentId != null) RecordEffect.OpenSayAgain(id) else RecordEffect.OpenReport(id))
            }
        }
    }

    companion object {
        const val MARKER_MS = 60_000L
        const val PERSIST_EVERY_MS = 5_000L
    }
}
