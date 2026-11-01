package com.vaak.data.work

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.vaak.data.TestStores
import com.vaak.data.db.StreakDayEntity
import com.vaak.data.prefs.ReminderSettings
import com.vaak.data.prefs.Routine
import com.vaak.domain.FixedClock
import com.vaak.domain.reminder.Backoff
import com.vaak.domain.reminder.ReminderOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** ReminderScheduler + ReminderHandler with real AlarmManager / NotificationManager shadows (US-033). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSchedulerTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val stores = TestStores()
    private val dao = stores.db.habitDao()
    private val clock = FixedClock(at("2026-11-05", "10:00"), ist)
    private val scheduler = ReminderScheduler(context, stores.settings, dao, clock)
    private val notifier = ReminderNotifier(context)
    private val handler = ReminderHandler(stores.settings, dao, clock, scheduler, notifier)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    private fun at(date: String, time: String): Instant = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ist).toInstant()

    @Before
    fun setUp() = runBlocking<Unit> {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        stores.settings.setReminder(ReminderSettings(LocalTime.of(19, 30), Routine.MORNING_TEA))
        stores.settings.completeOnboarding(clock.now())
    }

    @After
    fun tearDown() = stores.close()

    @Test
    fun noReminderBeforeOnboardingIsDone() = runBlocking<Unit> {
        val fresh = TestStores()
        try {
            fresh.settings.setReminder(ReminderSettings(LocalTime.of(19, 30), Routine.LUNCH))
            val plan = ReminderScheduler(context, fresh.settings, fresh.db.habitDao(), clock).reschedule()
            assertThat(plan).isNull()
        } finally {
            fresh.close()
        }
    }

    @Test
    fun schedulesOneInexactAlarmAtTheChosenTime() = runBlocking<Unit> {
        val plan = scheduler.reschedule()!!
        assertThat(plan.at).isEqualTo(at("2026-11-05", "19:30"))
        assertThat(plan.backoff).isEqualTo(Backoff.NONE)
        assertThat(alarms.scheduledAlarms).hasSize(1)
        val alarm = alarms.peekNextScheduledAlarm()!!
        assertThat(alarm.type).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.isAllowWhileIdle).isTrue()
        // Rescheduling replaces the alarm rather than adding a second one.
        scheduler.reschedule()
        assertThat(alarms.scheduledAlarms).hasSize(1)
    }

    @Test
    fun aCreditedDayMovesTheReminderToTomorrow() = runBlocking<Unit> {
        dao.insertStreakDay(StreakDayEntity("2026-11-05", "talk", 0, ist.id))
        assertThat(scheduler.reschedule()!!.at).isEqualTo(at("2026-11-06", "19:30"))
    }

    @Test
    fun turningTheReminderOffCancelsTheAlarm() = runBlocking<Unit> {
        scheduler.reschedule()
        stores.settings.setReminder(null)
        assertThat(scheduler.reschedule()).isNull()
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun theAlarmShowsOneNotificationWithTheRoutinePhraseAndLogsIt() = runBlocking<Unit> {
        clock.instant = at("2026-11-05", "19:31")
        val decision = handler.onAlarm(at("2026-11-05", "19:30"))
        assertThat(decision).isEqualTo(FireDecision.Show(adjustNote = false))
        val posted = notifications.allNotifications
        assertThat(posted).hasSize(1)
        val text = posted.single().extras.getCharSequence("android.text").toString()
        assertThat(text).isEqualTo("After your morning tea: today's talk is ready ☕")
        assertThat(posted.single().channelId).isEqualTo(NotificationChannels.REMINDERS)

        val log = dao.recentReminders()
        assertThat(log.map { it.outcome }).containsExactly("shown")
        assertThat(log.single().shownAt).isEqualTo(clock.now().toEpochMilli())
        // Next one planned for tomorrow, and a second alarm today shows nothing.
        assertThat(scheduler.next.value!!.at).isEqualTo(at("2026-11-06", "19:30"))
        assertThat(handler.onAlarm(at("2026-11-05", "19:30"))).isEqualTo(FireDecision.Skip(null))
        assertThat(notifications.allNotifications).hasSize(1)

        handler.onOpened(log.single().id)
        assertThat(dao.recentReminders().single().outcome).isEqualTo("opened")
    }

    @Test
    fun aCreditedDayIsSkippedAndLogged() = runBlocking<Unit> {
        dao.insertStreakDay(StreakDayEntity("2026-11-05", "review", 0, ist.id))
        clock.instant = at("2026-11-05", "19:31")
        assertThat(handler.onAlarm(at("2026-11-05", "19:30"))).isEqualTo(FireDecision.Skip(ReminderOutcome.SKIPPED_CREDITED))
        assertThat(notifications.allNotifications).isEmpty()
        assertThat(dao.recentReminders().map { it.outcome }).containsExactly("skipped_credited")
    }

    @Test
    fun noNotificationWithoutThePermission() = runBlocking<Unit> {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        clock.instant = at("2026-11-05", "19:31")
        handler.onAlarm(at("2026-11-05", "19:30"))
        assertThat(notifications.allNotifications).isEmpty()
        assertThat(dao.recentReminders()).isEmpty()
    }

    @Test
    fun ignoredRemindersBackOffToEveryOtherDay() = runBlocking<Unit> {
        // Shown and not acted on for 3 days in a row.
        for (day in listOf("2026-11-02", "2026-11-03", "2026-11-04")) {
            dao.insertReminderLog(com.vaak.data.db.ReminderLogEntity(scheduledFor = at(day, "19:30").toEpochMilli(), shownAt = 1, openedAt = null, outcome = "shown"))
        }
        clock.instant = at("2026-11-05", "10:00")
        val plan = scheduler.reschedule()!!
        assertThat(plan.backoff).isEqualTo(Backoff.EVERY_OTHER_DAY)
        assertThat(plan.at).isEqualTo(at("2026-11-06", "19:30"))
        assertThat(dao.recentReminders().map { it.outcome }).containsExactly("ignored", "ignored", "ignored")
        assertThat(stores.settings.current().reminderBackoffLevel).isEqualTo(Backoff.EVERY_OTHER_DAY.ordinal)
    }

    @Test
    fun channelsIncludeTheTrialStub() {
        NotificationChannels.ensure(context)
        val ids = context.getSystemService(NotificationManager::class.java).notificationChannels.map { it.id }
        assertThat(ids).containsExactly(NotificationChannels.REMINDERS, NotificationChannels.TRIAL)
    }
}
