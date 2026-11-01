package com.vaak.data.repo

import com.vaak.domain.talk.TalkThresholds
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

fun capSeconds(premium: Boolean): Int = if (premium) TalkThresholds.PREMIUM_CAP_S else TalkThresholds.FREE_CAP_S
