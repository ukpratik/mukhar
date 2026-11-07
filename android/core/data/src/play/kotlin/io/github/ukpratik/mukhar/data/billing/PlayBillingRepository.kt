package io.github.ukpratik.mukhar.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.domain.billing.Plan
import io.github.ukpratik.mukhar.domain.billing.PlanCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [BillingRepository] on Google Play Billing Library 9 (US-061). One subscription product with three base plans and
 * a free-trial offer each ([PlanCatalog]). Verification happens on the Worker; this class only talks to Play.
 */
@Singleton
class PlayBillingRepository @Inject constructor(@param:ApplicationContext context: Context) : BillingRepository {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: SharedFlow<PurchaseUpdate> = _updates.asSharedFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connectMutex = Mutex()
    private val details = mutableMapOf<String, ProductDetails>()

    override suspend fun plans(): Outcome<List<PlanOffer>> {
        if (!connect()) return Outcome.Err(AppError.Upstream(io.github.ukpratik.mukhar.data.net.Stage.SERVER, BILLING_UNAVAILABLE))
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PlanCatalog.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            ),
        ).build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            MukharLog.i("billing: product details ${result.billingResult.responseCode}")
            return Outcome.Err(errorFor(result.billingResult.responseCode))
        }
        val product = result.productDetailsList?.firstOrNull { it.productId == PlanCatalog.PRODUCT_ID }
            ?: return Outcome.Err(AppError.Upstream(io.github.ukpratik.mukhar.data.net.Stage.SERVER, "PRODUCT_MISSING"))
        details[product.productId] = product
        return Outcome.Ok(offersOf(product))
    }

    override suspend fun launch(activity: Activity, offer: PlanOffer): LaunchResult {
        val product = details[PlanCatalog.PRODUCT_ID] ?: run {
            plans()
            details[PlanCatalog.PRODUCT_ID]
        } ?: return LaunchResult.UNAVAILABLE
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product)
                    .setOfferToken(offer.offerToken)
                    .build(),
            ),
        ).build()
        val r = client.launchBillingFlow(activity, params)
        return if (r.responseCode == BillingClient.BillingResponseCode.OK) LaunchResult.STARTED else LaunchResult.UNAVAILABLE
    }

    override suspend fun activePurchases(): Outcome<List<PlayPurchase>> {
        if (!connect()) return Outcome.Err(errorFor(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE))
        val r = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return Outcome.Err(errorFor(r.billingResult.responseCode))
        return Outcome.Ok(r.purchasesList.flatMap(::toPlay))
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!connect()) return false
        val r = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
        return r.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val update = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                if (list.any { it.purchaseState == Purchase.PurchaseState.PENDING } && list.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    PurchaseUpdate.Pending
                } else {
                    PurchaseUpdate.Purchased(list.flatMap(::toPlay))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
            else -> PurchaseUpdate.Failed(result.responseCode)
        }
        _updates.tryEmit(update)
    }

    private suspend fun connect(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont: CancellableContinuation<Boolean> ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    companion object {
        const val BILLING_UNAVAILABLE = "BILLING_UNAVAILABLE"

        private fun errorFor(code: Int): AppError = when (code) {
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            -> AppError.Offline
            else -> AppError.Upstream(io.github.ukpratik.mukhar.data.net.Stage.SERVER, BILLING_UNAVAILABLE)
        }

        private fun toPlay(p: Purchase): List<PlayPurchase> = p.products.map { productId ->
            PlayPurchase(
                token = p.purchaseToken,
                productId = productId,
                purchased = p.purchaseState == Purchase.PurchaseState.PURCHASED,
                acknowledged = p.isAcknowledged,
                autoRenewing = p.isAutoRenewing,
            )
        }

        /** Base-plan price from the recurring phase; trial days from a free first phase of the trial offer. */
        fun offersOf(product: ProductDetails): List<PlanOffer> {
            val all = product.subscriptionOfferDetails.orEmpty()
            return Plan.entries.mapNotNull { plan ->
                val base = all.firstOrNull { it.basePlanId == plan.basePlanId && it.offerId == null } ?: return@mapNotNull null
                val recurring = base.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                val trial = all.firstOrNull { it.basePlanId == plan.basePlanId && it.offerId == PlanCatalog.TRIAL_OFFER_ID }
                val trialPhase = trial?.pricingPhases?.pricingPhaseList?.firstOrNull { it.priceAmountMicros == 0L }
                PlanOffer(
                    plan = plan,
                    priceMicros = recurring.priceAmountMicros,
                    currencyCode = recurring.priceCurrencyCode,
                    formattedPrice = recurring.formattedPrice,
                    trialDays = trialPhase?.let { days(it.billingPeriod) },
                    offerToken = (if (trialPhase != null) trial.offerToken else base.offerToken),
                )
            }
        }

        private fun days(iso: String): Int? = runCatching { Period.parse(iso).let { it.days + it.months * 30 + it.years * 365 } }.getOrNull()
    }
}
