package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.db.ProgressDao
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.quota.Decision
import io.github.ukpratik.mukhar.domain.quota.QuotaKind
import io.github.ukpratik.mukhar.domain.quota.QuotaMirror
import io.github.ukpratik.mukhar.domain.quota.QuotaRequest
import io.github.ukpratik.mukhar.domain.quota.Tier
import io.github.ukpratik.mukhar.domain.quota.Usage
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class TalkQuotaStatus(
    val exhausted: Boolean,
    val used: Int,
    val limit: Int,
    val premium: Boolean,
    /** The next quota day starts here: 03:00 IST, shown in the user's local time (S17). */
    val resetsAt: Instant,
    /** The server refused a talk today (it waits, queued, for the next quota day). */
    val serverRefused: Boolean,
)

/**
 * The client mirror of the daily talk quota (US-060), for S17 only: the server decides. Counts today's coached talks
 * on the device (quota day = 03:00 IST), takes the server's count when it's higher, and treats a QUOTA_EXHAUSTED
 * refusal as used up. Re-takes and the baseline never count (D-031).
 */
@Singleton
class TalkQuota @Inject constructor(
    private val dao: ProgressDao,
    private val settings: SettingsStore,
    private val entitlements: Entitlements,
    private val clock: Clock,
) {
    /** Whether the server quota applies (only Mukhar Cloud, off in R1). Tests switch it on to cover the cloud rules. */
    @androidx.annotation.VisibleForTesting
    internal var serverQuota: suspend () -> Boolean = { settings.analysisMode.first().usesServerQuota }

    suspend fun status(): TalkQuotaStatus {
        val now = clock.now()
        // D-039: no daily limits on the phone or on the user's own keys. Only Mukhar Cloud (off in R1) has a quota.
        if (!serverQuota()) {
            return TalkQuotaStatus(
                exhausted = false, used = 0, limit = Int.MAX_VALUE, premium = entitlements.isPremium.first(),
                resetsAt = QuotaMirror.resetsAt(QuotaMirror.quotaDay(now)), serverRefused = false,
            )
        }
        val day = QuotaMirror.quotaDay(now)
        val from = QuotaMirror.resetsAt(day.minusDays(1)).toEpochMilli()
        val to = QuotaMirror.resetsAt(day).toEpochMilli()
        val local = dao.talksBetween(from, to)
        val server = settings.serverTalkQuota.first()?.takeIf { it.day == day }?.used ?: 0
        val refused = dao.quotaRefusedSince(from) > 0
        val premium = entitlements.isPremium.first()
        val used = maxOf(local, server)
        val tier = if (premium) Tier.PREMIUM else Tier.FREE
        val request = QuotaRequest(uid = LOCAL_UID, tier = tier, now = now, kind = QuotaKind.TALK, durationMs = 0)
        val decision = QuotaMirror.decide(request, { d -> if (d == day) Usage(uidTalks = used) else Usage() })
        val status = QuotaMirror.status(tier, now, Usage(uidTalks = used))
        return TalkQuotaStatus(
            exhausted = decision is Decision.Deny || refused,
            used = used,
            limit = status.talksLimit,
            premium = premium,
            resetsAt = status.resetsAt,
            serverRefused = refused,
        )
    }

    private companion object {
        const val LOCAL_UID = "device"
    }
}
