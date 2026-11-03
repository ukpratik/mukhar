package io.github.ukpratik.mukhar.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.ukpratik.mukhar.data.analytics.Analytics
import io.github.ukpratik.mukhar.data.analytics.AnalyticsEvent
import io.github.ukpratik.mukhar.data.db.HabitDao
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.reminder.ReminderPlan
import io.github.ukpratik.mukhar.domain.reminder.ReminderPolicy
import io.github.ukpratik.mukhar.domain.streak.streakDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plans the next daily reminder with [ReminderPolicy] and sets one inexact alarm (`setAndAllowWhileIdle`, so it
 * still fires in Doze; no exact-alarm permission, PRD §7). Called after onboarding, on boot, time and zone changes,
 * app updates, each alarm, and app start. Idempotent: the single PendingIntent is replaced each time.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val dao: HabitDao,
    private val clock: Clock,
    private val analytics: Analytics = Analytics.None,
) {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val _next = MutableStateFlow<ReminderPlan?>(null)

    /** The planned reminder, for the debug screen. */
    val next: StateFlow<ReminderPlan?> = _next.asStateFlow()

    suspend fun reschedule(): ReminderPlan? {
        val s = settings.current()
        val credited = creditedDays()
        finaliseLog(credited)
        val time = s.reminder?.time?.takeIf { s.onboardingDone }
        val log = ReminderDecisions.toDomain(dao.recentReminders(LOG_WINDOW))
        val plan = ReminderPolicy.nextReminder(clock.now(), clock.zone(), time, log, credited)
        alarms.cancel(pendingIntent(scheduledFor = 0L))
        if (plan != null) {
            // The alarm runs on the real wall clock; the debug clock may be shifted, so convert via the delay.
            val delay = (plan.at.toEpochMilli() - clock.now().toEpochMilli()).coerceAtLeast(0L)
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delay,
                pendingIntent(plan.at.toEpochMilli()),
            )
        }
        settings.setReminderBackoffLevel(plan?.backoff?.ordinal ?: 0)
        if (plan != null && plan.at != _next.value?.at) analytics.log(AnalyticsEvent.reminderScheduled)
        _next.value = plan
        return plan
    }

    internal suspend fun creditedDays(): Set<LocalDate> = dao.streakDays().map { LocalDate.parse(it.day) }.toSet()

    private suspend fun finaliseLog(credited: Set<LocalDate>) {
        val today = streakDay(clock.now(), clock.zone())
        ReminderDecisions.toIgnore(dao.recentReminders(LOG_WINDOW), credited, today, clock.zone())
            .forEach { dao.markReminderIgnored(it) }
    }

    private fun pendingIntent(scheduledFor: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_SCHEDULED_FOR, scheduledFor),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal companion object {
        const val REQUEST_CODE = 3300
        const val LOG_WINDOW = 30
    }
}
