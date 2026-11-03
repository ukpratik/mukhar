package io.github.ukpratik.mukhar.domain.quota

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class Tier { FREE, PREMIUM }

enum class QuotaKind { TALK, RETAKE, CHECK, BASELINE }

/** Whether this uid or this device hash already used its one free baseline (D-031). */
data class BaselineUsed(val uid: Boolean = false, val device: Boolean = false)

/** Server limits (D1 `config`, LLD §2.2). */
data class Limits(
    val freeTalksPerDay: Int = 1,
    val freeTalkMaxS: Int = 120,
    val premiumTalksPerDay: Int = 20,
    val premiumTalkMaxS: Int = 180,
    val freeChecksPerDay: Int = 10,
    val premiumChecksPerDay: Int = 200,
    val checkMaxS: Int = 15,
)

/** Usage on one quota day, per user and per device (the larger counts, so reinstalls don't reset quota). */
data class Usage(val uidTalks: Int = 0, val deviceTalks: Int = 0, val uidChecks: Int = 0, val deviceChecks: Int = 0) {
    val talks: Int get() = maxOf(uidTalks, deviceTalks)
    val checks: Int get() = maxOf(uidChecks, deviceChecks)
}

data class ParentSession(val uid: String, val kind: String, val retakeUsed: Boolean)

data class QuotaRequest(
    val uid: String,
    val tier: Tier,
    val now: Instant,
    val kind: QuotaKind,
    val durationMs: Long,
    val queued: Boolean = false,
    val parent: ParentSession? = null,
    val baselineUsed: BaselineUsed = BaselineUsed(),
)

enum class DenyCode { QUOTA_EXHAUSTED, AUDIO_TOO_LONG, SESSION_STATE, BASELINE_USED }

sealed interface Decision {
    /** [debitDay] is the quota day charged, or `null` when free (re-takes). */
    data class Allow(val debitDay: LocalDate?) : Decision
    data class Deny(val code: DenyCode, val retryAfterS: Long? = null) : Decision
}

data class QuotaStatus(
    val day: LocalDate,
    val talksUsed: Int,
    val talksLimit: Int,
    val checksUsed: Int,
    val checksLimit: Int,
    val resetsAt: Instant,
)

/**
 * Client mirror of the Worker's quota rules (US-060, US-017, LLD §4.7), for display and offline hints only:
 * the server is the source of truth. Shares `shared/test-vectors/quota.json` with the Worker.
 * The quota day resets at 03:00 IST = 21:30 UTC, independent of the device clock's zone.
 */
object QuotaMirror {
    private val SHIFT: Duration = Duration.ofMinutes(150) // +2h30: 21:30Z becomes midnight
    private const val GRACE_MS = 5_000L

    fun quotaDay(now: Instant): LocalDate = now.plus(SHIFT).atOffset(ZoneOffset.UTC).toLocalDate()

    /** End of [day]: 21:30Z on that calendar date. */
    fun resetsAt(day: LocalDate): Instant = day.atTime(21, 30).toInstant(ZoneOffset.UTC)

    fun decide(request: QuotaRequest, usage: (LocalDate) -> Usage, limits: Limits = Limits()): Decision {
        val premium = request.tier == Tier.PREMIUM
        val capS = when (request.kind) {
            QuotaKind.CHECK -> limits.checkMaxS
            else -> if (premium) limits.premiumTalkMaxS else limits.freeTalkMaxS
        }
        if (request.durationMs > capS * 1000L + GRACE_MS) return Decision.Deny(DenyCode.AUDIO_TOO_LONG)

        val today = quotaDay(request.now)
        val retryAfter = { Duration.between(request.now, resetsAt(today)).seconds.coerceAtLeast(0) }
        return when (request.kind) {
            // D-031: one free baseline per uid AND per device; never debits the daily quota.
            QuotaKind.BASELINE ->
                if (request.baselineUsed.uid || request.baselineUsed.device) Decision.Deny(DenyCode.BASELINE_USED)
                else Decision.Allow(debitDay = null)
            QuotaKind.RETAKE -> {
                val parent = request.parent
                if (parent != null && parent.uid == request.uid && parent.kind == "talk" && !parent.retakeUsed) {
                    Decision.Allow(debitDay = null)
                } else {
                    Decision.Deny(DenyCode.SESSION_STATE)
                }
            }
            QuotaKind.TALK -> {
                val limit = if (premium) limits.premiumTalksPerDay else limits.freeTalksPerDay
                val tomorrow = today.plusDays(1)
                when {
                    usage(today).talks < limit -> Decision.Allow(today)
                    request.queued && usage(tomorrow).talks < limit -> Decision.Allow(tomorrow)
                    else -> Decision.Deny(DenyCode.QUOTA_EXHAUSTED, retryAfter())
                }
            }
            QuotaKind.CHECK -> {
                val limit = if (premium) limits.premiumChecksPerDay else limits.freeChecksPerDay
                if (usage(today).checks < limit) Decision.Allow(today) else Decision.Deny(DenyCode.QUOTA_EXHAUSTED, retryAfter())
            }
        }
    }

    fun status(tier: Tier, now: Instant, usage: Usage, limits: Limits = Limits()): QuotaStatus {
        val day = quotaDay(now)
        val premium = tier == Tier.PREMIUM
        return QuotaStatus(
            day = day,
            talksUsed = usage.talks,
            talksLimit = if (premium) limits.premiumTalksPerDay else limits.freeTalksPerDay,
            checksUsed = usage.checks,
            checksLimit = if (premium) limits.premiumChecksPerDay else limits.freeChecksPerDay,
            resetsAt = resetsAt(day),
        )
    }
}
