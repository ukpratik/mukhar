package com.vaak.domain.streak

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.SortedSet

/**
 * Pure streak rules (US-031, US-032, LLD §4.6). Credited days are stored and never recomputed, so a time-zone or
 * clock change can't remove them, and a day can't be credited twice.
 */
object StreakEngine {
    const val MIN_SPEECH_MS = 30_000L
    const val MIN_REVIEW_CARDS = 5
    const val MAX_FREEZES = 2
    const val FREEZE_EVERY_DAYS = 7
    const val REPAIR_WINDOW_DAYS = 2L
    const val REPAIR_COOLDOWN_DAYS = 30L
    const val REPAIR_SESSIONS = 2

    /** Talk ≥ 30 s of detected speech, review ≥ 5 cards, onboarding read-aloud always. */
    fun qualifies(a: Activity): Boolean = when (a) {
        is Activity.Talk -> a.speechMs >= MIN_SPEECH_MS
        is Activity.Review -> a.cards >= MIN_REVIEW_CARDS
        is Activity.Onboarding -> true
    }

    /**
     * The day to credit for [a], or `null` when it doesn't qualify, the day is already covered (no double credit), or
     * the day is before the latest covered day (clock or zone moved back: no retro credit).
     */
    fun creditFor(a: Activity, credited: SortedSet<LocalDate>): LocalDate? {
        if (!qualifies(a)) return null
        val day = streakDay(a.at, a.zone)
        if (day in credited) return null
        if (credited.isNotEmpty() && day < credited.last()) return null
        return day
    }

    /** Convenience: apply [a] to the stored days. Returns the same map when nothing is credited. */
    fun credit(a: Activity, days: Map<LocalDate, Source>): Map<LocalDate, Source> {
        val source = when (a) {
            is Activity.Talk -> Source.TALK
            is Activity.Review -> Source.REVIEW
            is Activity.Onboarding -> Source.ONBOARDING
        }
        val day = creditFor(a, days.keys.toSortedSet()) ?: return days
        return days + (day to source)
    }

    /**
     * Auto-use freezes for missed days (US-032). Freezes are spent only when they actually save the streak
     * (D-029): either they cover every missed day, or they cover all but the last missed day and a repair is
     * still available for that day. Otherwise nothing is spent and the user keeps their freezes. Idempotent.
     */
    fun applyMissedDays(
        days: Map<LocalDate, Source>,
        habit: HabitState,
        today: LocalDate,
    ): Pair<Map<LocalDate, Source>, HabitState> {
        val last = days.keys.maxOrNull() ?: return days to habit
        val gap = ChronoUnit.DAYS.between(last, today) - 1
        val held = habit.freezesHeld
        if (gap <= 0 || held == 0 || gap > held + 1) return days to habit
        val spend = minOf(gap.toInt(), held)
        val out = days.toMutableMap()
        var d = last.plusDays(1)
        repeat(spend) {
            out[d] = Source.FREEZE
            d = d.plusDays(1)
        }
        val after = habit.copy(freezesHeld = held - spend)
        if (spend < gap && canRepair(out, after, today) == null) return days to habit
        return out to after
    }

    /** Consecutive covered days ending today, or ending yesterday if today isn't covered yet. */
    fun streak(days: Map<LocalDate, Source>, today: LocalDate): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun best(days: Map<LocalDate, Source>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.keys.sorted()) {
            run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** +1 freeze at each multiple of 7 above the last award, max 2 held. A broken streak resets the award marker. */
    fun awardFreezes(streak: Int, habit: HabitState): HabitState {
        val base = if (streak < habit.lastFreezeAwardStreak) 0 else habit.lastFreezeAwardStreak
        val milestone = (streak / FREEZE_EVERY_DAYS) * FREEZE_EVERY_DAYS
        if (milestone <= base) return habit.copy(lastFreezeAwardStreak = base)
        val earned = (milestone - base) / FREEZE_EVERY_DAYS
        return habit.copy(
            freezesHeld = minOf(MAX_FREEZES, habit.freezesHeld + earned),
            lastFreezeAwardStreak = milestone,
        )
    }

    /**
     * The repairable missed day, if any (US-032): exactly one uncovered day `m` between covered days (`m − 1` covered),
     * every day from `m + 1` to yesterday covered, [today] ≤ m + 2 (48 h), and the last repair more than 30 days ago.
     */
    fun canRepair(days: Map<LocalDate, Source>, habit: HabitState, today: LocalDate): LocalDate? {
        habit.lastRepairDay?.let { if (ChronoUnit.DAYS.between(it, today) <= REPAIR_COOLDOWN_DAYS) return null }
        for (offset in 1..REPAIR_WINDOW_DAYS) {
            val m = today.minusDays(offset)
            if (m in days || m.minusDays(1) !in days) continue
            val between = generateSequence(m.plusDays(1)) { it.plusDays(1) }.takeWhile { it < today }
            if (between.all { it in days }) return m
        }
        return null
    }

    /** 2 qualifying sessions today repair the missed day. Returns `null` when there's nothing to repair yet. */
    fun repairIfEarned(
        days: Map<LocalDate, Source>,
        habit: HabitState,
        today: LocalDate,
        sessionsToday: Int,
    ): Pair<Map<LocalDate, Source>, HabitState>? {
        if (sessionsToday < REPAIR_SESSIONS) return null
        val m = canRepair(days, habit, today) ?: return null
        return (days + (m to Source.REPAIR)) to habit.copy(lastRepairDay = today)
    }

    fun state(days: Map<LocalDate, Source>, habit: HabitState, today: LocalDate): StreakState =
        StreakState(
            current = streak(days, today),
            best = best(days),
            freezesHeld = habit.freezesHeld,
            coveredToday = today in days,
        )
}
