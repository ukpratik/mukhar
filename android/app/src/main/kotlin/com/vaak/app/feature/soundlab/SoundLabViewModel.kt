package com.vaak.app.feature.soundlab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaak.data.audio.Recorder
import com.vaak.data.audio.RecorderState
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.tts.Speaker
import com.vaak.domain.model.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class SoundLabUiState(
    val lab: SoundLabState = SoundLabState(),
    val registry: ClipRegistry? = null,
    /** Key of the clip playing now: a word id, [MY_TAKE] or null. */
    val playing: String? = null,
    /** Installed TTS voices, for preview clips (true = installed, false = none, missing = starting). */
    val voices: Map<Lang, Boolean> = emptyMap(),
    /** Content couldn't be read (a broken build); shown as a calm empty state. */
    val loadFailed: Boolean = false,
) {
    fun source(word: PairWord, lang: Lang): ClipSource = registry?.resolve(word, lang) ?: ClipSource.Preview(word.text, lang)

    fun isPreview(word: PairWord): Boolean = registry?.isRecorded(word) != true

    /** A preview clip can't play without a TTS voice for its language. */
    fun canPlay(word: PairWord, lang: Lang): Boolean = !isPreview(word) || voices[lang] != false

    companion object {
        const val MY_TAKE = "my-take"
    }
}

sealed interface SoundLabAction {
    data class Lab(val event: LabEvent) : SoundLabAction
    data class Play(val word: PairWord) : SoundLabAction
    /** Plays the clip of the current Pick round again. */
    data object PlayRound : SoundLabAction
    /** "Hear both": the two words of the round, back to back. */
    data object HearBoth : SoundLabAction
    data object PlayMine : SoundLabAction
    data object StartRecord : SoundLabAction
    data object StopRecord : SoundLabAction
}

/**
 * S22 Sound Lab lite (US-070). Runs [SoundLabMachine] and its side effects: clip playback, the user's take with the
 * app's [Recorder] (≤ 3 s, kept in the cache, never uploaded or scored, deleted when the pair is left). Finished pair
 * ids (only the ids: no audio, no score) are kept in the `settings` DataStore for the "Practised" chip.
 */
@HiltViewModel
class SoundLabViewModel @Inject constructor(
    private val source: PairSource,
    private val player: ClipPlayer,
    private val recorder: Recorder,
    private val files: SoundLabFiles,
    private val settings: SettingsStore,
    speaker: Speaker,
) : ViewModel() {
    private val machine = SoundLabMachine()
    private val _state = MutableStateFlow(SoundLabUiState())
    val state: StateFlow<SoundLabUiState> = _state.asStateFlow()
    private var recording: Job? = null
    private var sequence: Job? = null

    init {
        speaker.warmUp()
        files.clear() // takes left by a process death
        viewModelScope.launch { player.playing.collect { k -> _state.update { it.copy(playing = k) } } }
        viewModelScope.launch { speaker.voices.collect { v -> _state.update { it.copy(voices = v) } } }
        viewModelScope.launch {
            settings.soundLabPractised.collect { ids -> _state.update { it.copy(lab = it.lab.copy(practised = it.lab.practised + ids)) } }
        }
        viewModelScope.launch {
            runCatching { source.load() }
                .onSuccess { loaded ->
                    _state.update { it.copy(registry = loaded.registry) }
                    dispatch(LabEvent.Loaded(loaded.pack.pairs))
                }
                .onFailure { _state.update { it.copy(loadFailed = true, lab = it.lab.copy(loading = false)) } }
        }
    }

    fun onAction(a: SoundLabAction) {
        when (a) {
            is SoundLabAction.Lab -> onLab(a.event)
            is SoundLabAction.Play -> play(a.word)
            SoundLabAction.PlayRound -> session()?.current?.word?.let(::play)
            SoundLabAction.HearBoth -> hearBoth()
            SoundLabAction.PlayMine -> session()?.takePath?.let { player.playFile(SoundLabUiState.MY_TAKE, it) }
            SoundLabAction.StartRecord -> startRecord()
            SoundLabAction.StopRecord -> viewModelScope.launch { stopRecord() }
        }
    }

    private fun session() = _state.value.lab.session

    private fun onLab(e: LabEvent) {
        val leaving = e == LabEvent.Back || e == LabEvent.Done
        val finished = if (e == LabEvent.Done) session()?.pair?.id else null
        if (leaving || e == LabEvent.NextClip || e == LabEvent.TestMyEar || e == LabEvent.RecordAgain) stopAudio()
        if (leaving || e == LabEvent.RecordAgain) cancelRecording()
        dispatch(e)
        if (finished != null) viewModelScope.launch { settings.markSoundLabPractised(finished) }
    }

    /** Applies [e] and deletes a take file the new state no longer holds. */
    private fun dispatch(e: LabEvent) {
        val before = session()?.takePath
        _state.update { it.copy(lab = machine.reduce(it.lab, e)) }
        val after = session()?.takePath
        if (before != null && before != after) File(before).delete()
    }

    private fun play(word: PairWord) {
        sequence?.cancel()
        val lang = session()?.pair?.lang ?: return
        player.play(word.id, _state.value.source(word, lang))
    }

    private fun hearBoth() {
        val s = session() ?: return
        val (x, y) = s.current?.choices ?: return
        sequence?.cancel()
        sequence = viewModelScope.launch {
            player.play(x.id, _state.value.source(x, s.pair.lang))
            player.playing.first { it == null }
            delay(GAP_MS)
            player.play(y.id, _state.value.source(y, s.pair.lang))
        }
    }

    private fun stopAudio() {
        sequence?.cancel()
        player.stop()
    }

    private fun startRecord() {
        val s = session() ?: return
        if (s.step != LabStep.SAY || s.take is Take.Recording) return
        stopAudio()
        dispatch(LabEvent.RecordAgain) // drops (and deletes) an earlier take
        val file = files.newTake(UUID.randomUUID().toString())
        if (!recorder.start(file, SoundLabMachine.TAKE_CAP_MS)) {
            dispatch(LabEvent.TakeRetry)
            return
        }
        dispatch(LabEvent.RecordStarted)
        recording = viewModelScope.launch {
            recorder.state.collect { r ->
                when (r) {
                    is RecorderState.Active -> dispatch(LabEvent.RecordProgress(r.elapsedMs))
                    is RecorderState.Finished, is RecorderState.Failed -> stopRecord()
                    RecorderState.Idle -> Unit
                }
            }
        }
    }

    private suspend fun stopRecord() {
        if (session()?.take !is Take.Recording) return
        recording?.cancel()
        recording = null
        val result = recorder.stop()
        recorder.reset()
        when {
            session()?.take !is Take.Recording -> result?.file?.delete() // left the pair meanwhile
            result == null || result.durationMs < SoundLabMachine.MIN_TAKE_MS -> {
                result?.file?.delete()
                dispatch(LabEvent.TakeRetry)
            }
            else -> dispatch(LabEvent.TakeReady(result.file.path, result.durationMs))
        }
    }

    private fun cancelRecording() {
        if (session()?.take !is Take.Recording) return
        recording?.cancel()
        recording = null
        viewModelScope.launch {
            recorder.discard()
            recorder.reset()
        }
    }

    override fun onCleared() {
        sequence?.cancel()
        player.release()
        if (session()?.take is Take.Recording) {
            recording?.cancel()
            runBlocking { recorder.discard() }
            recorder.reset()
        }
        files.clear()
    }

    private companion object {
        const val GAP_MS = 350L
    }
}
