package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.domain.talk.TalkThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Premium or free (US-060): sets the talk cap (3:00 vs 2:00). Billing and server verification arrive in M5. */
interface Entitlements {
    val isPremium: Flow<Boolean>
}

/** Until M5 everyone is on the free plan. Debug builds can flip it in the debug menu. */
object FreePlan : Entitlements {
    override val isPremium: Flow<Boolean> = flowOf(false)
}

/** Talk length cap. The 2-minute free cap exists only for Mukhar Cloud; with Cloud off every talk gets 3 minutes (D-039). */
fun capSeconds(premium: Boolean): Int =
    if (premium || !io.github.ukpratik.mukhar.domain.analysis.AnalysisMode.CLOUD_ENABLED) TalkThresholds.PREMIUM_CAP_S else TalkThresholds.FREE_CAP_S
