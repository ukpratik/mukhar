package io.github.ukpratik.mukhar.app.feature.talk.report

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.nav.ReportRoute
import io.github.ukpratik.mukhar.data.content.PromptRepository
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.prefs.UserSettings
import io.github.ukpratik.mukhar.data.analytics.Analytics
import io.github.ukpratik.mukhar.data.analytics.AnalyticsEvent
import io.github.ukpratik.mukhar.data.repo.Entitlements
import io.github.ukpratik.mukhar.data.repo.HarvestResult
import io.github.ukpratik.mukhar.data.repo.TalkRepository
import io.github.ukpratik.mukhar.data.repo.TalkStatus
import io.github.ukpratik.mukhar.data.repo.capSeconds
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.metrics.MetricAverages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReportEvent {
    data object RetryFeedback : ReportEvent
    data object RetryTranscript : ReportEvent

    /** ADR-008: a talk waiting for an engine is analysed again from its saved audio (through the queue). */
    data object Reanalyse : ReportEvent
    data object Discard : ReportEvent
    data class Rate(val value: Int) : ReportEvent
    data class ToggleWord(val word: UpgradedWord, val example: String) : ReportEvent
    data class ToggleSave(val line: BetterUi) : ReportEvent
    data class SendReport(val reason: ReportReason, val consent: Boolean, val note: String) : ReportEvent
}

sealed interface ReportEffect {
    data class Toast(@StringRes val message: Int, val undo: ReportEvent? = null) : ReportEffect
    data object Added : ReportEffect
}

/** S10/S11 (LLD §5 UDF): state from Room flows, so the analysing status survives process death. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val talks: TalkRepository,
    private val prompts: PromptRepository,
    private val settings: UserSettings,
    entitlements: Entitlements,
    private val analytics: Analytics,
) : ViewModel() {
    private var feedbackShownLogged = false
    private val id = savedState.toRoute<ReportRoute>().sessionId
    private val manualRetryFailed = MutableStateFlow(false)
    private val retrying = MutableStateFlow(false)
    private val effects = Channel<ReportEffect>(Channel.BUFFERED)
    val effect: Flow<ReportEffect> = effects.receiveAsFlow()

    private var averages: MetricAverages? = null
    private var averagesLoaded = false
    private var promptText: String? = null

    private val base = combine(talks.observeTalk(id), settings.prefs, entitlements.isPremium, manualRetryFailed) { d, prefs, premium, failed ->
        if (d == null) return@combine null
        if (!averagesLoaded) {
            averages = talks.previousAverages(d.lang, d.session.createdAt)
            averagesLoaded = true
        }
        if (promptText == null) promptText = prompts.byId(d.session.promptId)?.text.orEmpty()
        ReportMapper.map(d, averages, promptText.orEmpty(), prefs.stutterFriendly, capSeconds(premium), failed)
    }.filterNotNull()

    val state: StateFlow<ReportUiState> = base.flatMapLatest { s ->
        combine(talks.observeCollectedWords(id), talks.observeSavedLines(id), retrying) { words, lines, busy ->
            s.copy(
                collectedWordIds = words.toSet(),
                savedLines = lines.mapNotNull { it.substringAfterLast(':').toIntOrNull() }.toSet(),
                retrying = busy,
            )
        }
    }.onEach { s ->
        if (s.phase == ReportPhase.READY && !feedbackShownLogged) {
            feedbackShownLogged = true
            analytics.log(AnalyticsEvent.feedbackShown)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState(sessionId = id))

    fun onEvent(e: ReportEvent) {
        viewModelScope.launch {
            when (e) {
                ReportEvent.RetryFeedback -> {
                    retrying.value = true
                    talks.analyze(id)
                    manualRetryFailed.value = talks.session(id)?.status == TalkStatus.FEEDBACK_FAILED
                    retrying.value = false
                }
                ReportEvent.RetryTranscript -> talks.analyze(id)
                ReportEvent.Reanalyse -> talks.analyzeRecovered(id)
                ReportEvent.Discard -> talks.discard(id)
                is ReportEvent.Rate -> {
                    talks.rate(id, e.value)
                    analytics.log(AnalyticsEvent.feedbackRated(e.value))
                }
                is ReportEvent.ToggleWord -> {
                    val s = state.value
                    val meta = s.meta ?: return@launch
                    when (val r = talks.toggleWord(id, meta.lang, meta.track.key, e.word, e.example)) {
                        null -> Unit // undone
                        HarvestResult.AlreadyInGym -> effects.send(ReportEffect.Toast(R.string.toast_already_in_gym))
                        is HarvestResult.Added -> {
                            effects.send(ReportEffect.Added)
                            analytics.log(AnalyticsEvent.wordAdded("talk"))
                            // Over the 5-a-day cap the word waits for a later day (US-021).
                            effects.send(ReportEffect.Toast(if (r.carriedOver) R.string.toast_added_later else R.string.toast_added, undo = e))
                        }
                    }
                }
                is ReportEvent.ToggleSave -> {
                    val meta = state.value.meta ?: return@launch
                    if (talks.toggleSavedLine(id, meta.lang, e.line.index, e.line.quote, e.line.rewrite)) effects.send(ReportEffect.Added)
                }
                is ReportEvent.SendReport -> {
                    val r = talks.report(id, e.reason.key, e.consent, e.note)
                    if (r is Outcome.Ok) analytics.log(AnalyticsEvent.feedbackReported(e.reason.key))
                    effects.send(ReportEffect.Toast(if (r is Outcome.Ok) R.string.toast_reported else R.string.toast_report_failed))
                }
            }
        }
    }

}
