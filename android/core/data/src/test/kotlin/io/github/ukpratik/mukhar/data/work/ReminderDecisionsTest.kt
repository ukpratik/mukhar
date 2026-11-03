package io.github.ukpratik.mukhar.data.work

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.db.ReminderLogEntity
import io.github.ukpratik.mukhar.domain.reminder.ReminderLogEntry
import io.github.ukpratik.mukhar.domain.reminder.ReminderOutcome
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Fire-time decisions (US-033). */
class ReminderDecisionsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val t1930 = LocalTime.of(19, 30)

    private fun at(date: String, time: String = "19:30"): Instant = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ist).toInstant()
    private fun d(s: String) = LocalDate.parse(s)
    private fun ignored(vararg dates: String) = dates.map { ReminderLogEntry(at(it), ReminderOutcome.IGNORED) }

    private fun decide(
        now: Instant = at("2026-11-05", "19:32"),
        scheduled: Instant = at("2026-11-05"),
        time: LocalTime? = t1930,
        log: List<ReminderLogEntry> = emptyList(),
        credited: Set<LocalDate> = emptySet(),
    ) = ReminderDecisions.onFire(now, ist, scheduled, time, log, credited)

    @Test
    fun showsOnAnOrdinaryDay() {
        assertThat(decide()).isEqualTo(FireDecision.Show(adjustNote = false))
    }

    @Test
    fun skipsWhenTheDayIsAlreadyCredited() {
        assertThat(decide(credited = setOf(d("2026-11-05")))).isEqualTo(FireDecision.Skip(ReminderOutcome.SKIPPED_CREDITED))
    }

    @Test
    fun atMostOneADay() {
        val log = listOf(ReminderLogEntry(at("2026-11-05", "08:15"), ReminderOutcome.SHOWN))
        assertThat(decide(log = log)).isEqualTo(FireDecision.Skip(null))
    }

    @Test
    fun turnedOffSinceSchedulingShowsNothing() {
        assertThat(decide(time = null)).isEqualTo(FireDecision.Skip(null))
    }

    @Test
    fun aVeryLateAlarmIsDropped() {
        assertThat(decide(now = at("2026-11-05", "22:00"))).isEqualTo(FireDecision.Skip(null))
    }

    @Test
    fun threeIgnoredMeansEveryOtherDay() {
        val log = ignored("2026-11-02", "2026-11-03", "2026-11-04")
        // Last shown Nov 4, every other day: Nov 5 is suppressed, Nov 6 shows.
        assertThat(decide(log = log)).isEqualTo(FireDecision.Skip(ReminderOutcome.SUPPRESSED_BACKOFF))
        assertThat(decide(now = at("2026-11-06", "19:31"), scheduled = at("2026-11-06"), log = log))
            .isEqualTo(FireDecision.Show(adjustNote = false))
    }

    @Test
    fun sevenIgnoredMeansWeeklyWithTheAdjustNote() {
        val log = ignored("2026-10-23", "2026-10-24", "2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29")
        assertThat(decide(now = at("2026-11-05", "19:31"), log = log)).isEqualTo(FireDecision.Show(adjustNote = true))
        assertThat(decide(now = at("2026-11-04", "19:31"), scheduled = at("2026-11-04"), log = log))
            .isEqualTo(FireDecision.Skip(ReminderOutcome.SUPPRESSED_BACKOFF))
    }

    @Test
    fun practiceResetsTheBackoff() {
        val log = ignored("2026-11-01", "2026-11-02", "2026-11-03")
        assertThat(decide(log = log, credited = setOf(d("2026-11-04")))).isEqualTo(FireDecision.Show(adjustNote = false))
    }

    @Test
    fun shownRemindersFromPastUncreditedDaysBecomeIgnored() {
        fun row(id: Long, date: String, outcome: String) = ReminderLogEntity(id, at(date).toEpochMilli(), null, null, outcome)
        val log = listOf(
            row(1, "2026-11-02", "shown"), // practised that day: stays shown
            row(2, "2026-11-03", "shown"), // ignored
            row(3, "2026-11-04", "opened"),
            row(4, "2026-11-05", "shown"), // today: not final yet
        )
        val ids = ReminderDecisions.toIgnore(log, credited = setOf(d("2026-11-02")), today = d("2026-11-05"), zone = ist)
        assertThat(ids).containsExactly(2L)
    }
}
