package io.github.ukpratik.mukhar.domain.billing

import java.time.Duration
import java.time.Instant
import java.util.Currency
import kotlin.math.roundToLong

enum class BillingPeriod(val iso: String, val months: Int) { MONTH("P1M", 1), YEAR("P1Y", 12) }

/**
 * The Play subscription (US-061, D-008, D-035): one product, [PlanCatalog.PRODUCT_ID] (the id the Worker's
 * `/entitlement` filters on), with three base plans. The founding-member price is its own base plan so the price is
 * kept for as long as the user stays subscribed (Play offers can't discount a renewal forever).
 */
enum class Plan(val basePlanId: String, val period: BillingPeriod) {
    FOUNDING("founding-yearly", BillingPeriod.YEAR),
    YEARLY("yearly", BillingPeriod.YEAR),
    MONTHLY("monthly", BillingPeriod.MONTH);

    companion object {
        fun fromBasePlan(id: String?): Plan? = entries.firstOrNull { it.basePlanId == id }
    }
}

object PlanCatalog {
    const val PRODUCT_ID = "premium"

    /** The 7-day free-trial offer on each base plan (Play: new-customer eligibility only). */
    const val TRIAL_OFFER_ID = "free-trial"
    const val TRIAL_DAYS = 7L

    /** The D-008 prices, used by the debug fake billing only; the paywall shows Play's real prices. */
    val LIST_PRICE_MICROS: Map<Plan, Long> = mapOf(
        Plan.MONTHLY to 249_000_000L,
        Plan.YEARLY to 1_499_000_000L,
        Plan.FOUNDING to 999_000_000L,
    )
}

/** The verified entitlement the Worker returned (POST /entitlement or GET /me), cached on the device. */
data class EntitlementSnapshot(
    val premium: Boolean = false,
    val expiryMs: Long? = null,
    val isTrial: Boolean = false,
    val autoRenewing: Boolean = false,
    /** Remembered from the purchase on this device (Play doesn't say the base plan after a reinstall). */
    val plan: Plan? = null,
    /** Play's formatted price for [plan] at purchase time ("₹1,499"), for the reminder copy. */
    val priceLabel: String? = null,
    val verifiedAtMs: Long? = null,
) {
    companion object {
        val FREE = EntitlementSnapshot()
    }
}

enum class SubscriptionStatus { FREE, TRIAL, TRIAL_CANCELLED, ACTIVE, CANCELLED, EXPIRED }

/**
 * The entitlement state machine (US-061, US-062): trial → active (renewed) → cancelled (Premium until expiry) →
 * expired. Pure; "now" comes from the injected clock so the debug trial-expiry override and tests drive it.
 */
object EntitlementRules {
    /** The notification goes out a day before the charge (US-062). */
    val REMINDER_LEAD: Duration = Duration.ofHours(24)

    /** The in-app banner shows from trial day 6, i.e. the last 48 h of a 7-day trial (US-062, D-016). */
    val BANNER_LEAD: Duration = Duration.ofHours(48)

    fun isPremium(s: EntitlementSnapshot, now: Instant): Boolean =
        s.premium && s.expiryMs != null && s.expiryMs > now.toEpochMilli()

    fun status(s: EntitlementSnapshot, now: Instant): SubscriptionStatus = when {
        s.expiryMs == null -> SubscriptionStatus.FREE
        !isPremium(s, now) -> SubscriptionStatus.EXPIRED
        s.isTrial && s.autoRenewing -> SubscriptionStatus.TRIAL
        s.isTrial -> SubscriptionStatus.TRIAL_CANCELLED
        s.autoRenewing -> SubscriptionStatus.ACTIVE
        else -> SubscriptionStatus.CANCELLED
    }

    /** A charge is coming: a trial that will convert. A cancelled trial (`autoRenewing` false) gets no reminder. */
    private fun chargeAhead(s: EntitlementSnapshot, now: Instant) = status(s, now) == SubscriptionStatus.TRIAL

    /** When to post the trial notification (expiry − 24 h), or null when none is due (US-062). */
    fun reminderAt(s: EntitlementSnapshot, now: Instant): Instant? {
        if (!chargeAhead(s, now)) return null
        return Instant.ofEpochMilli(s.expiryMs!!).minus(REMINDER_LEAD)
    }

    /** The day-6 banner on Today and Plan: the guarantee, shown even when notifications are denied. */
    fun bannerVisible(s: EntitlementSnapshot, now: Instant): Boolean {
        if (!chargeAhead(s, now)) return false
        return !now.isBefore(Instant.ofEpochMilli(s.expiryMs!!).minus(BANNER_LEAD))
    }

    /** Trial day N of 7 (1-based), for the Plan screen. */
    fun trialDay(s: EntitlementSnapshot, now: Instant): Int? {
        val expiry = s.expiryMs ?: return null
        if (!s.isTrial) return null
        val start = Instant.ofEpochMilli(expiry).minus(Duration.ofDays(PlanCatalog.TRIAL_DAYS))
        val day = Duration.between(start, now).toDays() + 1
        return day.coerceIn(1, PlanCatalog.TRIAL_DAYS).toInt()
    }
}

/**
 * Honest price lines for the paywall (US-061, S18 notes): the real Play price, plus a computed per-month and
 * per-week equivalent labelled "about" and rounded to whole rupees. Indian digit grouping (₹1,499; ₹1,49,900).
 */
object Prices {
    private const val MICROS = 1_000_000.0
    private const val WEEKS_PER_YEAR = 52.0

    fun perMonthMicros(priceMicros: Long, period: BillingPeriod): Long = (priceMicros.toDouble() / period.months).roundToLong()

    fun perWeekMicros(priceMicros: Long, period: BillingPeriod): Long =
        (priceMicros.toDouble() * (12.0 / period.months) / WEEKS_PER_YEAR).roundToLong()

    /** Whole units, Indian grouping, with the currency's symbol (₹ for INR). */
    fun format(micros: Long, currencyCode: String = "INR"): String {
        val whole = (micros / MICROS).roundToLong()
        val symbol = if (currencyCode == "INR") "₹" else runCatching { Currency.getInstance(currencyCode).symbol }.getOrDefault("$currencyCode ")
        return symbol + groupIndian(whole)
    }

    fun groupIndian(n: Long): String {
        if (n < 0) return "-" + groupIndian(-n)
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val pairs = rest.reversed().chunked(2).joinToString(",") { it }.reversed()
        return "$pairs,$last3"
    }
}
