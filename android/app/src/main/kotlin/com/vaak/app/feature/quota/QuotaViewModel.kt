package com.vaak.app.feature.quota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaak.data.analytics.Analytics
import com.vaak.data.analytics.AnalyticsEvent
import com.vaak.data.db.WordDao
import com.vaak.data.repo.SentenceChecker
import com.vaak.data.repo.TalkQuota
import com.vaak.domain.Clock
import com.vaak.domain.streak.nextCutoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S17 (US-060): opened by the client mirror before a talk, or after the server's QUOTA_EXHAUSTED. */
@HiltViewModel
class QuotaViewModel @Inject constructor(
    quota: TalkQuota,
    wordDao: WordDao,
    checker: SentenceChecker,
    analytics: Analytics,
    clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(QuotaUiState())
    val state: StateFlow<QuotaUiState> = _state.asStateFlow()

    init {
        analytics.log(AnalyticsEvent.quotaHit)
        viewModelScope.launch {
            val q = quota.status()
            _state.update {
                it.copy(
                    loading = false,
                    premium = q.premium,
                    opensAt = q.resetsAt.atZone(clock.zone()).toLocalTime(),
                    talkWaiting = q.serverRefused,
                )
            }
        }
        viewModelScope.launch {
            val cutoff = nextCutoff(clock.now(), clock.zone()).toEpochMilli()
            combine(wordDao.observeDueCount(cutoff), checker.quota) { due, checks -> due to checks }.collect { (due, checks) ->
                _state.update { it.copy(due = due, checksLeft = (checks.limit - checks.used).coerceAtLeast(0), checksLimit = checks.limit) }
            }
        }
    }
}
