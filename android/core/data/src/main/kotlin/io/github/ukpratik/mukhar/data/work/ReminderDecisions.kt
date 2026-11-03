package io.github.ukpratik.mukhar.data.work

import io.github.ukpratik.mukhar.data.db.ReminderLogEntity
import io.github.ukpratik.mukhar.domain.reminder.ReminderLogEntry
import io.github.ukpratik.mukhar.domain.reminder.ReminderOutcome
import io.github.ukpratik.mukhar.domain.reminder.ReminderPolicy
import io.github.ukpratik.mukhar.domain.streak.streakDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What to do when the reminder alarm fires. */
sealed interface FireDecision {
    data class Show(val adjustNote: Boolean) : FireDecision

    /** Not shown. [outcome] is logged when it's a policy outcome; `null` means nothing to log. */
    data class Skip(val outcome: ReminderOutcome?) : FireDecision
}

/**
 * Fire-time checks for US-033, on top of [ReminderPolicy] (which planned the alarm): the day may have been credited
 * since, the time may have changed, or a reminder may already have been shown today. Pure, so it's table-tested.
 */
object ReminderDecisions {
    /** An alarm delivered this late (phone off, deep idle) is dropped rather than shown at an odd hour. */
    val STALE_AFTER: Duration = Duration.ofHours(2)

    private val SHOWN_LIKE = setOf(ReminderOutcome.SHOWN, ReminderOutcome.OPENED, ReminderOutcome.IGNORED)

    fun onFire(
        now: Instant,
        zone: ZoneId,
        scheduledFor: Instant,
        time: LocalTime?,
        log: List<ReminderLogEntry>,
        credited: Set<LocalDate>,
    ): FireDecision {
        if (time == null) return FireDecision.Skip(null) // reminder turned off since it was scheduled
        if (Duration.between(scheduledFor, now) > STALE_AFTER) return FireDecision.Skip(null)
        if (streakDay(now, zone) in credited) return FireDecision.Skip(ReminderOutcome.SKIPPED_CREDITED)
        val date = now.atZone(zone).toLocalDate()
        if (log.any { it.outcome in SHOWN_LIKE && it.scheduledFor.atZone(zone).toLocalDate() == date }) {
            return FireDecision.Skip(null) // at most one a day
        }
        val dayStart = date.atStartOfDay(zone).toInstant().minusMillis(1)
        val plan = ReminderPolicy.nextReminder(dayStart, zone, time, log, credited)
        if (plan == null || plan.at.atZone(zone).toLocalDate() != date) {
            return FireDecision.Skip(ReminderOutcome.SUPPRESSED_BACKOFF)
        }
        return FireDecision.Show(plan.showAdjustNote)
    }

    /**
     * Shown reminders from earlier streak days that were neither tapped nor followed by practice that day become
     * `ignored` (the back-off input). Returns the row ids to update.
     */
    fun toIgnore(log: List<ReminderLogEntity>, credited: Set<LocalDate>, today: LocalDate, zone: ZoneId): List<Long> =
        log.filter { it.outcome == ReminderOutcome.SHOWN.key }
            .filter {
                val day = streakDay(Instant.ofEpochMilli(it.scheduledFor), zone)
                day < today && day !in credited
            }
            .map { it.id }

    fun toDomain(log: List<ReminderLogEntity>): List<ReminderLogEntry> = log.mapNotNull { row ->
        ReminderOutcome.entries.firstOrNull { it.key == row.outcome }
            ?.let { ReminderLogEntry(Instant.ofEpochMilli(row.scheduledFor), it) }
    }
}
