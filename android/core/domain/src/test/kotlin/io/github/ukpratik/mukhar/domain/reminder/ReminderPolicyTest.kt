package io.github.ukpratik.mukhar.domain.reminder

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderPolicyTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val ny = ZoneId.of("America/New_York")
    private val at1930 = LocalTime.of(19, 30)

    private fun istAt(date: String, time: String) = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ist).toInstant()
    private fun ignored(vararg dates: String) = dates.map { ReminderLogEntry(istAt(it, "19:30"), ReminderOutcome.IGNORED) }

    @Test
    fun nextOccurrenceTodayOrTomorrow() {
        val before = ReminderPolicy.nextReminder(istAt("2026-11-02", "10:00"), ist, at1930, emptyList(), emptySet())!!
        assertThat(before.at).isEqualTo(istAt("2026-11-02", "19:30"))
        assertThat(before.backoff).isEqualTo(Backoff.NONE)
        val after = ReminderPolicy.nextReminder(istAt("2026-11-02", "19:30"), ist, at1930, emptyList(), emptySet())!!
        assertThat(after.at).isEqualTo(istAt("2026-11-03", "19:30"))
    }

    @Test
    fun noTimeMeansNoReminder() {
        assertThat(ReminderPolicy.nextReminder(Instant.EPOCH, ist, null, emptyList(), emptySet())).isNull()
    }

    @Test
    fun skipsACreditedDay() {
        val plan = ReminderPolicy.nextReminder(istAt("2026-11-02", "10:00"), ist, at1930, emptyList(), setOf(LocalDate.parse("2026-11-02")))!!
        assertThat(plan.at).isEqualTo(istAt("2026-11-03", "19:30"))
    }

    @Test
    fun atMostOnePerDay() {
        val log = listOf(ReminderLogEntry(istAt("2026-11-02", "08:00"), ReminderOutcome.SHOWN))
        // Reminder time changed to later the same day: still only one that day.
        val plan = ReminderPolicy.nextReminder(istAt("2026-11-02", "10:00"), ist, at1930, log, emptySet())!!
        assertThat(plan.at).isEqualTo(istAt("2026-11-03", "19:30"))
    }

    @Test
    fun backoffTable() {
        data class Row(val ignoredDays: List<String>, val expected: Backoff, val next: String)
        val rows = listOf(
            Row(listOf("2026-11-01", "2026-11-02"), Backoff.NONE, "2026-11-03"),
            Row(listOf("2026-10-31", "2026-11-01", "2026-11-02"), Backoff.EVERY_OTHER_DAY, "2026-11-04"),
            Row((0L until 6L).map { LocalDate.parse("2026-10-28").plusDays(it).toString() }, Backoff.EVERY_OTHER_DAY, "2026-11-04"),
            Row((0L until 7L).map { LocalDate.parse("2026-10-27").plusDays(it).toString() }, Backoff.WEEKLY, "2026-11-09"),
        )
        for (r in rows) {
            val plan = ReminderPolicy.nextReminder(istAt("2026-11-02", "21:00"), ist, at1930, ignored(*r.ignoredDays.toTypedArray()), emptySet())!!
            assertThat(plan.backoff).isEqualTo(r.expected)
            assertThat(plan.at).isEqualTo(istAt(r.next, "19:30"))
            assertThat(plan.showAdjustNote).isEqualTo(r.expected == Backoff.WEEKLY)
        }
    }

    @Test
    fun practiceResetsBackoff() {
        val log = ignored("2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31")
        assertThat(ReminderPolicy.consecutiveIgnored(log, emptySet(), ist)).isEqualTo(4)
        // Practised on Nov 1 after the last ignored reminder.
        assertThat(ReminderPolicy.consecutiveIgnored(log, setOf(LocalDate.parse("2026-11-01")), ist)).isEqualTo(0)
        val plan = ReminderPolicy.nextReminder(istAt("2026-11-01", "21:00"), ist, at1930, log, setOf(LocalDate.parse("2026-11-01")))!!
        assertThat(plan.backoff).isEqualTo(Backoff.NONE)
        assertThat(plan.at).isEqualTo(istAt("2026-11-02", "19:30"))
    }

    @Test
    fun openedReminderBreaksTheIgnoredRun() {
        val log = ignored("2026-10-28", "2026-10-29") +
            ReminderLogEntry(istAt("2026-10-30", "19:30"), ReminderOutcome.OPENED) + ignored("2026-10-31", "2026-11-01")
        assertThat(ReminderPolicy.consecutiveIgnored(log, emptySet(), ist)).isEqualTo(2)
    }

    @Test
    fun reminderOnAPractisedDayIsNotIgnored() {
        val log = ignored("2026-10-30", "2026-10-31", "2026-11-01")
        assertThat(ReminderPolicy.consecutiveIgnored(log, setOf(LocalDate.parse("2026-10-31")), ist)).isEqualTo(1)
    }

    @Test
    fun dstGapMovesTheReminderForward() {
        // 02:30 doesn't exist in New York on 2026-03-08: java.time shifts it to 03:30 EDT.
        val now = Instant.parse("2026-03-08T05:00:00Z") // 00:00 EST
        val plan = ReminderPolicy.nextReminder(now, ny, LocalTime.of(2, 30), emptyList(), emptySet())!!
        assertThat(plan.at).isEqualTo(Instant.parse("2026-03-08T07:30:00Z"))
    }

    @Test
    fun timeZoneChangeUsesTheNewLocalTime() {
        val now = Instant.parse("2026-11-02T12:00:00Z")
        val plan = ReminderPolicy.nextReminder(now, ny, at1930, emptyList(), emptySet())!!
        assertThat(plan.at).isEqualTo(Instant.parse("2026-11-03T00:30:00Z")) // 19:30 EST
    }
}
