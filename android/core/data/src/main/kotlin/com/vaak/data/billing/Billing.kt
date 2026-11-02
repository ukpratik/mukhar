package com.vaak.data.billing

import android.app.Activity
import android.net.Uri
import androidx.core.net.toUri
import com.vaak.data.net.Outcome
import com.vaak.data.prefs.ServerTalkQuota
import com.vaak.domain.billing.Plan
import com.vaak.domain.billing.PlanCatalog
import kotlinx.coroutines.flow.SharedFlow

/** One purchasable plan as Play prices it for this user (S18). [trialDays] is null when the user isn't eligible. */
data class PlanOffer(
    val plan: Plan,
    val priceMicros: Long,
    val currencyCode: String,
    /** Play's own formatted price, shown as-is. */
    val formattedPrice: String,
    val trialDays: Int?,
    /** The offer token to launch: the free-trial offer when eligible, otherwise the base plan. */
    val offerToken: String,
)

/** A subscription purchase as Play reports it. [plan] is known only to the debug fake (Play doesn't say). */
data class PlayPurchase(
    val token: String,
    val productId: String,
    val purchased: Boolean,
    val acknowledged: Boolean,
    val autoRenewing: Boolean,
    val plan: Plan? = null,
)

sealed interface PurchaseUpdate {
    data class Purchased(val purchases: List<PlayPurchase>) : PurchaseUpdate
    data object Cancelled : PurchaseUpdate

    /** Paid with a slow method (cash, some UPI flows): Premium starts once Play confirms. */
    data object Pending : PurchaseUpdate
    data class Failed(val code: Int) : PurchaseUpdate
}

enum class LaunchResult { STARTED, UNAVAILABLE }

/**
 * Google Play Billing behind a seam (US-061): products, the purchase flow, acknowledge, and the active purchases
 * used for restore on reinstall. Debug builds can swap in `FakeBillingRepository` (no Play account needed).
 */
interface BillingRepository {
    /** Results of [launch] (and any purchase Play pushes later, such as a pending one completing). */
    val updates: SharedFlow<PurchaseUpdate>

    suspend fun plans(): Outcome<List<PlanOffer>>

    suspend fun launch(activity: Activity, offer: PlanOffer): LaunchResult

    /** Active subscriptions on this Play account (restore). An error means Play couldn't be asked. */
    suspend fun activePurchases(): Outcome<List<PlayPurchase>>

    suspend fun acknowledge(token: String): Boolean
}

/** The Worker's verified view of a subscription. */
data class ServerEntitlement(val premium: Boolean, val expiryMs: Long?, val isTrial: Boolean, val autoRenewing: Boolean)

data class ServerStatus(val entitlement: ServerEntitlement, val quota: ServerTalkQuota?)

/** The account endpoints of the Worker (LLD §3): `/entitlement`, `GET /me`, `DELETE /me`. */
interface AccountServer {
    suspend fun verify(purchaseToken: String, productId: String): Outcome<ServerEntitlement>
    suspend fun status(): Outcome<ServerStatus>
    suspend fun deleteMe(): Outcome<Unit>
}

object PlayLinks {
    /** "Manage" (US-062): one tap to Play's subscription page for Vaak. */
    fun manageSubscription(packageName: String, productId: String = PlanCatalog.PRODUCT_ID): Uri =
        "https://play.google.com/store/account/subscriptions?sku=$productId&package=$packageName".toUri()
}
