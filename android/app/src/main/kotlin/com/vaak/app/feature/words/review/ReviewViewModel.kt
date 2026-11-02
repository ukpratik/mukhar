package com.vaak.app.feature.words.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vaak.app.nav.ReviewRoute
import com.vaak.data.audio.Recorder
import com.vaak.data.audio.RecorderState
import com.vaak.data.audio.RecordingFiles
import com.vaak.data.net.AppError
import com.vaak.data.net.Outcome
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.repo.CheckQuota
import com.vaak.data.repo.ReviewCard
import com.vaak.data.repo.ReviewMode
import com.vaak.data.repo.SentenceCheck
import com.vaak.data.repo.SentenceChecker
import com.vaak.data.repo.WordRepository
import com.vaak.data.system.ConnectivityObserver
import com.vaak.data.tts.Speaker
import com.vaak.domain.Clock
import com.vaak.domain.fsrs.Rating
import com.vaak.domain.model.Lang
import com.vaak.domain.revise.CardType
import com.vaak.domain.streak.StreakEngine
import com.vaak.domain.words.SentenceCheckRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class SentencePhase { IDLE, RECORDING, CHECKING, RESULT, FAILED }

enum class SentenceFailure { OFFLINE, QUOTA, TOO_SHORT, ERROR }

data class SentenceUi(
    val phase: SentencePhase = SentencePhase.IDLE,
    val elapsedMs: Long = 0,
    val result: SentenceCheck? = null,
    val failure: SentenceFailure? = null,
)

/** S15 state 4 (and the Word Gym's end, S13 notes). [streakDay] is set when ≥ 5 cards counted for the day. */
data class DoneUi(
    val cards: Int,
    val elapsedMs: Long,
    val words: Int,
    val fixes: Int,
    val lines: Int,
    val streakDay: Int?,
    val dueTomorrow: Int,
    val more: Int,
)

data class ReviewUiState(
    val loading: Boolean = true,
    val mode: ReviewMode = ReviewMode.GYM,
    val cards: List<ReviewCard> = emptyList(),
    val index: Int = 0,
    val flipped: Boolean = false,
    val intervals: Map<Rating, Duration> = emptyMap(),
    val sentence: SentenceUi = SentenceUi(),
    val online: Boolean = true,
    val talksEnabled: Boolean = true,
    val quota: CheckQuota = CheckQuota(0, 10),
    val voices: Map<Lang, Boolean> = emptyMap(),
    val done: DoneUi? = null,
) {
    val card: ReviewCard? get() = cards.getOrNull(index)
    val total: Int get() = cards.size

    /** Sentence checks need a word card, a network, talks on (they upload audio) and quota (S13 notes). */
    val canCheck: Boolean
        get() = card?.word != null && online && talksEnabled && !quota.exhausted
}

sealed interface ReviewEvent {
    data object Flip : ReviewEvent
    data class Grade(val rating: Rating) : ReviewEvent
    data object StartSentence : ReviewEvent
    data object StopSentence : ReviewEvent
    data object CancelSentence : ReviewEvent
    data object RetrySentence : ReviewEvent
    data class Speak(val text: String, val lang: Lang) : ReviewEvent
    data object KeepGoing : ReviewEvent
}

sealed interface ReviewEffect {
    /** The 5th graded card credited the day (S15 notes: a small "Today counts" toast). */
    data object TodayCounts : ReviewEffect
    data object Confirm : ReviewEffect
}

/**
 * S13 Word Gym and S15 quick revision (US-022, US-023, US-040): one FSRS session over [WordRepository.queue].
 * Grades write the card and `review_log` immediately, so closing saves progress (no dialog). The 5th grade credits
 * the streak day. "Say it in a sentence" records ≤ 15 s with the app's [Recorder] (auto-stops after 1.5 s of
 * silence) and checks it through [SentenceChecker]; offline or with talks off the user self-grades.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val words: WordRepository,
    private val checker: SentenceChecker,
    private val recorder: Recorder,
    private val files: RecordingFiles,
    private val speaker: Speaker,
    private val clock: Clock,
    settings: SettingsStore,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val route = savedState.toRoute<ReviewRoute>()
    private val mode = if (route.mode == ReviewRoute.REVISE) ReviewMode.REVISE else ReviewMode.GYM
    private val _state = MutableStateFlow(ReviewUiState(mode = mode))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private val effects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effect: Flow<ReviewEffect> = effects.receiveAsFlow()

    private val startedAt: Instant = clock.now()
    private var shownAt: Instant = startedAt
    private var graded = 0
    private val gradedTypes = mutableListOf<CardType>()
    private var streakDay: Int? = null
    private var recording: Job? = null

    init {
        speaker.warmUp()
        viewModelScope.launch { connectivity.isOnline.collect { on -> _state.update { it.copy(online = on) } } }
        viewModelScope.launch { settings.settings.collect { s -> _state.update { it.copy(talksEnabled = s.talksEnabled) } } }
        viewModelScope.launch { checker.quota.collect { q -> _state.update { it.copy(quota = q) } } }
        viewModelScope.launch { speaker.voices.collect { v -> _state.update { it.copy(voices = v) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val cards = words.queue(mode, route.wordId)
        _state.update { it.copy(loading = false, cards = cards, index = 0, flipped = startsFlipped(cards.firstOrNull()), sentence = SentenceUi()) }
        onCardShown()
    }

    /** Saved lines have no hidden side: read aloud, then grade (S15 state 3). */
    private fun startsFlipped(card: ReviewCard?) = card?.type == CardType.SAVED_LINE

    private suspend fun onCardShown() {
        shownAt = clock.now()
        val card = _state.value.card ?: return finish()
        _state.update { it.copy(intervals = words.preview(card.id)) }
    }

    fun onEvent(e: ReviewEvent) {
        when (e) {
            ReviewEvent.Flip -> _state.update { it.copy(flipped = true) }
            is ReviewEvent.Grade -> viewModelScope.launch { grade(e.rating) }
            ReviewEvent.StartSentence -> startSentence()
            ReviewEvent.StopSentence -> viewModelScope.launch { stopSentence() }
            ReviewEvent.CancelSentence -> cancelSentence()
            ReviewEvent.RetrySentence -> _state.update { it.copy(sentence = SentenceUi()) }
            is ReviewEvent.Speak -> speaker.speak(e.text, e.lang)
            ReviewEvent.KeepGoing -> viewModelScope.launch {
                _state.update { it.copy(loading = true, done = null) }
                load()
            }
        }
    }

    private suspend fun grade(rating: Rating) {
        val s = _state.value
        val card = s.card ?: return
        if (s.sentence.phase == SentencePhase.RECORDING) cancelSentence()
        words.grade(card.id, rating, Duration.between(shownAt, clock.now()).toMillis())
        graded++
        gradedTypes += card.type
        effects.send(ReviewEffect.Confirm)
        if (graded == StreakEngine.MIN_REVIEW_CARDS) {
            // US-031: the 5th grade credits the day immediately, even if the user then closes (S15 notes).
            val credit = words.creditReview(graded)
            streakDay = credit.streak.takeIf { it > 0 }
            if (credit.creditedDay != null) effects.send(ReviewEffect.TodayCounts)
        }
        val next = s.index + 1
        _state.update { it.copy(index = next, flipped = startsFlipped(it.cards.getOrNull(next)), sentence = SentenceUi()) }
        onCardShown()
    }

    private suspend fun finish() {
        if (graded == 0 && _state.value.cards.isEmpty()) return
        val more = words.queue(mode).size
        _state.update {
            it.copy(
                done = DoneUi(
                    cards = graded,
                    elapsedMs = Duration.between(startedAt, clock.now()).toMillis(),
                    words = gradedTypes.count { t -> t == CardType.WORD_MEANING || t == CardType.MEANING_WORD },
                    fixes = gradedTypes.count { t -> t == CardType.MISTAKE_FIX },
                    lines = gradedTypes.count { t -> t == CardType.SAVED_LINE },
                    streakDay = streakDay,
                    dueTomorrow = words.dueTomorrow(),
                    more = more,
                ),
            )
        }
    }

    // ---- Say it in a sentence (US-023) ----

    private fun startSentence() {
        val s = _state.value
        if (!s.canCheck || s.sentence.phase == SentencePhase.RECORDING) return
        val file = files.fileFor("check-${UUID.randomUUID()}")
        if (!recorder.start(file, SentenceCheckRules.MAX_CLIP_MS)) {
            _state.update { it.copy(sentence = SentenceUi(SentencePhase.FAILED, failure = SentenceFailure.ERROR)) }
            return
        }
        _state.update { it.copy(sentence = SentenceUi(SentencePhase.RECORDING)) }
        recording = viewModelScope.launch {
            recorder.state.collect { r ->
                when (r) {
                    is RecorderState.Active -> {
                        _state.update { it.copy(sentence = it.sentence.copy(elapsedMs = r.elapsedMs)) }
                        if (SilenceRule.shouldStop(r.speechMs, r.waveform)) stopSentence()
                    }
                    is RecorderState.Finished, is RecorderState.Failed -> stopSentence()
                    RecorderState.Idle -> Unit
                }
            }
        }
    }

    private suspend fun stopSentence() {
        if (_state.value.sentence.phase != SentencePhase.RECORDING) return
        _state.update { it.copy(sentence = SentenceUi(SentencePhase.CHECKING)) }
        recording?.cancel()
        val result = recorder.stop()
        recorder.reset()
        val card = _state.value.card
        val word = card?.word
        if (result == null || word == null) {
            result?.file?.delete()
            _state.update { it.copy(sentence = SentenceUi(SentencePhase.FAILED, failure = SentenceFailure.ERROR)) }
            return
        }
        if (result.speechMs < SilenceRule.MIN_SPEECH_MS) {
            result.file.delete()
            _state.update { it.copy(sentence = SentenceUi(SentencePhase.FAILED, failure = SentenceFailure.TOO_SHORT)) }
            return
        }
        val outcome = checker.check(word.lang, word.lemma, word.meaning, result.file, result.durationMs)
        _state.update {
            it.copy(
                sentence = when (outcome) {
                    is Outcome.Ok -> SentenceUi(SentencePhase.RESULT, result = outcome.value)
                    is Outcome.Err -> SentenceUi(SentencePhase.FAILED, failure = failureOf(outcome.error))
                },
            )
        }
    }

    private fun cancelSentence() {
        recording?.cancel()
        viewModelScope.launch {
            recorder.discard()
            recorder.reset()
        }
        _state.update { it.copy(sentence = SentenceUi()) }
    }

    override fun onCleared() {
        if (_state.value.sentence.phase == SentencePhase.RECORDING) {
            recording?.cancel()
            kotlinx.coroutines.runBlocking { recorder.discard() }
            recorder.reset()
        }
    }

    companion object {
        fun failureOf(e: AppError): SentenceFailure = when (e) {
            AppError.Offline -> SentenceFailure.OFFLINE
            is AppError.Quota -> SentenceFailure.QUOTA
            AppError.TooLittleSpeech -> SentenceFailure.TOO_SHORT
            else -> SentenceFailure.ERROR
        }
    }
}

/** Auto-stop on 1.5 s of silence once speech started (S13 notes). Waveform peaks are one per 50 ms, 0..1. */
object SilenceRule {
    const val MIN_SPEECH_MS = 1_000L
    private const val SILENT_FRAMES = 30
    private const val QUIET = 0.04f

    fun shouldStop(speechMs: Long, waveform: List<Float>): Boolean =
        speechMs >= MIN_SPEECH_MS && waveform.size >= SILENT_FRAMES && waveform.takeLast(SILENT_FRAMES).all { it < QUIET }
}
