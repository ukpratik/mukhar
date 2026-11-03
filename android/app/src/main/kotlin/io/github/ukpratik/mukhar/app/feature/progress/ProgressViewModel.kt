package io.github.ukpratik.mukhar.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.data.repo.ProgressRepository
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.progress.TrendRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S16 (US-050, US-051): Room flows + the plan, mapped by [ProgressMapper]. */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progress: ProgressRepository,
    entitlements: EntitlementRepository,
    settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    private val range = MutableStateFlow(TrendRange.TWELVE)
    private val lang = MutableStateFlow<Lang?>(null)

    val state: StateFlow<ProgressUiState> = combine(progress.observe(), entitlements.snapshot, settings.settings, range, lang) { data, ent, s, r, l ->
        ProgressMapper.map(data, l, r, ent, clock.now(), clock.zone(), s.talksEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), ProgressUiState())

    init {
        // New milestones since the last visit become insight cards (each fires once).
        viewModelScope.launch { progress.evaluateInsights() }
    }

    fun onRange(r: TrendRange) {
        range.value = r
    }

    fun onLang(l: Lang) {
        lang.value = l
    }

    fun onDismissInsight(key: String) {
        viewModelScope.launch { progress.dismissInsight(key) }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
