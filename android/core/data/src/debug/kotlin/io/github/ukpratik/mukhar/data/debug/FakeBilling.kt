package io.github.ukpratik.mukhar.data.debug

import android.app.Activity
import io.github.ukpratik.mukhar.data.billing.AccountServer
import io.github.ukpratik.mukhar.data.billing.BillingRepository
import io.github.ukpratik.mukhar.data.billing.LaunchResult
import io.github.ukpratik.mukhar.data.billing.PlanOffer
import io.github.ukpratik.mukhar.data.billing.PlayPurchase
import io.github.ukpratik.mukhar.data.billing.PurchaseUpdate
import io.github.ukpratik.mukhar.data.billing.ServerEntitlement
import io.github.ukpratik.mukhar.data.billing.ServerStatus
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.Stage
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.billing.BillingPeriod
import io.github.ukpratik.mukhar.domain.billing.Plan
import io.github.ukpratik.mukhar.domain.billing.PlanCatalog
import io.github.ukpratik.mukhar.domain.billing.Prices
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

/** Play's subscription states, as subscriptionsv2 names them (the Worker's `mapSubscription` reads these). */
enum class FakeState { ACTIVE, CANCELED, EXPIRED }

@Serializable
data class FakeSubscription(
    val token: String,
    val plan: Plan,
    val expiryMs: Long,
    val isTrial: Boolean,
    val autoRenewing: Boolean,
    val acknowledged: Boolean,
    val state: FakeState,
)

/** Where the fake Play account keeps its state (SharedPreferences in the app, memory in tests). */
interface FakeStorePersistence {
    fun load(): String?
    fun save(json: String?)

    class InMemory : FakeStorePersistence {
        private var value: String? = null
        override fun load() = value
        override fun save(json: String?) {
            value = json
        }
    }
}

/**
 * A fake Google Play account for debug builds and tests (build plan "Debug hooks"): one subscription that renews,
 * cancels and expires on the injected [Clock], like Play would. It survives "Delete all my data" (it isn't ours),
 * which is how restore on reinstall is demoed. The trial-expiry override moves [FakeSubscription.expiryMs].
 */
class FakePlayStore(private val clock: Clock, private val persistence: FakeStorePersistence = FakeStorePersistence.InMemory()) {
    private val json = Json { ignoreUnknownKeys = true }

    var subscription: FakeSubscription? = persistence.load()?.let { runCatching { json.decodeFromString<FakeSubscription>(it) }.getOrNull() }
        private set(value) {
            field = value
            persistence.save(value?.let { json.encodeToString(FakeSubscription.serializer(), it) })
        }

    /** Play's free-trial eligibility: new customers only. */
    val trialEligible: Boolean get() = subscription == null

    fun buy(plan: Plan, withTrial: Boolean): FakeSubscription {
        val now = clock.now().atZone(clock.zone())
        val expiry = if (withTrial) now.plusDays(PlanCatalog.TRIAL_DAYS) else next(now, plan.period)
        val sub = FakeSubscription(
            token = "fake-" + UUID.randomUUID(),
            plan = plan,
            expiryMs = expiry.toInstant().toEpochMilli(),
            isTrial = withTrial,
            autoRenewing = true,
            acknowledged = false,
            state = FakeState.ACTIVE,
        )
        subscription = sub
        return sub
    }

    /** Cancel in Play: auto-renew off, Premium stays until expiry (Play state CANCELED). */
    fun cancel() = update { it.copy(autoRenewing = false, state = FakeState.CANCELED) }

    /** Turn auto-renew back on ("Resubscribe" in Play). */
    fun resume() = update { it.copy(autoRenewing = true, state = FakeState.ACTIVE) }

    /** The debug trial-expiry override: the current period ends [fromNow] from now. */
    fun endsIn(fromNow: Duration) = update { it.copy(expiryMs = clock.now().plus(fromNow).toEpochMilli()) }

    fun acknowledge(token: String) = update { if (it.token == token) it.copy(acknowledged = true) else it }

    /** "A new Play account": forget the subscription (trial eligible again). */
    fun clear() {
        subscription = null
    }

    /** Renewals and expiry as time passes: a renewing subscription rolls into paid periods; otherwise it expires. */
    fun current(): FakeSubscription? {
        var sub = subscription ?: return null
        val now = clock.now().toEpochMilli()
        if (sub.expiryMs > now || sub.state == FakeState.EXPIRED) return sub
        if (sub.autoRenewing) {
            var expiry = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(sub.expiryMs), clock.zone())
            while (expiry.toInstant().toEpochMilli() <= now) expiry = next(expiry, sub.plan.period)
            sub = sub.copy(expiryMs = expiry.toInstant().toEpochMilli(), isTrial = false, state = FakeState.ACTIVE)
        } else {
            sub = sub.copy(state = FakeState.EXPIRED)
        }
        subscription = sub
        return sub
    }

    /** What the Worker's `/entitlement` would answer for [token] (mirrors `mapSubscription`). */
    fun verify(token: String): ServerEntitlement? {
        val sub = current()?.takeIf { it.token == token } ?: return null
        return entitlementOf(sub)
    }

    fun entitlementOf(sub: FakeSubscription): ServerEntitlement {
        val premium = sub.state != FakeState.EXPIRED && sub.expiryMs > clock.now().toEpochMilli()
        return ServerEntitlement(premium, sub.expiryMs, premium && sub.isTrial, sub.autoRenewing)
    }

    private fun update(f: (FakeSubscription) -> FakeSubscription) {
        subscription = subscription?.let(f)
    }

    private fun next(from: ZonedDateTime, period: BillingPeriod): ZonedDateTime = from.plusMonths(period.months.toLong())
}

/** [BillingRepository] on [FakePlayStore]: the purchase "sheet" completes at once with a fake token. */
class FakeBillingRepository(private val store: FakePlayStore) : BillingRepository {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: SharedFlow<PurchaseUpdate> = _updates.asSharedFlow()

    /** Set by tests and the debug menu to simulate the user backing out of the Play sheet. */
    var nextLaunchCancelled = false

    override suspend fun plans(): Outcome<List<PlanOffer>> = Outcome.Ok(
        Plan.entries.map { plan ->
            val micros = PlanCatalog.LIST_PRICE_MICROS.getValue(plan)
            val trial = store.trialEligible
            PlanOffer(
                plan = plan,
                priceMicros = micros,
                currencyCode = "INR",
                formattedPrice = Prices.format(micros),
                trialDays = if (trial) PlanCatalog.TRIAL_DAYS.toInt() else null,
                offerToken = "fake:${plan.basePlanId}:${if (trial) PlanCatalog.TRIAL_OFFER_ID else "base"}",
            )
        },
    )

    override suspend fun launch(activity: Activity, offer: PlanOffer): LaunchResult {
        buy(offer.plan, offer.trialDays != null)
        return LaunchResult.STARTED
    }

    /** The purchase without an Activity (tests). Emits the same update Play's listener would. */
    fun buy(plan: Plan, withTrial: Boolean): PurchaseUpdate {
        val update = if (nextLaunchCancelled) {
            nextLaunchCancelled = false
            PurchaseUpdate.Cancelled
        } else {
            PurchaseUpdate.Purchased(listOf(store.buy(plan, withTrial).toPlay()))
        }
        _updates.tryEmit(update)
        return update
    }

    override suspend fun activePurchases(): Outcome<List<PlayPurchase>> {
        val sub = store.current()?.takeIf { it.state != FakeState.EXPIRED } ?: return Outcome.Ok(emptyList())
        return Outcome.Ok(listOf(sub.toPlay()))
    }

    override suspend fun acknowledge(token: String): Boolean {
        store.acknowledge(token)
        return true
    }

    private fun FakeSubscription.toPlay() = PlayPurchase(
        token = token,
        productId = PlanCatalog.PRODUCT_ID,
        purchased = true,
        acknowledged = acknowledged,
        autoRenewing = autoRenewing,
        plan = plan,
    )
}

/** [AccountServer] answered from [FakePlayStore] (no Worker, no network). */
class FakeAccountServer(private val store: FakePlayStore) : AccountServer {
    var deleteCalls = 0
        private set

    override suspend fun verify(purchaseToken: String, productId: String): Outcome<ServerEntitlement> =
        store.verify(purchaseToken)?.let { Outcome.Ok(it) } ?: Outcome.Err(AppError.Upstream(Stage.SERVER, "PURCHASE_INVALID"))

    override suspend fun status(): Outcome<ServerStatus> {
        val e = store.current()?.let(store::entitlementOf) ?: ServerEntitlement(false, null, false, false)
        return Outcome.Ok(ServerStatus(e, quota = null))
    }

    override suspend fun deleteMe(): Outcome<Unit> {
        deleteCalls++
        return Outcome.Ok(Unit)
    }
}
