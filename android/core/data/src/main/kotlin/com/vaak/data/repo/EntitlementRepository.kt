package com.vaak.data.repo

import android.app.Activity
import com.vaak.data.analytics.Analytics
import com.vaak.data.analytics.AnalyticsEvent
import com.vaak.data.billing.AccountServer
import com.vaak.data.billing.BillingRepository
import com.vaak.data.billing.LaunchResult
import com.vaak.data.billing.PlanOffer
import com.vaak.data.billing.PlayPurchase
import com.vaak.data.billing.PurchaseUpdate
import com.vaak.data.billing.ServerEntitlement
import com.vaak.data.net.AppError
import com.vaak.data.net.Outcome
import com.vaak.data.net.errorCode
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.util.VaakLog
import com.vaak.data.work.AccountSync
import com.vaak.data.work.TrialReminderScheduler
import com.vaak.domain.Clock
import com.vaak.domain.billing.EntitlementRules
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.domain.billing.Plan
import com.vaak.domain.billing.PlanCatalog
import com.vaak.domain.billing.SubscriptionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a purchase or restore led to, for the paywall's toast and close (S18 notes). */
sealed interface EntitlementEvent {
    data class Activated(val snapshot: EntitlementSnapshot) : EntitlementEvent
    data object Pending : EntitlementEvent
    data object Cancelled : EntitlementEvent

    /** Verification couldn't finish now; it's queued and retried when online ("Confirming…"). */
    data object Confirming : EntitlementEvent
    data object Failed : EntitlementEvent
    data object NothingToRestore : EntitlementEvent
}

enum class RefreshResult { UPDATED, NONE, RETRY, UNAVAILABLE }

/**
 * The plan (US-060 to US-062). Play is asked for purchases; the Worker verifies each token with subscriptionsv2 and
 * returns the entitlement, which is cached in DataStore (LLD §2.1) and drives the tier: [isPremium] feeds the
 * recording cap (Premium 3:00), the quota mirror and the sentence-check limit. The client acknowledges a purchase
 * only after the Worker verified it. The trial reminder is scheduled at expiry − 24 h unless auto-renew is off.
 */
class EntitlementRepository(
    private val billing: BillingRepository,
    private val server: AccountServer,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val reminders: TrialReminderScheduler,
    private val sync: AccountSync,
    private val analytics: Analytics,
    scope: CoroutineScope,
) : Entitlements {
    private val mutex = Mutex()
    private var pendingPlan: Plan? = null
    private var pendingPrice: String? = null

    private val _confirming = MutableStateFlow(false)

    /** A purchase Play accepted but the Worker hasn't verified yet ("Confirming…" chip). */
    val confirming: StateFlow<Boolean> = _confirming.asStateFlow()

    private val _events = MutableSharedFlow<EntitlementEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EntitlementEvent> = _events.asSharedFlow()

    val snapshot: Flow<EntitlementSnapshot> = settings.entitlement

    override val isPremium: Flow<Boolean> = snapshot.map { EntitlementRules.isPremium(it, clock.now()) }

    init {
        scope.launch { billing.updates.collect { onUpdate(it) } }
    }

    suspend fun current(): EntitlementSnapshot = settings.entitlement.first()

    suspend fun status(): SubscriptionStatus = EntitlementRules.status(current(), clock.now())

    suspend fun plans(): Outcome<List<PlanOffer>> = billing.plans()

    /** Starts Play's purchase sheet; the result arrives through [BillingRepository.updates]. */
    suspend fun purchase(activity: Activity, offer: PlanOffer): Boolean {
        pendingPlan = offer.plan
        pendingPrice = offer.formattedPrice
        val launched = billing.launch(activity, offer) == LaunchResult.STARTED
        if (!launched) _events.tryEmit(EntitlementEvent.Failed)
        return launched
    }

    suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchased -> {
                val subs = update.purchases.filter { it.purchased && it.productId == PlanCatalog.PRODUCT_ID }
                if (subs.isEmpty()) return
                _confirming.value = true
                val result = mutex.withLock { verifyAndSave(subs.last(), fresh = true) }
                _confirming.value = result == RefreshResult.RETRY
                if (result == RefreshResult.RETRY) sync.enqueueEntitlementRefresh()
                _events.tryEmit(
                    when (result) {
                        RefreshResult.UPDATED -> EntitlementEvent.Activated(current())
                        RefreshResult.RETRY -> EntitlementEvent.Confirming
                        else -> EntitlementEvent.Failed
                    },
                )
            }
            PurchaseUpdate.Pending -> _events.tryEmit(EntitlementEvent.Pending)
            PurchaseUpdate.Cancelled -> _events.tryEmit(EntitlementEvent.Cancelled)
            is PurchaseUpdate.Failed -> _events.tryEmit(EntitlementEvent.Failed)
        }
    }

    /**
     * Restore on reinstall and the app-start refresh (LLD §4.9): asks Play for active subscriptions and verifies each
     * with the Worker. No subscription on the account → free. Play unreachable → the Worker's `/me` view (it
     * re-verifies lazily), else the cache stays. Network failures queue a retry.
     */
    suspend fun refresh(scheduleRetry: Boolean = true): RefreshResult = mutex.withLock {
        val result = when (val r = billing.activePurchases()) {
            is Outcome.Err -> {
                VaakLog.i("entitlement: play ${r.error.errorCode}")
                fromServer() ?: RefreshResult.UNAVAILABLE
            }
            is Outcome.Ok -> {
                val subs = r.value.filter { it.purchased && it.productId == PlanCatalog.PRODUCT_ID }
                if (subs.isEmpty()) {
                    val cached = current()
                    if (cached.premium) save(cached.copy(premium = false, verifiedAtMs = clock.now().toEpochMilli()))
                    RefreshResult.NONE
                } else {
                    verifyAndSave(subs.last(), fresh = false)
                }
            }
        }
        if (result == RefreshResult.RETRY && scheduleRetry) sync.enqueueEntitlementRefresh()
        _confirming.value = _confirming.value && result == RefreshResult.RETRY
        syncQuota()
        result
    }

    /** "Restore purchase" on the paywall. */
    suspend fun restore(): RefreshResult {
        val r = refresh()
        _events.tryEmit(
            when (r) {
                RefreshResult.UPDATED -> if (EntitlementRules.isPremium(current(), clock.now())) EntitlementEvent.Activated(current()) else EntitlementEvent.NothingToRestore
                RefreshResult.NONE -> EntitlementEvent.NothingToRestore
                RefreshResult.RETRY -> EntitlementEvent.Confirming
                RefreshResult.UNAVAILABLE -> EntitlementEvent.Failed
            },
        )
        return r
    }

    /** After a reboot or an update: the alarm is gone, plan it again from the cache. */
    suspend fun rescheduleReminder() = planReminder(current())

    private suspend fun verifyAndSave(p: PlayPurchase, fresh: Boolean): RefreshResult {
        return when (val v = server.verify(p.token, p.productId)) {
            is Outcome.Err -> {
                VaakLog.i("entitlement: verify ${v.error.errorCode}")
                if (isRetryable(v.error)) RefreshResult.RETRY else RefreshResult.UNAVAILABLE
            }
            is Outcome.Ok -> {
                val before = current()
                val plan = p.plan ?: (if (fresh) pendingPlan else null) ?: before.plan
                val price = (if (fresh) pendingPrice else null) ?: before.priceLabel.takeIf { plan == before.plan }
                val next = v.value.toSnapshot(plan, price)
                save(next)
                if (!p.acknowledged && next.premium) billing.acknowledge(p.token)
                if (fresh && next.premium) {
                    if (next.isTrial) analytics.log(AnalyticsEvent.trialStarted)
                    analytics.log(AnalyticsEvent.purchaseCompleted(plan?.basePlanId ?: "unknown"))
                }
                val wasRenewing = before.premium && before.autoRenewing
                if (!fresh && wasRenewing && next.premium && !next.autoRenewing) analytics.log(AnalyticsEvent.subscriptionCancelled)
                pendingPlan = null
                pendingPrice = null
                RefreshResult.UPDATED
            }
        }
    }

    private suspend fun fromServer(): RefreshResult? {
        val r = server.status() as? Outcome.Ok ?: return null
        r.value.quota?.let { settings.setServerTalkQuota(it) }
        val e = r.value.entitlement
        if (!e.premium && e.expiryMs == null && current().expiryMs == null) return RefreshResult.NONE
        save(e.toSnapshot(current().plan, current().priceLabel))
        return RefreshResult.UPDATED
    }

    /** Best effort: the server's quota count for S17. Fails quietly until auth lands. */
    private suspend fun syncQuota() {
        val r = server.status() as? Outcome.Ok ?: return
        r.value.quota?.let { settings.setServerTalkQuota(it) }
    }

    private suspend fun save(e: EntitlementSnapshot) {
        settings.setEntitlement(e)
        planReminder(e)
    }

    /** US-062: one alarm at expiry − 24 h while a trial will convert; none if auto-renew is off. */
    private fun planReminder(e: EntitlementSnapshot) {
        val at = EntitlementRules.reminderAt(e, clock.now())
        if (at == null) reminders.cancel() else reminders.schedule(at)
    }

    private fun ServerEntitlement.toSnapshot(plan: Plan?, price: String?) = EntitlementSnapshot(
        premium = premium,
        expiryMs = expiryMs,
        isTrial = isTrial,
        autoRenewing = autoRenewing,
        plan = plan,
        priceLabel = price,
        verifiedAtMs = clock.now().toEpochMilli(),
    )

    private fun isRetryable(e: AppError) = e is AppError.Offline || e is AppError.Busy || e is AppError.Auth ||
        e is AppError.Integrity || (e is AppError.Upstream && e.code != "PURCHASE_INVALID")
}
