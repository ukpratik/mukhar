package com.vaak.data.repo

import com.vaak.data.db.HabitDao
import com.vaak.data.db.StreakDayEntity
import com.vaak.domain.streak.Activity
import com.vaak.domain.streak.Source
import com.vaak.domain.streak.StreakEngine
import com.vaak.domain.streak.WeeklyGoal
import com.vaak.domain.streak.streakDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Streak credit hook (US-031): a talk with ≥ 30 s of speech credits its streak day at stop time, offline-queued talks
 * too. Days are stored, never recomputed. Freezes, repair and the Today UI arrive in M3.
 */
class HabitRepository(private val habitDao: HabitDao) {

    suspend fun creditTalk(at: Instant, zone: ZoneId, speechMs: Long): Credit? {
        val days = days(habitDao.streakDays())
        val day = StreakEngine.creditFor(Activity.Talk(at, zone, speechMs), days.keys.toSortedSet()) ?: return null
        habitDao.insertStreakDay(StreakDayEntity(day.toString(), Source.TALK.key, at.toEpochMilli(), zone.id))
        return creditFor(day, days + (day to Source.TALK), weeklyGoal())
    }

    /** The credit pill for a talk recorded at [at], if its day is covered. */
    fun observeCredit(at: Instant, zone: ZoneId): Flow<Credit?> = habitDao.observeStreakDays().map { rows ->
        val day = streakDay(at, zone)
        val days = days(rows)
        if (day in days) creditFor(day, days, weeklyGoal()) else null
    }

    private suspend fun weeklyGoal(): Int = habitDao.habitState()?.weeklyGoal?.takeIf { it in WeeklyGoal.ALLOWED } ?: DEFAULT_GOAL

    private fun creditFor(day: LocalDate, days: Map<LocalDate, Source>, goal: Int) = Credit(
        day = day,
        streak = StreakEngine.streak(days, day),
        weeklyGoalMet = WeeklyGoal.weekProgress(days, day, goal).met,
    )

    private fun days(rows: List<StreakDayEntity>): Map<LocalDate, Source> =
        rows.associate { LocalDate.parse(it.day) to Source.fromKey(it.source) }

    private companion object {
        const val DEFAULT_GOAL = 5
    }
}
