package com.vaak.domain.streak

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.domain.FixedClock
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private val NY: ZoneId = ZoneId.of("America/New_York")
private val LA: ZoneId = ZoneId.of("America/Los_Angeles")

private fun d(s: String): LocalDate = LocalDate.parse(s)
private fun i(s: String): Instant = Instant.parse(s)

/** Contiguous covered days [from, to] with [source]. */
private fun covered(from: String, to: String, source: Source = Source.TALK): Map<LocalDate, Source> =
    generateSequence(d(from)) { it.plusDays(1) }.takeWhile { it <= d(to) }.associateWith { source }

class StreakDayTest {
    private data class Row(val name: String, val at: Instant, val zone: ZoneId, val day: String)

    @Test
    fun cutoffTable() {
        val rows = listOf(
            Row("IST 02:59 belongs to yesterday", i("2026-11-01T21:29:00Z"), IST, "2026-11-01"),
            Row("IST 03:00 starts the day", i("2026-11-01T21:30:00Z"), IST, "2026-11-02"),
            Row("IST 23:59", i("2026-11-02T18:29:00Z"), IST, "2026-11-02"),
            Row("IST midnight is still yesterday", i("2026-11-02T18:30:00Z"), IST, "2026-11-02"),
            Row("NY spring-forward 01:59 EST", i("2026-03-08T06:59:00Z"), NY, "2026-03-07"),
            Row("NY spring-forward 03:00 EDT (02:xx doesn't exist)", i("2026-03-08T07:00:00Z"), NY, "2026-03-08"),
            Row("NY spring-forward 03:30 EDT is the new day", i("2026-03-08T07:30:00Z"), NY, "2026-03-08"),
            Row("NY fall-back first 01:30 EDT", i("2026-11-01T05:30:00Z"), NY, "2026-10-31"),
            Row("NY fall-back second 01:30 EST", i("2026-11-01T06:30:00Z"), NY, "2026-10-31"),
            Row("NY fall-back 02:59 EST", i("2026-11-01T07:59:00Z"), NY, "2026-10-31"),
            Row("NY fall-back 03:00 EST", i("2026-11-01T08:00:00Z"), NY, "2026-11-01"),
            Row("same instant, different zone", i("2026-11-02T04:30:00Z"), LA, "2026-11-01"),
        )
        for (r in rows) assertWithMessage(r.name).that(streakDay(r.at, r.zone)).isEqualTo(d(r.day))
    }

    @Test
    fun nextCutoffIsTheNextLocal0300() {
        assertThat(nextCutoff(i("2026-11-01T20:30:00Z"), IST)).isEqualTo(i("2026-11-01T21:30:00Z")) // 02:00 IST
        assertThat(nextCutoff(i("2026-11-01T21:30:00Z"), IST)).isEqualTo(i("2026-11-02T21:30:00Z")) // exactly 03:00
        assertThat(nextCutoff(i("2026-03-07T12:00:00Z"), NY)).isEqualTo(i("2026-03-08T07:00:00Z")) // 03:00 EDT
    }
}

class StreakCreditTest {
    private val noDays = sortedSetOf<LocalDate>()

    @Test
    fun qualificationThresholds() {
        val at = i("2026-11-02T05:00:00Z")
        data class Row(val a: Activity, val ok: Boolean)
        val rows = listOf(
            Row(Activity.Talk(at, IST, 29_999), false),
            Row(Activity.Talk(at, IST, 30_000), true),
            Row(Activity.Review(at, IST, 4), false),
            Row(Activity.Review(at, IST, 5), true),
            Row(Activity.Onboarding(at, IST), true),
        )
        for (r in rows) {
            assertWithMessage("${r.a}").that(StreakEngine.qualifies(r.a)).isEqualTo(r.ok)
            assertWithMessage("${r.a}").that(StreakEngine.creditFor(r.a, noDays)).isEqualTo(if (r.ok) d("2026-11-02") else null)
        }
    }

    @Test
    fun noDoubleCreditOnTheSameDay() {
        var days = StreakEngine.credit(Activity.Talk(i("2026-11-02T05:00:00Z"), IST, 60_000), emptyMap())
        val again = StreakEngine.credit(Activity.Review(i("2026-11-02T15:00:00Z"), IST, 10), days)
        assertThat(again).isEqualTo(days)
        assertThat(days).containsExactly(d("2026-11-02"), Source.TALK)
        days = StreakEngine.credit(Activity.Talk(i("2026-11-02T21:29:00Z"), IST, 60_000), days) // 02:59 next morning
        assertThat(days.keys).containsExactly(d("2026-11-02"))
        days = StreakEngine.credit(Activity.Talk(i("2026-11-02T21:30:00Z"), IST, 60_000), days) // 03:00
        assertThat(days.keys).containsExactly(d("2026-11-02"), d("2026-11-03"))
    }

    @Test
    fun manualClockMovedBackGivesNoRetroCredit() {
        val credited = sortedSetOf(d("2026-11-01"), d("2026-11-03"))
        // Clock set back to Nov 2: no credit for the gap, nothing removed.
        assertThat(StreakEngine.creditFor(Activity.Talk(i("2026-11-02T06:00:00Z"), IST, 60_000), credited)).isNull()
        assertThat(credited).hasSize(2)
    }

    @Test
    fun manualClockMovedForwardThenBack() {
        val clock = FixedClock(i("2026-11-02T05:00:00Z"), IST)
        var days = StreakEngine.credit(Activity.Talk(clock.now(), clock.zone(), 60_000), emptyMap())
        clock.advanceBy(Duration.ofDays(3)) // user jumps the clock to Nov 5
        days = StreakEngine.credit(Activity.Talk(clock.now(), clock.zone(), 60_000), days)
        clock.advanceBy(Duration.ofDays(-3)) // and back to the real Nov 2, then practises on Nov 3
        days = StreakEngine.credit(Activity.Talk(clock.now().plus(Duration.ofDays(1)), clock.zone(), 60_000), days)
        assertThat(days.keys).containsExactly(d("2026-11-02"), d("2026-11-05")) // kept, never lost or doubled
    }

    @Test
    fun timeZoneChangeWestNeverDoubleCreditsOrLosesDays() {
        // Credited Nov 2 in India, then flies to LA where it's still Nov 1 / early Nov 2.
        var days = StreakEngine.credit(Activity.Talk(i("2026-11-02T04:30:00Z"), IST, 60_000), emptyMap())
        days = StreakEngine.credit(Activity.Talk(i("2026-11-02T10:00:00Z"), LA, 60_000), days) // 02:00 PST → Nov 1
        days = StreakEngine.credit(Activity.Talk(i("2026-11-02T20:00:00Z"), LA, 60_000), days) // 12:00 PST → Nov 2
        assertThat(days.keys).containsExactly(d("2026-11-02"))
        days = StreakEngine.credit(Activity.Talk(i("2026-11-03T18:00:00Z"), LA, 60_000), days) // Nov 3 in LA
        assertThat(days.keys).containsExactly(d("2026-11-02"), d("2026-11-03"))
        assertThat(StreakEngine.streak(days, streakDay(i("2026-11-03T18:00:00Z"), LA))).isEqualTo(2)
    }

    @Test
    fun timeZoneChangeEastCreditsTheNewLocalDay() {
        var days = StreakEngine.credit(Activity.Talk(i("2026-11-03T04:00:00Z"), LA, 60_000), emptyMap()) // Nov 2 20:00 PST
        days = StreakEngine.credit(Activity.Talk(i("2026-11-03T05:00:00Z"), IST, 60_000), days) // Nov 3 10:30 IST
        assertThat(days.keys).containsExactly(d("2026-11-02"), d("2026-11-03"))
    }

    @Test
    fun streakSurvivesAZoneChangeAfterCrediting() {
        val days = covered("2026-11-01", "2026-11-03")
        // Same instant viewed from LA is "yesterday" there; stored days are unchanged and the streak still counts.
        val laToday = streakDay(i("2026-11-03T12:00:00Z"), LA)
        assertThat(laToday).isEqualTo(d("2026-11-03"))
        assertThat(StreakEngine.streak(days, laToday)).isEqualTo(3)
    }

    @Test
    fun offlineTalkIsCreditedForItsStopTime() {
        val stop = i("2026-11-01T18:20:00Z") // 23:50 IST on Nov 1, recorded offline
        val clock = FixedClock(stop, IST)
        clock.advanceBy(Duration.ofHours(12)) // analysed the next morning
        val days = StreakEngine.credit(Activity.Talk(stop, IST, 45_000), emptyMap())
        assertThat(days.keys).containsExactly(d("2026-11-01"))
        assertThat(streakDay(clock.now(), clock.zone())).isEqualTo(d("2026-11-02"))
    }

    @Test
    fun streakCountsToTodayOrYesterday() {
        val days = covered("2026-11-01", "2026-11-05")
        assertThat(StreakEngine.streak(days, d("2026-11-05"))).isEqualTo(5)
        assertThat(StreakEngine.streak(days, d("2026-11-06"))).isEqualTo(5) // today not covered yet
        assertThat(StreakEngine.streak(days, d("2026-11-07"))).isEqualTo(0)
        assertThat(StreakEngine.streak(emptyMap(), d("2026-11-07"))).isEqualTo(0)
    }

    @Test
    fun stateReportsBestAndCoveredToday() {
        val days = covered("2026-10-01", "2026-10-10") + covered("2026-11-01", "2026-11-03")
        val s = StreakEngine.state(days, HabitState(freezesHeld = 1), d("2026-11-03"))
        assertThat(s).isEqualTo(StreakState(current = 3, best = 10, freezesHeld = 1, coveredToday = true))
    }
}

class FreezeTest {
    @Test
    fun awardTable() {
        data class Row(val name: String, val streak: Int, val before: HabitState, val held: Int, val last: Int)
        val rows = listOf(
            Row("6 days: nothing", 6, HabitState(), 0, 0),
            Row("7 days: first freeze", 7, HabitState(), 1, 7),
            Row("8 days: no repeat award", 8, HabitState(freezesHeld = 1, lastFreezeAwardStreak = 7), 1, 7),
            Row("14 days: second freeze", 14, HabitState(freezesHeld = 1, lastFreezeAwardStreak = 7), 2, 14),
            Row("21 days: capped at 2", 21, HabitState(freezesHeld = 2, lastFreezeAwardStreak = 14), 2, 21),
            Row("jump to 14 from 0", 14, HabitState(), 2, 14),
            Row("broken streak resets the marker", 3, HabitState(freezesHeld = 0, lastFreezeAwardStreak = 14), 0, 0),
            Row("after reset, 7 earns again", 7, HabitState(freezesHeld = 0, lastFreezeAwardStreak = 0), 1, 7),
        )
        for (r in rows) {
            val after = StreakEngine.awardFreezes(r.streak, r.before)
            assertWithMessage(r.name).that(after.freezesHeld).isEqualTo(r.held)
            assertWithMessage(r.name).that(after.lastFreezeAwardStreak).isEqualTo(r.last)
        }
    }

    @Test
    fun missedDayUsesAFreezeAutomatically() {
        val days = covered("2026-11-01", "2026-11-07")
        val (after, habit) = StreakEngine.applyMissedDays(days, HabitState(freezesHeld = 1), d("2026-11-09"))
        assertThat(after[d("2026-11-08")]).isEqualTo(Source.FREEZE)
        assertThat(habit.freezesHeld).isEqualTo(0)
        assertThat(StreakEngine.streak(after, d("2026-11-09"))).isEqualTo(8)
        // Idempotent: running again on the next app open changes nothing.
        assertThat(StreakEngine.applyMissedDays(after, habit, d("2026-11-09"))).isEqualTo(after to habit)
    }

    @Test
    fun twoMissedDaysWithTwoFreezes() {
        val (after, habit) = StreakEngine.applyMissedDays(covered("2026-11-01", "2026-11-07"), HabitState(freezesHeld = 2), d("2026-11-10"))
        assertThat(after.filterValues { it == Source.FREEZE }.keys).containsExactly(d("2026-11-08"), d("2026-11-09"))
        assertThat(habit.freezesHeld).isEqualTo(0)
        assertThat(StreakEngine.streak(after, d("2026-11-10"))).isEqualTo(9)
    }

    @Test
    fun gapTooBigToSaveKeepsFreezes() {
        val days = covered("2026-11-01", "2026-11-07")
        val habit = HabitState(freezesHeld = 1)
        // 3 missed days (8, 9, 10) with 1 freeze + 1 repair can't be saved, so nothing is spent (D-029).
        assertThat(StreakEngine.applyMissedDays(days, habit, d("2026-11-11"))).isEqualTo(days to habit)
    }

    @Test
    fun freezePlusRepairOnlyWhenRepairStillAvailable() {
        val days = covered("2026-11-01", "2026-11-07")
        // Repair used 10 days ago (within 30 days), so 1 freeze can't save a 2-day gap: keep the freeze.
        val habit = HabitState(freezesHeld = 1, lastRepairDay = d("2026-10-31"))
        assertThat(StreakEngine.applyMissedDays(days, habit, d("2026-11-10"))).isEqualTo(days to habit)
    }

    @Test
    fun moreMissedDaysThanFreezesStopsAndLeavesARepairableDay() {
        val (after, habit) = StreakEngine.applyMissedDays(covered("2026-11-01", "2026-11-07"), HabitState(freezesHeld = 1), d("2026-11-10"))
        assertThat(after[d("2026-11-08")]).isEqualTo(Source.FREEZE)
        assertThat(after).doesNotContainKey(d("2026-11-09"))
        assertThat(StreakEngine.streak(after, d("2026-11-10"))).isEqualTo(0)
        assertThat(StreakEngine.canRepair(after, habit, d("2026-11-10"))).isEqualTo(d("2026-11-09"))
    }

    @Test
    fun noMissedDaysOrClockBackChangesNothing() {
        val days = covered("2026-11-01", "2026-11-07")
        val habit = HabitState(freezesHeld = 2)
        assertThat(StreakEngine.applyMissedDays(days, habit, d("2026-11-08"))).isEqualTo(days to habit)
        assertThat(StreakEngine.applyMissedDays(days, habit, d("2026-11-03"))).isEqualTo(days to habit)
        assertThat(StreakEngine.applyMissedDays(emptyMap(), habit, d("2026-11-03"))).isEqualTo(emptyMap<LocalDate, Source>() to habit)
    }
}

class RepairTest {
    private val base = covered("2026-11-01", "2026-11-05") // Nov 6 missed

    @Test
    fun repairWindowTable() {
        data class Row(val name: String, val days: Map<LocalDate, Source>, val habit: HabitState, val today: String, val m: String?)
        val rows = listOf(
            Row("next day", base, HabitState(), "2026-11-07", "2026-11-06"),
            Row("within 48 h, day after covered", base + covered("2026-11-07", "2026-11-07"), HabitState(), "2026-11-08", "2026-11-06"),
            Row("too late (72 h)", base + covered("2026-11-07", "2026-11-08"), HabitState(), "2026-11-09", null),
            Row("two missed days", base, HabitState(), "2026-11-08", null),
            Row("nothing missed", base, HabitState(), "2026-11-06", null),
            Row("repaired 28 days ago", base, HabitState(lastRepairDay = d("2026-10-10")), "2026-11-07", null),
            Row("repaired exactly 30 days ago", base, HabitState(lastRepairDay = d("2026-10-08")), "2026-11-07", null),
            Row("repaired 31 days ago", base, HabitState(lastRepairDay = d("2026-10-07")), "2026-11-07", "2026-11-06"),
            Row("no streak before the gap", emptyMap(), HabitState(), "2026-11-07", null),
        )
        for (r in rows) {
            assertWithMessage(r.name).that(StreakEngine.canRepair(r.days, r.habit, d(r.today))).isEqualTo(r.m?.let(::d))
        }
    }

    @Test
    fun twoSessionsInOneDayRepair() {
        val today = d("2026-11-07")
        val withToday = base + (today to Source.TALK)
        assertThat(StreakEngine.repairIfEarned(withToday, HabitState(), today, sessionsToday = 1)).isNull()
        val (days, habit) = StreakEngine.repairIfEarned(withToday, HabitState(), today, sessionsToday = 2)!!
        assertThat(days[d("2026-11-06")]).isEqualTo(Source.REPAIR)
        assertThat(habit.lastRepairDay).isEqualTo(today)
        assertThat(StreakEngine.streak(days, today)).isEqualTo(7)
        // Once per 30 days.
        assertThat(StreakEngine.canRepair(covered("2026-11-08", "2026-11-10"), habit, d("2026-11-12"))).isNull()
    }
}

class WeeklyGoalTest {
    @Test
    fun weekRunsMondayToSunday() {
        val days = covered("2026-11-01", "2026-11-04") // Sun Nov 1 (last week) + Mon–Wed
        val p = WeeklyGoal.weekProgress(days, d("2026-11-04"), 3)
        assertThat(p.weekStart).isEqualTo(d("2026-11-02"))
        assertThat(p.days).containsExactly(true, true, true, false, false, false, false).inOrder()
        assertThat(p.covered).isEqualTo(3)
        assertThat(p.met).isTrue()
        assertThat(WeeklyGoal.weekProgress(days, d("2026-11-04"), 5).met).isFalse()
    }

    @Test
    fun sundayStillBelongsToTheWeek() {
        val p = WeeklyGoal.weekProgress(covered("2026-11-02", "2026-11-08"), d("2026-11-08"), 7)
        assertThat(p.met).isTrue()
    }

    @Test
    fun onlyThreeFiveOrSeven() {
        assertThrows(IllegalArgumentException::class.java) { WeeklyGoal.weekProgress(emptyMap(), d("2026-11-04"), 4) }
    }
}
