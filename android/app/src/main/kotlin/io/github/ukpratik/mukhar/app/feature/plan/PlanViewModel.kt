package io.github.ukpratik.mukhar.app.feature.plan

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.progress.shortDate
import io.github.ukpratik.mukhar.app.nav.PlanRoute
import io.github.ukpratik.mukhar.data.analytics.Analytics
import io.github.ukpratik.mukhar.data.analytics.AnalyticsEvent
import io.github.ukpratik.mukhar.data.billing.PlanOffer
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.repo.EntitlementEvent
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.billing.EntitlementRules
import io.github.ukpratik.mukhar.domain.billing.EntitlementSnapshot
import io.github.ukpratik.mukhar.domain.billing.Plan
import io.github.ukpratik.mukhar.domain.billing.Prices
import io.github.ukpratik.mukhar.domain.billing.SubscriptionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanUiState(
    val loading: Boolean = true,
    val offers: List<PlanOffer> = emptyList(),
    val selected: Plan? = null,
    val loadFailed: Boolean = false,
    val snapshot: EntitlementSnapshot = EntitlementSnapshot.FREE,
    val status: SubscriptionStatus = SubscriptionStatus.FREE,
    val trialDay: Int? = null,
    val banner: TrialBannerUi? = null,
    val confirming: Boolean = false,
    val busy: Boolean = false,
) {
    val selectedOffer: PlanOffer? get() = offers.firstOrNull { it.plan == selected }

    /** Premium (any state with access) shows the Plan facts; free and expired see the paywall (S18). */
    val showPaywall: Boolean get() = status == SubscriptionStatus.FREE || status == SubscriptionStatus.EXPIRED
}

sealed interface PlanEffect {
    data class Toast(@param:StringRes val message: Int, val arg: String? = null) : PlanEffect
    data object Close : PlanEffect
    data object OpenManage : PlanEffect
}

/** S18 (US-061, US-062): the honest paywall, the purchase flow, restore, and the Plan facts once subscribed. */
@HiltViewModel
class PlanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val entitlements: EntitlementRepository,
    private val settings: SettingsStore,
    private val analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {
    private val source = runCatching { savedState.toRoute<PlanRoute>().source }.getOrDefault(PlanRoute.SOURCE_ME)
    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()
    private val effects = Channel<PlanEffect>(Channel.BUFFERED)
    val effect: Flow<PlanEffect> = effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(entitlements.snapshot, entitlements.confirming, settings.trialBannerDismissedDay) { e, c, d -> Triple(e, c, d) }
                .collect { (e, confirming, dismissed) ->
                    val now = clock.now()
                    _state.update {
                        it.copy(
                            snapshot = e,
                            status = EntitlementRules.status(e, now),
                            trialDay = EntitlementRules.trialDay(e, now),
                            banner = TrialBannerUi.from(e, now, clock.zone(), dismissed),
                            confirming = confirming,
                        )
                    }
                }
        }
        viewModelScope.launch { entitlements.events.collect(::onEntitlementEvent) }
        viewModelScope.launch {
            val e = entitlements.current()
            if (!EntitlementRules.isPremium(e, clock.now())) analytics.log(AnalyticsEvent.paywallViewed(source))
            load()
        }
    }

    fun onSelect(plan: Plan) = _state.update { it.copy(selected = plan) }

    fun onRetry() {
        viewModelScope.launch { load() }
    }

    fun onBuy(activity: Activity?) {
        val offer = _state.value.selectedOffer ?: return
        if (activity == null || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            if (!entitlements.purchase(activity, offer)) _state.update { it.copy(busy = false) }
        }
    }

    fun onRestore() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch { entitlements.restore() }
    }

    fun onKeep() {
        viewModelScope.launch { settings.dismissTrialBanner(clock.now().atZone(clock.zone()).toLocalDate()) }
    }

    fun onManage() {
        effects.trySend(PlanEffect.OpenManage)
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        when (val r = entitlements.plans()) {
            is Outcome.Ok -> _state.update { s ->
                s.copy(loading = false, offers = r.value, selected = s.selected ?: preselect(r.value))
            }
            is Outcome.Err -> _state.update { it.copy(loading = false, loadFailed = true) }
        }
    }

    private fun onEntitlementEvent(e: EntitlementEvent) {
        _state.update { it.copy(busy = false) }
        when (e) {
            is EntitlementEvent.Activated -> {
                val s = e.snapshot
                val reminder = EntitlementRules.reminderAt(s, clock.now())
                if (reminder != null) {
                    effects.trySend(PlanEffect.Toast(R.string.paywall_toast_trial, shortDate(reminder.atZone(clock.zone()).toLocalDate())))
                } else {
                    effects.trySend(PlanEffect.Toast(R.string.paywall_toast_on))
                }
                effects.trySend(PlanEffect.Close)
            }
            EntitlementEvent.Pending -> effects.trySend(PlanEffect.Toast(R.string.paywall_toast_pending))
            EntitlementEvent.Confirming -> effects.trySend(PlanEffect.Toast(R.string.paywall_toast_confirming))
            EntitlementEvent.Failed -> effects.trySend(PlanEffect.Toast(R.string.paywall_toast_failed))
            EntitlementEvent.NothingToRestore -> effects.trySend(PlanEffect.Toast(R.string.paywall_toast_nothing))
            EntitlementEvent.Cancelled -> Unit
        }
    }

    companion object {
        /** Honest default (S18 notes): the cheapest per-month plan. */
        fun preselect(offers: List<PlanOffer>): Plan? =
            offers.minByOrNull { Prices.perMonthMicros(it.priceMicros, it.plan.period) }?.plan
    }
}
