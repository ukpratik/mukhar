package com.vaak.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaakDatabaseTest {
    private lateinit var db: VaakDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VaakDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun streakDayIsNeverOverwritten() = runTest {
        val dao = db.habitDao()
        assertThat(dao.insertStreakDay(StreakDayEntity("2026-11-02", "talk", 1L, "Asia/Kolkata"))).isNotEqualTo(-1L)
        assertThat(dao.insertStreakDay(StreakDayEntity("2026-11-02", "review", 2L, "America/New_York"))).isEqualTo(-1L)
        assertThat(dao.streakDays().single().source).isEqualTo("talk")
    }

    @Test
    fun onlyOnePathClaimsTheAnalysisLease() = runTest {
        val dao = db.talkDao()
        dao.insertSession(session("s1", status = "recorded"))
        assertThat(dao.claimLease("s1", now = 1_000)).isEqualTo(1)
        assertThat(dao.claimLease("s1", now = 2_000)).isEqualTo(0) // lease held until 91_000
        assertThat(dao.claimLease("s1", now = 91_001)).isEqualTo(1) // stale lease can be reclaimed
    }

    private fun session(id: String, status: String) = TalkSessionEntity(
        id = id, kind = "talk", parentId = null, lang = "en", track = "interviews", promptId = "p1", createdAt = 0,
        zoneId = "Asia/Kolkata", durationMs = 60_000, speechMs = 50_000, snrDb = 25.0, status = status, leaseUntil = null,
        attempts = 0, errorCode = null, audioPath = null, audioKept = false, serverSessionId = null, quotaDay = null, updatedAt = 0,
    )
}
