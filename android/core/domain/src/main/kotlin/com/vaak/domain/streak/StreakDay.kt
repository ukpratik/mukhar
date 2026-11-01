package com.vaak.domain.streak

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A streak day runs 03:00 → 02:59 device-local time (US-031). */
const val CUTOFF_HOUR = 3

/**
 * The streak day an instant belongs to. Uses local wall-clock time, so DST days behave: 03:30 on a spring-forward
 * day is that day, and 02:30 (repeated on a fall-back day) is the previous day.
 * (LLD §4.6 subtracts 3 h on the instant timeline, which shifts the cutoff by an hour on DST days.)
 */
fun streakDay(at: Instant, zone: ZoneId): LocalDate =
    at.atZone(zone).toLocalDateTime().minusHours(CUTOFF_HOUR.toLong()).toLocalDate()

/** The next 03:00 local cutoff strictly after [now] (used by the revision queue and reminders). */
fun nextCutoff(now: Instant, zone: ZoneId): Instant =
    streakDay(now, zone).plusDays(1).atTime(CUTOFF_HOUR, 0).atZone(zone).toInstant()

/** Why a day is covered. Stored per day in `streak_day.source`. */
enum class Source(val key: String) {
    TALK("talk"),
    REVIEW("review"),
    ONBOARDING("onboarding"),
    FREEZE("freeze"),
    REPAIR("repair");

    companion object {
        fun fromKey(key: String): Source = entries.first { it.key == key }
    }
}

/** A practice event, stamped with the instant and zone at **stop** time (offline-queued talks too). */
sealed interface Activity {
    val at: Instant
    val zone: ZoneId

    data class Talk(override val at: Instant, override val zone: ZoneId, val speechMs: Long) : Activity
    data class Review(override val at: Instant, override val zone: ZoneId, val cards: Int) : Activity
    data class Onboarding(override val at: Instant, override val zone: ZoneId) : Activity
}

/** Habit bookkeeping (`habit_state`, single row). */
data class HabitState(
    val freezesHeld: Int = 0,
    val lastRepairDay: LocalDate? = null,
    val weeklyGoal: Int = 5,
    val lastFreezeAwardStreak: Int = 0,
)

data class StreakState(val current: Int, val best: Int, val freezesHeld: Int, val coveredToday: Boolean)
