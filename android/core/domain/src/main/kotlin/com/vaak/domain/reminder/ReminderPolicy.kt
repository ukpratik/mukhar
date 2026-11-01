package com.vaak.domain.reminder

import com.vaak.domain.streak.streakDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ReminderOutcome(val key: String) {
    SHOWN("shown"),
    OPENED("opened"),
    IGNORED("ignored"),
    SKIPPED_CREDITED("skipped_credited"),
    SUPPRESSED_BACKOFF("suppressed_backoff"),
}

/** `reminder_log` row. [scheduledFor] is the planned instant; [outcome] is final once the day is over. */
data class ReminderLogEntry(val scheduledFor: Instant, val outcome: ReminderOutcome)

enum class Backoff(val minDaysBetween: Long) { NONE(1), EVERY_OTHER_DAY(2), WEEKLY(7) }

data class ReminderPlan(val at: Instant, val backoff: Backoff, val showAdjustNote: Boolean)

/**
 * US-033, LLD §4.8: at most one reminder a day at the chosen local time; none on a credited streak day; after 3
 * consecutive ignored reminders every other day, after 7 weekly with an "Adjust your reminder?" note; any practice
 * after the last ignored reminder resets the back-off. Ignored = not tapped and no practice that day.
 */
object ReminderPolicy {
    const val EVERY_OTHER_DAY_AFTER = 3
    const val WEEKLY_AFTER = 7
    private const val SEARCH_DAYS = 400L

    /** Consecutive ignored reminders at the end of [log], reset by an opened reminder or a later credited day. */
    fun consecutiveIgnored(log: List<ReminderLogEntry>, credited: Set<LocalDate>, zone: ZoneId): Int {
        val shown = log.filter { it.outcome in setOf(ReminderOutcome.SHOWN, ReminderOutcome.OPENED, ReminderOutcome.IGNORED) }
            .sortedBy { it.scheduledFor }
        val lastPractice = credited.maxOrNull()
        var n = 0
        for (entry in shown.asReversed()) {
            if (entry.outcome != ReminderOutcome.IGNORED) break
            val day = streakDay(entry.scheduledFor, zone)
            if (day in credited) break // practised that day: not really ignored
            if (lastPractice != null && lastPractice > day) break // practised since: reset
            n++
        }
        return n
    }

    fun backoffFor(ignored: Int): Backoff = when {
        ignored >= WEEKLY_AFTER -> Backoff.WEEKLY
        ignored >= EVERY_OTHER_DAY_AFTER -> Backoff.EVERY_OTHER_DAY
        else -> Backoff.NONE
    }

    fun nextReminder(
        now: Instant,
        zone: ZoneId,
        time: LocalTime?,
        log: List<ReminderLogEntry>,
        credited: Set<LocalDate>,
    ): ReminderPlan? {
        if (time == null) return null
        val backoff = backoffFor(consecutiveIgnored(log, credited, zone))
        val lastShownDate = log
            .filter { it.outcome in setOf(ReminderOutcome.SHOWN, ReminderOutcome.OPENED, ReminderOutcome.IGNORED) }
            .maxOfOrNull { it.scheduledFor }
            ?.atZone(zone)?.toLocalDate()
        var date = now.atZone(zone).toLocalDate()
        repeat(SEARCH_DAYS.toInt()) {
            val at = date.atTime(time).atZone(zone).toInstant()
            val spacedOk = lastShownDate == null || ChronoUnit.DAYS.between(lastShownDate, date) >= backoff.minDaysBetween
            if (at.isAfter(now) && spacedOk && streakDay(at, zone) !in credited) {
                return ReminderPlan(at, backoff, showAdjustNote = backoff == Backoff.WEEKLY)
            }
            date = date.plusDays(1)
        }
        return null
    }
}
