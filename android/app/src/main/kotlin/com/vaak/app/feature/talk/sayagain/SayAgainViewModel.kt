package com.vaak.app.feature.talk.sayagain

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vaak.app.feature.talk.report.GrowUi
import com.vaak.app.feature.talk.report.ReportEvent
import com.vaak.app.feature.talk.report.ReportMapper
import com.vaak.app.feature.talk.report.ReportUiState
import com.vaak.app.nav.SayAgainRoute
import com.vaak.data.prefs.UserSettings
import com.vaak.data.repo.Entitlements
import com.vaak.data.repo.TalkDetails
import com.vaak.data.repo.TalkRepository
import com.vaak.data.repo.capSeconds
import com.vaak.domain.feedback.UpgradedWord
import com.vaak.domain.metrics.Metrics
import com.vaak.domain.metrics.MetricsCalculator
import com.vaak.domain.metrics.WpmBand
import com.vaak.domain.model.Lang
import com.vaak.domain.talk.SayItAgain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

enum class DeltaKind { FILLERS, PAUSES, PACE }

enum class Chip { FEWER, SAME, MORE, STILL_COMFORTABLE, FASTER_COMFORTABLE, SLOWER_COMFORTABLE, NOW_COMFORTABLE, FASTER, SLOWER }

/** One row of S12 "What changed": an arrow plus words, glow only when it improved (never red, never "worse"). */
data class DeltaRowUi(val kind: DeltaKind, val first: String, val retake: String, val chip: Chip, val good: Boolean, val percent: Int = 0)

data class SayAgainUiState(
    val parentId: String = "",
    val promptId: String = "",
    val track: String = "",
    val lang: Lang = Lang.EN,
    val retakeReport: ReportUiState? = null,
    val deltas: SayItAgain.Deltas? = null,
    val rows: List<DeltaRowUi> = emptyList(),
    val retakeMs: Long = 0,
    val capMs: Long = 0,
    val words: List<UpgradedWord> = emptyList(),
    val collectedIds: Set<String> = emptySet(),
    val creditDay: Int? = null,
    val nextTime: GrowUi? = null,
    val premium: Boolean = false,
) {
    fun collected(w: UpgradedWord) = TalkRepository.wordId(lang, w.word) in collectedIds
}

/** S12 (US-017): compares the re-take with its first take on fillers, long pauses and pace. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SayAgainViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val talks: TalkRepository,
    settings: UserSettings,
    entitlements: Entitlements,
) : ViewModel() {
    val retakeId = savedState.toRoute<SayAgainRoute>().retakeId

    val state: StateFlow<SayAgainUiState> = talks.observeTalk(retakeId).filterNotNull().flatMapLatest { retake ->
        val parentId = retake.session.parentId
        val parentFlow = if (parentId != null) talks.observeTalk(parentId) else flowOf(null)
        val wordsFlow = if (parentId != null) talks.observeCollectedWords(parentId) else flowOf(emptyList())
        combine(parentFlow, wordsFlow, settings.prefs, entitlements.isPremium) { parent, words, prefs, premium ->
            build(retake, parent, words.toSet(), prefs.stutterFriendly, premium)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SayAgainUiState())

    fun onReportEvent(e: ReportEvent) {
        viewModelScope.launch {
            when (e) {
                ReportEvent.RetryTranscript, ReportEvent.RetryFeedback -> talks.analyze(retakeId)
                ReportEvent.Discard -> talks.discard(retakeId)
                else -> Unit
            }
        }
    }

    fun toggleWord(w: UpgradedWord) {
        val s = state.value
        if (s.parentId.isEmpty()) return
        viewModelScope.launch { talks.toggleWord(s.parentId, s.lang, s.track, w, "") }
    }

    companion object {
        fun build(retake: TalkDetails, parent: TalkDetails?, collected: Set<String>, stutter: Boolean, premium: Boolean): SayAgainUiState {
            val lang = retake.lang
            val tierCap = capSeconds(premium)
            val report = ReportMapper.map(retake, null, "", stutter, tierCap)
            val base = SayAgainUiState(
                parentId = parent?.session?.id.orEmpty(),
                promptId = retake.session.promptId,
                track = retake.session.track,
                lang = lang,
                retakeReport = report,
                premium = premium,
            )
            val rw = retake.words?.takeIf { it.isNotEmpty() } ?: return base
            val pw = parent?.words?.takeIf { it.isNotEmpty() } ?: return base
            val r = MetricsCalculator.compute(rw, lang, stutter)
            val p = MetricsCalculator.compute(pw, lang, stutter)
            val (low, high) = TalkRepository.bandLimits(lang)
            val deltas = SayItAgain.deltas(p, r, low, high)
            val words = parent.feedback?.feedback?.betterVersions.orEmpty().flatMap { it.upgradedWords }.distinctBy { it.word.lowercase() }
            return base.copy(
                deltas = deltas,
                rows = rows(p, r, deltas, stutter),
                retakeMs = retake.session.durationMs.toLong(),
                capMs = SayItAgain.capSeconds(parent.session.durationMs.toLong(), tierCap) * 1000,
                words = words,
                collectedIds = collected,
                creditDay = (retake.credit ?: parent.credit)?.streak,
                nextTime = retake.feedback?.feedback?.grow?.let { GrowUi(it.quote, it.why, it.how) },
            )
        }

        fun rows(first: Metrics, retake: Metrics, d: SayItAgain.Deltas, stutter: Boolean): List<DeltaRowUi> {
            val out = mutableListOf(downRow(DeltaKind.FILLERS, first.fillersPerMin, retake.fillersPerMin, d.fillersImproved))
            if (!stutter) {
                out += downRow(DeltaKind.PAUSES, first.longPausesPerMin ?: 0.0, retake.longPausesPerMin ?: 0.0, d.pausesImproved)
            }
            out += paceRow(first, retake, d.paceImproved)
            return out
        }

        private fun downRow(kind: DeltaKind, first: Double, retake: Double, improved: Boolean): DeltaRowUi {
            val chip = when {
                improved -> Chip.FEWER
                SayItAgain.aboutSame(first, retake) -> Chip.SAME
                retake > first -> Chip.MORE
                else -> Chip.SAME
            }
            val pct = SayItAgain.reduction(first, retake)?.let { (it * 100).roundToInt() } ?: 0
            return DeltaRowUi(kind, ReportMapper.one(first), ReportMapper.one(retake), chip, improved, pct)
        }

        private fun paceRow(first: Metrics, retake: Metrics, improved: Boolean): DeltaRowUi {
            val comfortableNow = retake.wpmBand == WpmBand.COMFORTABLE
            val chip = when {
                improved && comfortableNow -> Chip.NOW_COMFORTABLE
                comfortableNow && first.wpmBand == WpmBand.COMFORTABLE && SayItAgain.aboutSame(first.wpm, retake.wpm) -> Chip.STILL_COMFORTABLE
                comfortableNow && retake.wpm > first.wpm -> Chip.FASTER_COMFORTABLE
                comfortableNow && retake.wpm < first.wpm -> Chip.SLOWER_COMFORTABLE
                comfortableNow -> Chip.STILL_COMFORTABLE
                retake.wpm > first.wpm -> Chip.FASTER
                else -> Chip.SLOWER
            }
            return DeltaRowUi(DeltaKind.PACE, first.wpm.toInt().toString(), retake.wpm.toInt().toString(), chip, improved)
        }
    }
}
