package io.github.ukpratik.mukhar.app.feature.talk.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.mukhar.data.content.PromptRepository
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.prefs.UserSettings
import io.github.ukpratik.mukhar.data.repo.Entitlements
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Prompt
import io.github.ukpratik.mukhar.domain.prompt.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PromptUiState(
    val langs: List<Lang> = listOf(Lang.EN, Lang.HI),
    val lang: Lang = Lang.EN,
    val tracks: List<Track> = emptyList(),
    val track: Track = Track.EVERYDAY,
    val prompt: Prompt? = null,
    /** Just shuffled: tagged "New prompt" (S08 state 3). */
    val fresh: Boolean = false,
    val structureHint: Boolean = true,
    val transliteration: Boolean = false,
    val premium: Boolean = false,
    /** US-079: prompts not done in this language and track, and the pool size. */
    val left: Int = 0,
    val total: Int = 0,
)

sealed interface PromptEvent {
    data class SelectLang(val lang: Lang) : PromptEvent
    data class SelectTrack(val track: Track) : PromptEvent
    data object Shuffle : PromptEvent
    data class ToggleStructure(val on: Boolean) : PromptEvent
    data class ToggleTranslit(val on: Boolean) : PromptEvent

    /** US-079: this prompt is done; never pick it again, and show the next one. */
    data object MarkDone : PromptEvent
}

@HiltViewModel
class PromptViewModel @Inject constructor(
    private val prompts: PromptRepository,
    private val settings: UserSettings,
    entitlements: Entitlements,
    private val store: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(PromptUiState())
    val state: StateFlow<PromptUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = settings.currentPrefs()
            val lang = p.lastLang.takeIf { it in p.langs } ?: p.langs.first()
            val track = p.trackFor(lang)
            _state.update {
                it.copy(
                    langs = p.langs, lang = lang, tracks = p.tracksFor(lang), track = track,
                    structureHint = p.structureHint, transliteration = p.transliteration,
                    prompt = prompts.next(lang, track, null),
                )
            }
            refreshLeft()
        }
        viewModelScope.launch { entitlements.isPremium.collect { premium -> _state.update { it.copy(premium = premium) } } }
        // Counts follow the done set (a prompt marked done on a report, or "bring them back" in Settings).
        viewModelScope.launch { store.donePrompts.collect { refreshLeft() } }
    }

    private suspend fun refreshLeft() {
        val s = _state.value
        val (left, total) = prompts.remaining(s.lang, s.track)
        _state.update { it.copy(left = left, total = total) }
    }

    fun onEvent(e: PromptEvent) {
        viewModelScope.launch {
            when (e) {
                is PromptEvent.SelectLang -> {
                    if (e.lang == _state.value.lang) return@launch
                    settings.setLang(e.lang)
                    val p = settings.currentPrefs()
                    val track = p.trackFor(e.lang)
                    _state.update { it.copy(lang = e.lang, tracks = p.tracksFor(e.lang), track = track, fresh = false, prompt = prompts.next(e.lang, track, null)) }
                    refreshLeft()
                }
                is PromptEvent.SelectTrack -> {
                    if (e.track == _state.value.track) return@launch
                    val lang = _state.value.lang
                    settings.setTrack(lang, e.track)
                    _state.update { it.copy(track = e.track, fresh = false, prompt = prompts.next(lang, e.track, null)) }
                    refreshLeft()
                }
                PromptEvent.Shuffle -> {
                    val s = _state.value
                    _state.update { it.copy(prompt = prompts.next(s.lang, s.track, s.prompt?.id), fresh = true) }
                }
                is PromptEvent.ToggleStructure -> {
                    settings.setStructureHint(e.on)
                    _state.update { it.copy(structureHint = e.on) }
                }
                is PromptEvent.ToggleTranslit -> {
                    settings.setTransliteration(e.on)
                    _state.update { it.copy(transliteration = e.on) }
                }
                PromptEvent.MarkDone -> {
                    val s = _state.value
                    val current = s.prompt ?: return@launch
                    store.setPromptDone(current.id, true)
                    _state.update { it.copy(prompt = prompts.next(s.lang, s.track, current.id), fresh = true) }
                }
            }
        }
    }
}
