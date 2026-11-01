package com.vaak.app.feature.today

import com.vaak.data.prefs.GoalTrack
import com.vaak.data.prefs.HabitMarks
import com.vaak.data.prefs.Settings
import com.vaak.data.repo.HabitRepository
import com.vaak.data.repo.HabitSnapshot
import com.vaak.data.today.WaitingTalks
import com.vaak.domain.model.Lang
import com.vaak.domain.streak.HabitState
import com.vaak.domain.streak.Source
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Today (S07) fixtures matching the design's states. */
object TodayFixtures {
    val ist: ZoneId = ZoneId.of("Asia/Kolkata")
    val monday: LocalDate = LocalDate.parse("2026-11-02")
    val friday: LocalDate = LocalDate.parse("2026-11-06")

    val consented = Settings(
        firstName = "Rohan",
        langs = setOf(Lang.EN),
        tracks = setOf(GoalTrack.INTERVIEWS, GoalTrack.PROFESSIONAL),
        disclosureConsentAt = Instant.parse("2026-11-02T02:00:00Z"),
        onboardingDone = true,
        onboardedAt = Instant.parse("2026-11-02T02:00:00Z"),
    )

    fun days(from: LocalDate, to: LocalDate, source: Source = Source.TALK): Map<LocalDate, Source> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.associateWith { source }

    fun snapshot(days: Map<LocalDate, Source>, today: LocalDate, habit: HabitState = HabitState(), marks: HabitMarks = HabitMarks()): HabitSnapshot =
        HabitRepository.compute(days, habit, marks, today)

    fun inputs(
        settings: Settings = consented,
        habit: HabitSnapshot,
        hour: Int = 9,
        dueNow: Int = 0,
        dueTomorrow: Int = 0,
        online: Boolean = true,
        waiting: WaitingTalks? = null,
    ) = TodayInputs(
        now = habit.today.atTime(LocalTime.of(hour, 0)).atZone(ist),
        settings = settings,
        habit = habit,
        dueNow = dueNow,
        dueTomorrow = dueTomorrow,
        online = online,
        waiting = waiting,
        recovered = null,
        notificationsAllowed = true,
        shuffle = 0,
    )

    /** S07 state 1: the warm-up counted as day 1, baseline skipped, nothing to revise. */
    val firstDay get() = TodayStateMapper.map(inputs(habit = snapshot(mapOf(monday to Source.ONBOARDING), monday), hour = 8))

    /** S07 state 2: 12-day streak, week 4 of 5, talk not done, 8 cards due, 1 freeze. */
    val midDay get() = TodayStateMapper.map(
        inputs(
            habit = snapshot(days(friday.minusDays(12), friday.minusDays(1)), friday, HabitState(freezesHeld = 1, lastFreezeAwardStreak = 7)),
            hour = 14,
            dueNow = 8,
            settings = consented.copy(baselineRecorded = true),
        ),
    )

    /** S07 state 3: talk done, revision done, weekly goal met; tomorrow teased. */
    val done get() = TodayStateMapper.map(
        inputs(habit = snapshot(days(friday.minusDays(12), friday), friday), hour = 19, dueTomorrow = 6, settings = consented.copy(baselineRecorded = true)),
    )

    /** S07 state 4: offline with a queued talk that already counted. */
    val offline get() = TodayStateMapper.map(
        inputs(
            habit = snapshot(days(friday.minusDays(5), friday.plusDays(1)), friday.plusDays(1)),
            hour = 19,
            dueNow = 6,
            online = false,
            waiting = WaitingTalks(1, Instant.parse("2026-11-07T03:42:00Z"), 161_000),
            settings = consented.copy(baselineRecorded = true),
        ),
    )

    /** S07 state 5: disclosure declined. */
    val talksOff get() = TodayStateMapper.map(
        inputs(habit = snapshot(days(monday, monday.plusDays(1), Source.REVIEW), monday.plusDays(2)), dueNow = 7, settings = consented.copy(disclosureConsentAt = null)),
    )
}
