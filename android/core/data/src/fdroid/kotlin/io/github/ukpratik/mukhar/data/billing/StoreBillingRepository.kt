package io.github.ukpratik.mukhar.data.billing

import android.app.Activity
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * F-Droid flavor: no Google Play Billing (proprietary, not allowed on F-Droid). There are no paid plans in this
 * build, so purchases are never available and there is nothing to restore (D-039, D-044).
 */
class StoreBillingRepository @Inject constructor() : BillingRepository {
    override val updates: SharedFlow<PurchaseUpdate> = MutableSharedFlow<PurchaseUpdate>().asSharedFlow()
    override suspend fun plans(): Outcome<List<PlanOffer>> = Outcome.Err(AppError.Offline)
    override suspend fun launch(activity: Activity, offer: PlanOffer): LaunchResult = LaunchResult.UNAVAILABLE
    override suspend fun activePurchases(): Outcome<List<PlayPurchase>> = Outcome.Ok(emptyList())
    override suspend fun acknowledge(token: String): Boolean = false
}
