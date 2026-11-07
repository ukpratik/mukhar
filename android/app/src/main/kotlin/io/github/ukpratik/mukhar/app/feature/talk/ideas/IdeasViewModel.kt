package io.github.ukpratik.mukhar.app.feature.talk.ideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ukpratik.mukhar.data.byok.IdeasRequest
import io.github.ukpratik.mukhar.data.byok.SpeakingIdeas
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.data.ideas.IdeasResult
import io.github.ukpratik.mukhar.data.ideas.SpeakingIdeasRepository
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.domain.ideas.GuideTrack
import io.github.ukpratik.mukhar.domain.ideas.SpeakingGuide
import io.github.ukpratik.mukhar.domain.ideas.SpeakingGuideBuilder
import io.github.ukpratik.mukhar.domain.prompt.Prompt
import io.github.ukpratik.mukhar.domain.prompt.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The AI half of the sheet (US-078 b). */
sealed interface AiIdeasUi {
    /** No Anthropic key: one locked line, no upsell beyond it (D-039). */
    data object Locked : AiIdeasUi
    data object Ready : AiIdeasUi
    data object Loading : AiIdeasUi
    data class Shown(val ideas: SpeakingIdeas) : AiIdeasUi
    /** The free guide stays; this adds the one-line reason. */
    data class Failed(val reason: IdeasFailure) : AiIdeasUi
}

data class IdeasUiState(
    val prompt: Prompt? = null,
    /** Styles to pick from: the prompt's talk track plus content creation / word power when they're the user's goals. */
    val tracks: List<GuideTrack> = emptyList(),
    val track: GuideTrack = GuideTrack.EVERYDAY,
    val guide: SpeakingGuide? = null,
    val ai: AiIdeasUi = AiIdeasUi.Locked,
)

sealed interface IdeasEvent {
    data class SelectTrack(val track: GuideTrack) : IdeasEvent
    /** "Write me ideas" and "Regenerate": always a new call. */
    data object Write : IdeasEvent
}

/**
 * "Need ideas?" (US-078, D-046): the free guide from [SpeakingGuideBuilder] (offline, every user) and AI ideas with
 * the user's own Anthropic key, cached per prompt and track. Used by the prompt screen and Recording ("peek").
 */
@HiltViewModel
class IdeasViewModel @Inject constructor(
    private val ideas: SpeakingIdeasRepository,
    private val words: WordRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(IdeasUiState())
    val state: StateFlow<IdeasUiState> = _state.asStateFlow()
    private var userWords: List<String> = emptyList()
    private var job: Job? = null

    /** Loads the sheet for [prompt] in its [talkTrack]; a no-op when it's already showing that prompt. */
    fun load(prompt: Prompt, talkTrack: Track) {
        if (_state.value.prompt?.id == prompt.id && _state.value.guide != null) return
        job?.cancel()
        job = viewModelScope.launch {
            val goals = settings.current().tracks
            val base = GuideTrack.of(talkTrack)
            val extra = listOf(GoalTrack.CONTENT_CREATION to GuideTrack.CONTENT_CREATION, GoalTrack.WORD_POWER to GuideTrack.WORD_POWER)
                .filter { it.first in goals }.map { it.second }
            userWords = words.wordsToTry(prompt.lang, SpeakingGuideBuilder.WORDS_TO_TRY)
            _state.value = IdeasUiState(prompt = prompt, tracks = listOf(base) + extra, track = base)
            refresh()
        }
    }

    fun onEvent(e: IdeasEvent) {
        when (e) {
            is IdeasEvent.SelectTrack -> {
                if (e.track == _state.value.track) return
                _state.update { it.copy(track = e.track) }
                viewModelScope.launch { refresh() }
            }
            IdeasEvent.Write -> write()
        }
    }

    private suspend fun refresh() {
        val s = _state.value
        val prompt = s.prompt ?: return
        val guide = SpeakingGuideBuilder.build(prompt, s.track, userWords)
        val ai = when {
            !ideas.hasKey() -> AiIdeasUi.Locked
            else -> ideas.cached(prompt.id, s.track)?.let { AiIdeasUi.Shown(it) } ?: AiIdeasUi.Ready
        }
        _state.update { it.copy(guide = guide, ai = ai) }
    }

    private fun write() {
        val s = _state.value
        val prompt = s.prompt ?: return
        if (s.ai == AiIdeasUi.Loading || s.ai == AiIdeasUi.Locked) return
        _state.update { it.copy(ai = AiIdeasUi.Loading) }
        viewModelScope.launch {
            val request = IdeasRequest(prompt.id, prompt.lang, s.track, prompt.difficulty, prompt.text, prompt.prep, prompt.tip, userWords)
            val ai = when (val r = ideas.generate(request)) {
                is IdeasResult.Ok -> AiIdeasUi.Shown(r.ideas)
                IdeasResult.NoKey -> AiIdeasUi.Locked
                is IdeasResult.Failed -> AiIdeasUi.Failed(r.reason)
            }
            // The style may have changed while waiting: only show the answer for the style it was written for.
            _state.update { if (it.track == s.track) it.copy(ai = ai) else it }
        }
    }
}
