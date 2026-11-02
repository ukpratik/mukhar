package com.vaak.data.repo

import com.google.common.truth.Truth.assertThat
import com.vaak.data.TestStores
import com.vaak.data.analytics.ConsentAnalytics
import com.vaak.data.db.InsightEntity
import com.vaak.data.db.StreakDayEntity
import com.vaak.data.db.WordEntity
import com.vaak.data.prefs.GoalTrack
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.domain.model.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Instant

/** US-063: Delete all my data wipes Room, DataStore and recordings, and queues DELETE /me. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataWiperTest {
    private val stores = TestStores()
    private val recordings: File = Files.createTempDirectory("rec").toFile()
    private val share: File = Files.createTempDirectory("share").toFile()
    private val sync = RecordingAccountSync()
    private val reminders = RecordingTrialReminders()
    private val sink = RecordingSink()
    private var afterWipe = 0

    @After
    fun tearDown() {
        stores.close()
        recordings.deleteRecursively()
        share.deleteRecursively()
    }

    @Test
    fun wipesEverythingAndQueuesTheServerDelete() = runTest {
        val db = stores.db
        db.wordDao().insertWord(WordEntity("en:pivotal", "en", "pivotal", "m", "e", "formal", "", "curated", null, 0))
        db.habitDao().insertStreakDay(StreakDayEntity("2026-11-05", "talk", 0, "Asia/Kolkata"))
        db.progressDao().insertInsight(InsightEntity("talks_10", 0, false))
        stores.settings.setProfile("Ananya", setOf(Lang.EN), setOf(GoalTrack.INTERVIEWS))
        stores.settings.completeOnboarding(Instant.EPOCH)
        stores.settings.setAnalyticsConsent(true)
        stores.settings.setEntitlement(EntitlementSnapshot(premium = true, expiryMs = 1L, isTrial = true, autoRenewing = true))
        File(recordings, "t1.aac").writeBytes(ByteArray(16))
        File(share, "vaak-progress.png").writeBytes(ByteArray(16))
        val consent = MutableStateFlow(true)

        DataWiper(db, stores.settings, listOf(recordings, share), sync, reminders, ConsentAnalytics(consent, sink)) { afterWipe++ }.wipeAll()

        assertThat(db.wordDao().allWordIds()).isEmpty()
        assertThat(db.habitDao().streakDays()).isEmpty()
        assertThat(db.progressDao().insights()).isEmpty()
        val s = stores.settings.current()
        assertThat(s.onboardingDone).isFalse()
        assertThat(s.firstName).isNull()
        assertThat(s.analyticsConsent).isFalse()
        assertThat(stores.settings.entitlement.first()).isEqualTo(EntitlementSnapshot.FREE)
        assertThat(recordings.listFiles()).isEmpty()
        assertThat(share.listFiles()).isEmpty()
        assertThat(sync.cancels).isEqualTo(1)
        assertThat(sync.deletes).isEqualTo(1) // DELETE /me queued (retried by WorkManager until it succeeds)
        assertThat(reminders.log).containsExactly(null)
        assertThat(afterWipe).isEqualTo(1)
        assertThat(sink.names).containsExactly("data_deleted") // logged while consent was still on
    }
}
