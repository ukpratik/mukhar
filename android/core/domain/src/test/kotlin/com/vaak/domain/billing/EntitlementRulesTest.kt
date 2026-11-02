package com.vaak.domain.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** US-061/062: the entitlement states, the reminder at expiry − 24 h, the day-6 banner, and honest price lines. */
class EntitlementRulesTest {
    private val start = Instant.parse("2026-11-06T04:30:00Z")
    private val expiry = start.plus(Duration.ofDays(7))
    private val trial = EntitlementSnapshot(premium = true, expiryMs = expiry.toEpochMilli(), isTrial = true, autoRenewing = true)

    @Test
    fun statusFollowsTrialActiveCancelledExpired() {
        assertThat(EntitlementRules.status(EntitlementSnapshot.FREE, start)).isEqualTo(SubscriptionStatus.FREE)
        assertThat(EntitlementRules.status(trial, start)).isEqualTo(SubscriptionStatus.TRIAL)
        assertThat(EntitlementRules.status(trial.copy(autoRenewing = false), start)).isEqualTo(SubscriptionStatus.TRIAL_CANCELLED)
        assertThat(EntitlementRules.status(trial.copy(isTrial = false), start)).isEqualTo(SubscriptionStatus.ACTIVE)
        assertThat(EntitlementRules.status(trial.copy(isTrial = false, autoRenewing = false), start)).isEqualTo(SubscriptionStatus.CANCELLED)
        assertThat(EntitlementRules.status(trial, expiry)).isEqualTo(SubscriptionStatus.EXPIRED)
        assertThat(EntitlementRules.isPremium(trial, expiry.minusMillis(1))).isTrue()
        assertThat(EntitlementRules.isPremium(trial, expiry)).isFalse()
        assertThat(EntitlementRules.isPremium(trial.copy(premium = false), start)).isFalse()
    }

    @Test
    fun reminderIsAtExpiryMinus24hOnlyWhileTheTrialWillConvert() {
        assertThat(EntitlementRules.reminderAt(trial, start)).isEqualTo(expiry.minus(Duration.ofHours(24)))
        // Already cancelled (isAutoRenewing false): no reminder.
        assertThat(EntitlementRules.reminderAt(trial.copy(autoRenewing = false), start)).isNull()
        // Paid periods don't get a trial reminder.
        assertThat(EntitlementRules.reminderAt(trial.copy(isTrial = false), start)).isNull()
        assertThat(EntitlementRules.reminderAt(trial, expiry)).isNull()
    }

    @Test
    fun bannerShowsFromTrialDay6UntilExpiry() {
        val day6 = expiry.minus(Duration.ofHours(48))
        assertThat(EntitlementRules.bannerVisible(trial, day6.minusSeconds(1))).isFalse()
        assertThat(EntitlementRules.bannerVisible(trial, day6)).isTrue()
        assertThat(EntitlementRules.bannerVisible(trial, expiry.minusSeconds(1))).isTrue()
        assertThat(EntitlementRules.bannerVisible(trial, expiry)).isFalse()
        assertThat(EntitlementRules.bannerVisible(trial.copy(autoRenewing = false), day6)).isFalse()
        assertThat(EntitlementRules.trialDay(trial, day6)).isEqualTo(6)
        assertThat(EntitlementRules.trialDay(trial, start)).isEqualTo(1)
    }

    @Test
    fun pricesPerMonthAndWeekAreRoundedWholeRupees() {
        val founding = PlanCatalog.LIST_PRICE_MICROS.getValue(Plan.FOUNDING)
        val yearly = PlanCatalog.LIST_PRICE_MICROS.getValue(Plan.YEARLY)
        val monthly = PlanCatalog.LIST_PRICE_MICROS.getValue(Plan.MONTHLY)
        assertThat(Prices.format(founding)).isEqualTo("₹999")
        assertThat(Prices.format(yearly)).isEqualTo("₹1,499")
        assertThat(Prices.format(Prices.perMonthMicros(founding, BillingPeriod.YEAR))).isEqualTo("₹83")
        assertThat(Prices.format(Prices.perWeekMicros(founding, BillingPeriod.YEAR))).isEqualTo("₹19")
        assertThat(Prices.format(Prices.perMonthMicros(yearly, BillingPeriod.YEAR))).isEqualTo("₹125")
        assertThat(Prices.format(Prices.perWeekMicros(yearly, BillingPeriod.YEAR))).isEqualTo("₹29")
        assertThat(Prices.format(Prices.perWeekMicros(monthly, BillingPeriod.MONTH))).isEqualTo("₹57")
        assertThat(Prices.groupIndian(149_900)).isEqualTo("1,49,900")
        assertThat(Prices.groupIndian(12_34_56_789)).isEqualTo("12,34,56,789")
        assertThat(Prices.groupIndian(999)).isEqualTo("999")
    }
}
