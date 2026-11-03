package io.github.ukpratik.mukhar.data.repo

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.TestStores
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.streak.Activity
import io.github.ukpratik.mukhar.domain.streak.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** HabitRepository on in-memory Room (US-031, US-032, D-029). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitRepositoryTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val stores = TestStores()
    private val clock = FixedClock(at("2026-11-02", "10:00"), ist)
    private val repo = HabitRepository(stores.db.habitDao(), stores.settings, clock)

    @After
    fun tearDown() = stores.close()

    private fun at(date: String, time: String, zone: ZoneId = ZoneId.of("Asia/Kolkata")): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun d(s: String) = LocalDate.parse(s)

    private fun talk(date: String, time: String = "19:00", speechMs: Long = 45_000) = Activity.Talk(at(date, time), ist, speechMs)

    private fun review(date: String, cards: Int = 6) = Activity.Review(at(date, "08:00"), ist, cards)

    /** Moves the clock and credits a talk on each day in [from, to]. */
    private suspend fun talksFrom(from: String, to: String) {
        var day = d(from)
        while (day <= d(to)) {
            clock.instant = at(day.toString(), "19:00")
            repo.credit(talk(day.toString()))
            day = day.plusDays(1)
        }
    }

    @Test
    fun warmUpCreditsDayOne() = runBlocking<Unit> {
        val result = repo.credit(Activity.Onboarding(clock.now(), ist))
        assertThat(result.creditedDay).isEqualTo(d("2026-11-02"))
        assertThat(result.streak).isEqualTo(1)
        val snap = repo.snapshot()
        assertThat(snap.todaySource).isEqualTo(Source.ONBOARDING)
        assertThat(snap.coveredToday).isTrue()
        assertThat(snap.week.covered).isEqualTo(1)
        assertThat(snap.habit.weeklyGoal).isEqualTo(5)
    }

    @Test
    fun shortTalksDontCountAndADayIsNeverCreditedTwice() = runBlocking<Unit> {
        assertThat(repo.credit(talk("2026-11-02", speechMs = 20_000)).creditedDay).isNull()
        assertThat(repo.credit(talk("2026-11-02")).creditedDay).isEqualTo(d("2026-11-02"))
        assertThat(repo.credit(talk("2026-11-02", "21:00")).creditedDay).isNull()
        assertThat(repo.credit(review("2026-11-02")).creditedDay).isNull()
        val rows = stores.db.habitDao().streakDays()
        assertThat(rows.map { it.day to it.source }).containsExactly("2026-11-02" to "talk")
    }

    @Test
    fun aClockMovedBackGivesNoRetroCredit() = runBlocking<Unit> {
        clock.instant = at("2026-11-03", "19:00")
        repo.credit(talk("2026-11-03"))
        assertThat(repo.credit(talk("2026-11-02")).creditedDay).isNull()
        assertThat(repo.snapshot().days.keys).containsExactly(d("2026-11-03"))
    }

    @Test
    fun aZoneChangeNeverRemovesCreditedDays() = runBlocking<Unit> {
        repo.credit(talk("2026-11-02"))
        clock.instant = at("2026-11-02", "19:30")
        clock.zoneId = ZoneId.of("America/New_York") // flew west: 09:00 on Nov 2 there
        val snap = repo.snapshot()
        assertThat(snap.days.keys).containsExactly(d("2026-11-02"))
        assertThat(snap.streak.current).isEqualTo(1)
    }

    @Test
    fun aFreezeIsEarnedAtSevenDaysAndCoversAMissedDayOnTheNextOpen() = runBlocking<Unit> {
        talksFrom("2026-11-02", "2026-11-08")
        assertThat(repo.snapshot().habit.freezesHeld).isEqualTo(1)

        clock.instant = at("2026-11-10", "09:00") // missed Nov 9
        val snap = repo.refresh()
        assertThat(snap.days[d("2026-11-09")]).isEqualTo(Source.FREEZE)
        assertThat(snap.habit.freezesHeld).isEqualTo(0)
        assertThat(snap.freezeNoticeDay).isEqualTo(d("2026-11-09"))
        assertThat(snap.streak.current).isEqualTo(8) // Nov 2 to Nov 9, today still open
        // Frozen days keep the streak but don't add to the week (Mon Nov 9 to Sun Nov 15).
        assertThat(snap.week.covered).isEqualTo(0)
        assertThat(snap.frozenThisWeek).containsExactly(d("2026-11-09"))

        repo.acknowledgeFreezeNotice()
        assertThat(repo.snapshot().freezeNoticeDay).isNull()
        // Idempotent: a second open spends nothing.
        assertThat(repo.refresh().days.count { it.value == Source.FREEZE }).isEqualTo(1)
    }

    @Test
    fun noFreezeIsSpentWhenItCannotSaveTheStreak() = runBlocking<Unit> {
        talksFrom("2026-11-02", "2026-11-08") // 1 freeze held
        clock.instant = at("2026-11-12", "09:00") // missed Nov 9, 10, 11: one freeze can't save it (D-029)
        val snap = repo.refresh()
        assertThat(snap.habit.freezesHeld).isEqualTo(1)
        assertThat(snap.days.values).doesNotContain(Source.FREEZE)
        assertThat(snap.freshStart).isTrue()
    }

    @Test
    fun twoSessionsInOneDayRepairTheMissedDay() = runBlocking<Unit> {
        talksFrom("2026-11-02", "2026-11-05")
        clock.instant = at("2026-11-07", "08:00") // missed Nov 6, no freeze held
        assertThat(repo.refresh().repairDay).isEqualTo(d("2026-11-06"))

        repo.credit(review("2026-11-07"))
        var snap = repo.snapshot()
        assertThat(snap.repairDay).isEqualTo(d("2026-11-06"))
        assertThat(snap.repairSessionsDone).isEqualTo(1)
        assertThat(snap.repairableStreak).isEqualTo(4)

        val result = repo.credit(talk("2026-11-07", "19:00"))
        assertThat(result.repairedDay).isEqualTo(d("2026-11-06"))
        snap = repo.snapshot()
        assertThat(snap.days[d("2026-11-06")]).isEqualTo(Source.REPAIR)
        assertThat(snap.streak.current).isEqualTo(6)
        assertThat(snap.repairDay).isNull()
        assertThat(snap.habit.lastRepairDay).isEqualTo(d("2026-11-07"))
    }

    @Test
    fun freshStartIsShownOnceAfterTheRepairWindow() = runBlocking<Unit> {
        talksFrom("2026-11-02", "2026-11-03")
        clock.instant = at("2026-11-07", "09:00")
        var snap = repo.refresh()
        assertThat(snap.freshStart).isTrue()
        assertThat(snap.streak.current).isEqualTo(0)
        assertThat(snap.streak.best).isEqualTo(2)

        repo.acknowledgeFreshStart()
        assertThat(repo.snapshot().freshStart).isFalse()

        repo.credit(talk("2026-11-07"))
        snap = repo.snapshot()
        assertThat(snap.streak.current).isEqualTo(1)
        assertThat(snap.freshStart).isFalse()
    }

    @Test
    fun weeklyGoalChangesTakeEffectImmediately() = runBlocking<Unit> {
        repo.credit(talk("2026-11-02"))
        repo.setWeeklyGoal(3)
        val snap = repo.observe().first()
        assertThat(snap.week.goal).isEqualTo(3)
        assertThat(snap.habit.weeklyGoal).isEqualTo(3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weeklyGoalMustBe357() = runBlocking<Unit> {
        repo.setWeeklyGoal(4)
    }

    @Test
    fun theFreezeAwardDoesntRepeatForTheSameStreak() = runBlocking<Unit> {
        talksFrom("2026-11-02", "2026-11-15") // 14 days: 2 freezes, the max
        assertThat(repo.snapshot().habit.freezesHeld).isEqualTo(2)
        assertThat(repo.snapshot().nextFreezeInDays).isNull()
        talksFrom("2026-11-16", "2026-11-22") // 21 days: still 2
        assertThat(repo.snapshot().habit.freezesHeld).isEqualTo(2)
    }

    @Test
    fun talkPipelineCreditGoesThroughTheEngine() = runBlocking<Unit> {
        val stop = at("2026-11-02", "09:00")
        val credit = repo.creditTalk(stop, ist, 45_000)!!
        assertThat(credit.day).isEqualTo(d("2026-11-02"))
        assertThat(credit.streak).isEqualTo(1)
        assertThat(credit.weeklyGoalMet).isFalse()
        assertThat(repo.snapshot().todaySource).isEqualTo(Source.TALK)
        assertThat(repo.snapshot().sessionsToday).containsExactly(SessionKind.TALK) // counts towards a repair
        // A second talk the same day doesn't credit again; the pill still shows for both (S11).
        assertThat(repo.creditTalk(at("2026-11-02", "09:30"), ist, 45_000)).isNull()
        assertThat(repo.observeCredit(stop, ist).first()!!.streak).isEqualTo(1)
        // Under 30 s of speech never credits (US-031).
        assertThat(repo.creditTalk(at("2026-11-03", "09:00"), ist, 20_000)).isNull()
        assertThat(repo.observeCredit(at("2026-11-03", "09:00"), ist).first()).isNull()
    }

    @Test
    fun talkCreditUsesTheInjectedClockForCreditedAt() = runBlocking<Unit> {
        clock.instant = at("2026-11-02", "21:00")
        repo.creditTalk(at("2026-11-02", "20:59"), ist, 45_000)
        assertThat(stores.db.habitDao().streakDays().single().creditedAt).isEqualTo(clock.now().toEpochMilli())
    }
}
