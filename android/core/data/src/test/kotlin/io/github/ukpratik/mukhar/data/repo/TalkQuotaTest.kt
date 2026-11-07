package io.github.ukpratik.mukhar.data.repo

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.TestStores
import io.github.ukpratik.mukhar.data.db.TalkSessionEntity
import io.github.ukpratik.mukhar.data.prefs.ServerTalkQuota
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.quota.QuotaMirror
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** US-060 client mirror for S17: 1 coached talk a quota day (03:00 IST), Premium 20, the server's word wins. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TalkQuotaTest {
    private val stores = TestStores()
    private val clock = FixedClock(Instant.parse("2026-11-06T13:10:00Z"), ZoneId.of("Asia/Kolkata")) // 18:40 IST
    private val premium = MutableStateFlow(false)
    // These cover Mukhar Cloud's quota rules (kept, off in R1), so the server quota is switched on here.
    private val quota = TalkQuota(stores.db.progressDao(), stores.settings, object : Entitlements { override val isPremium = premium }, clock)
        .apply { serverQuota = { true } }

    @After
    fun tearDown() = stores.close()

    private suspend fun session(id: String, kind: String, at: Instant, status: String = "feedback_ready", error: String? = null, updated: Instant = at) =
        stores.db.talkDao().insertSession(
            TalkSessionEntity(
                id, kind, null, "en", "everyday", "p", at.toEpochMilli(), "Asia/Kolkata", 60_000, 50_000, 20.0, status, null, 0,
                error, null, false, null, null, updated.toEpochMilli(),
            ),
        )

    @Test
    fun oneTalkUsesTheFreeQuotaButRetakesAndBaselinesDont() = runTest {
        session("b", "baseline", clock.now().minusSeconds(600))
        session("r", "retake", clock.now().minusSeconds(300))
        assertThat(quota.status().exhausted).isFalse()
        session("t", "talk", clock.now().minusSeconds(900))
        val s = quota.status()
        assertThat(s.exhausted).isTrue()
        // Resets at 03:00 IST = 21:30 UTC.
        assertThat(s.resetsAt).isEqualTo(Instant.parse("2026-11-06T21:30:00Z"))
        assertThat(s.resetsAt).isEqualTo(QuotaMirror.resetsAt(QuotaMirror.quotaDay(clock.now())))
    }

    @Test
    fun yesterdaysTalkAndTooShortTalksDontCount() = runTest {
        session("old", "talk", Instant.parse("2026-11-05T21:00:00Z")) // before 03:00 IST today
        session("short", "talk", clock.now().minusSeconds(60), status = "failed", error = "TOO_LITTLE_SPEECH")
        session("gone", "talk", clock.now().minusSeconds(60), status = "discarded")
        assertThat(quota.status().exhausted).isFalse()
    }

    @Test
    fun premiumHasFairUseAndTheServerWins() = runTest {
        session("t", "talk", clock.now().minusSeconds(900))
        premium.value = true
        assertThat(quota.status().exhausted).isFalse()
        stores.settings.setServerTalkQuota(ServerTalkQuota(QuotaMirror.quotaDay(clock.now()), 20, 20))
        assertThat(quota.status().exhausted).isTrue()
    }

    @Test
    fun aServerRefusalCountsAsUsed() = runTest {
        // Recorded offline yesterday, refused by the server today: it waits for the next quota day.
        session("q", "talk", Instant.parse("2026-11-05T20:00:00Z"), status = "queued", error = "QUOTA_EXHAUSTED", updated = clock.now())
        val s = quota.status()
        assertThat(s.serverRefused).isTrue()
        assertThat(s.exhausted).isTrue()
    }

    @Test
    fun onThePhoneAndOnOwnKeysThereIsNoDailyLimit() = runTest {
        val local = TalkQuota(stores.db.progressDao(), stores.settings, object : Entitlements { override val isPremium = premium }, clock)
        stores.settings.setServerTalkQuota(ServerTalkQuota(QuotaMirror.quotaDay(clock.now()), 5, 1))
        for (mode in io.github.ukpratik.mukhar.domain.analysis.AnalysisMode.entries) {
            stores.settings.setAnalysisMode(mode)
            assertThat(local.status().exhausted).isFalse()
        }
        // The cloud rules would have refused it.
        assertThat(quota.status().exhausted).isTrue()
    }
}
