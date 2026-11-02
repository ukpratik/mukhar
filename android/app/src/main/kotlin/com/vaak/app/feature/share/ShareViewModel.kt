package com.vaak.app.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaak.data.analytics.Analytics
import com.vaak.data.analytics.AnalyticsEvent
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.repo.ProgressData
import com.vaak.data.repo.ProgressRepository
import com.vaak.domain.share.CardMetrics
import com.vaak.domain.share.ShareCardRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val loading: Boolean = true,
    /** Baseline vs. latest with the toggles applied; null when there's nothing comparable to share. */
    val model: ShareCardModel? = null,
    val base: ShareCardModel? = null,
    /** The current streak, when there is one to show. */
    val streak: Int? = null,
    val firstName: String? = null,
    val showStreak: Boolean = true,
    val addName: Boolean = false,
)

/** S21 (US-071): streak shown and name hidden by default; toggles update the preview instantly. */
@HiltViewModel
class ShareViewModel @Inject constructor(
    progress: ProgressRepository,
    settings: SettingsStore,
    private val analytics: Analytics,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = progress.observe().first()
            val name = settings.current().firstName?.trim()?.takeIf(String::isNotEmpty)
            val base = modelOf(data)
            _state.value = applyToggles(
                ShareUiState(loading = false, base = base, streak = data.currentStreak.takeIf { it > 0 }, firstName = name),
            )
        }
    }

    fun onShowStreak(on: Boolean) = _state.update { applyToggles(it.copy(showStreak = on)) }

    fun onAddName(on: Boolean) = _state.update { applyToggles(it.copy(addName = on)) }

    fun onShared() = analytics.log(AnalyticsEvent.shareCardShared)

    companion object {
        /** Baseline vs. the latest talk in its language; only improved or steady metrics (max 3). */
        fun modelOf(data: ProgressData): ShareCardModel? {
            val b = data.baseline ?: return null
            val l = data.latestComparable ?: return null
            val rows = ShareCardRules.metrics(CardMetrics(b.wpm, b.fillersPerMin, b.mattr), CardMetrics(l.wpm, l.fillersPerMin, l.mattr), b.lang)
            if (rows.isEmpty()) return null
            return ShareCardModel(b.lang, b.day, l.day, ShareCardRules.weeks(b.day, l.day), rows, streak = null, firstName = null)
        }

        fun applyToggles(s: ShareUiState): ShareUiState = s.copy(
            model = s.base?.copy(
                streak = if (s.showStreak) s.streak else null,
                firstName = if (s.addName) s.firstName else null,
            ),
        )
    }
}
