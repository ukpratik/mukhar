package com.vaak.data.repo

import com.google.common.truth.Truth.assertThat
import com.vaak.data.analytics.ConsentAnalytics
import com.vaak.data.billing.AccountServer
import com.vaak.data.billing.ServerEntitlement
import com.vaak.data.billing.ServerStatus
import com.vaak.data.debug.FakeAccountServer
import com.vaak.data.debug.FakeBillingRepository
import com.vaak.data.debug.FakePlayStore
import com.vaak.data.net.AppError
import com.vaak.data.net.Outcome
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.tempSettingsStore
import com.vaak.domain.FixedClock
import com.vaak.domain.billing.EntitlementRules
import com.vaak.domain.billing.Plan
import com.vaak.domain.billing.SubscriptionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The entitlement state machine with the debug fake billing (US-061, US-062): trial → active → cancelled → expired,
 * the reminder at expiry − 24 h (none once auto-renew is off), the day-6 banner, restore on reinstall and a retry
 * when verification can't reach the Worker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntitlementRepositoryTest {
    private val clock = FixedClock(Instant.parse("2026-11-06T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val play = FakePlayStore(clock)
    private val billing = FakeBillingRepository(play)
    private val reminders = RecordingTrialReminders()
    private val sync = RecordingAccountSync()
    private val sink = RecordingSink()
    private val consent = MutableStateFlow(true)

    /** Updates are fed in by the test, so the repository's own collector gets a scope that never runs. */
    private val idle = CoroutineScope(Job().apply { cancel() })

    private fun repo(settings: SettingsStore = tempSettingsStore(), server: AccountServer = FakeAccountServer(play)) =
        EntitlementRepository(billing, server, settings, clock, reminders, sync, ConsentAnalytics(consent, sink), idle)

    @Test
    fun trialThenActiveThenCancelledThenExpired() = runTest {
        val repo = repo()
        repo.onUpdate(billing.buy(Plan.YEARLY, withTrial = true))
        var e = repo.current()
        assertThat(repo.status()).isEqualTo(SubscriptionStatus.TRIAL)
        assertThat(repo.isPremium.first()).isTrue()
        assertThat(e.plan).isEqualTo(Plan.YEARLY)
        val expiry = Instant.ofEpochMilli(e.expiryMs!!)
        assertThat(expiry).isEqualTo(clock.now().atZone(clock.zone()).plusDays(7).toInstant())
        assertThat(reminders.last).isEqualTo(expiry.minus(Duration.ofHours(24)))
        assertThat(play.subscription!!.acknowledged).isTrue() // acknowledged only after the Worker verified it
        assertThat(sink.names).containsAtLeast("trial_started", "purchase_completed").inOrder()

        // The trial converts: a paid year, no trial reminder any more.
        clock.advanceBy(Duration.ofDays(7).plusHours(1))
        assertThat(repo.refresh()).isEqualTo(RefreshResult.UPDATED)
        e = repo.current()
        assertThat(repo.status()).isEqualTo(SubscriptionStatus.ACTIVE)
        assertThat(e.isTrial).isFalse()
        assertThat(reminders.log.last()).isNull()

        // Cancelled in Play: Premium stays until expiry, auto-renew false suppresses any reminder.
        play.cancel()
        repo.refresh()
        assertThat(repo.status()).isEqualTo(SubscriptionStatus.CANCELLED)
        assertThat(repo.isPremium.first()).isTrue()
        assertThat(reminders.log.last()).isNull()
        assertThat(sink.names).contains("subscription_cancelled")

        // Past the paid period: expired, back to the free tier.
        clock.advanceBy(Duration.ofDays(400))
        assertThat(repo.refresh()).isEqualTo(RefreshResult.NONE)
        assertThat(repo.status()).isEqualTo(SubscriptionStatus.EXPIRED)
        assertThat(repo.isPremium.first()).isFalse()
    }

    @Test
    fun cancellingDuringTheTrialSuppressesTheReminderAndBanner() = runTest {
        val repo = repo()
        repo.onUpdate(billing.buy(Plan.MONTHLY, withTrial = true))
        assertThat(reminders.last).isNotNull()
        play.cancel()
        repo.refresh()
        val e = repo.current()
        assertThat(repo.status()).isEqualTo(SubscriptionStatus.TRIAL_CANCELLED)
        assertThat(e.autoRenewing).isFalse()
        assertThat(reminders.log.last()).isNull()
        clock.advanceBy(Duration.ofDays(6))
        assertThat(EntitlementRules.bannerVisible(e, clock.now())).isFalse()
        assertThat(EntitlementRules.isPremium(e, clock.now())).isTrue()
    }

    @Test
    fun bannerVisibilityFromDay6WithTheTrialExpiryOverride() = runTest {
        val repo = repo()
        repo.onUpdate(billing.buy(Plan.FOUNDING, withTrial = true))
        assertThat(EntitlementRules.bannerVisible(repo.current(), clock.now())).isFalse()

        play.endsIn(Duration.ofHours(49)) // debug hook: day 5 still
        repo.refresh()
        assertThat(EntitlementRules.bannerVisible(repo.current(), clock.now())).isFalse()

        play.endsIn(Duration.ofHours(47)) // debug hook: trial day 6
        repo.refresh()
        val e = repo.current()
        assertThat(EntitlementRules.bannerVisible(e, clock.now())).isTrue()
        assertThat(reminders.last).isEqualTo(Instant.ofEpochMilli(e.expiryMs!!).minus(Duration.ofHours(24)))
        assertThat(e.plan).isEqualTo(Plan.FOUNDING)
    }

    @Test
    fun restoreOnReinstallFindsThePlayPurchase() = runTest {
        repo().onUpdate(billing.buy(Plan.YEARLY, withTrial = true))

        // Reinstall: empty settings, same Play account.
        val fresh = repo(tempSettingsStore())
        assertThat(fresh.status()).isEqualTo(SubscriptionStatus.FREE)
        assertThat(fresh.restore()).isEqualTo(RefreshResult.UPDATED)
        assertThat(fresh.status()).isEqualTo(SubscriptionStatus.TRIAL)
        assertThat(fresh.isPremium.first()).isTrue()
        // Restores don't count as new purchases.
        assertThat(sink.names.count { it == "purchase_completed" }).isEqualTo(1)
    }

    @Test
    fun noPurchaseOnTheAccountMeansFree() = runTest {
        val repo = repo()
        assertThat(repo.refresh()).isEqualTo(RefreshResult.NONE)
        assertThat(repo.isPremium.first()).isFalse()
        assertThat(billing.plans().let { (it as Outcome.Ok).value.all { o -> o.trialDays == 7 } }).isTrue()
        repo.onUpdate(billing.buy(Plan.MONTHLY, withTrial = false))
        // Trial eligibility ends with the first subscription (Play: new customers only).
        assertThat((billing.plans() as Outcome.Ok).value.all { it.trialDays == null }).isTrue()
    }

    @Test
    fun offlineVerificationKeepsConfirmingAndQueuesARetry() = runTest {
        val offline = object : AccountServer {
            override suspend fun verify(purchaseToken: String, productId: String): Outcome<ServerEntitlement> = Outcome.Err(AppError.Offline)
            override suspend fun status(): Outcome<ServerStatus> = Outcome.Err(AppError.Offline)
            override suspend fun deleteMe(): Outcome<Unit> = Outcome.Err(AppError.Offline)
        }
        val repo = repo(server = offline)
        repo.onUpdate(billing.buy(Plan.YEARLY, withTrial = true))
        assertThat(repo.confirming.value).isTrue()
        assertThat(repo.isPremium.first()).isFalse() // quotas change only after the Worker verified it
        assertThat(sync.refreshes).isEqualTo(1)
        assertThat(play.subscription!!.acknowledged).isFalse()
    }

    @Test
    fun noAnalyticsWithoutConsent() = runTest {
        consent.value = false
        repo().onUpdate(billing.buy(Plan.YEARLY, withTrial = true))
        assertThat(sink.events).isEmpty()
    }
}
