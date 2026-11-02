package com.vaak.data.work

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.vaak.data.analytics.Analytics
import com.vaak.data.analytics.AnalyticsEvent
import com.vaak.data.db.HabitDao
import com.vaak.data.db.ReminderLogEntity
import com.vaak.data.prefs.SettingsStore
import com.vaak.domain.Clock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Alarm → decide → notify → log → plan the next one (LLD §4.9 `ReminderReceiver`). */
@Singleton
class ReminderHandler @Inject constructor(
    private val settings: SettingsStore,
    private val dao: HabitDao,
    private val clock: Clock,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val analytics: Analytics = Analytics.None,
) {
    suspend fun onAlarm(scheduledFor: Instant): FireDecision {
        val s = settings.current()
        val reminder = s.reminder?.takeIf { s.onboardingDone }
        val decision = ReminderDecisions.onFire(
            now = clock.now(),
            zone = clock.zone(),
            scheduledFor = scheduledFor,
            time = reminder?.time,
            log = ReminderDecisions.toDomain(dao.recentReminders(ReminderScheduler.LOG_WINDOW)),
            credited = scheduler.creditedDays(),
        )
        when (decision) {
            is FireDecision.Show -> if (reminder != null && notifier.canPost()) {
                val now = clock.now().toEpochMilli()
                val id = dao.insertReminderLog(ReminderLogEntity(scheduledFor = scheduledFor.toEpochMilli(), shownAt = now, openedAt = null, outcome = "shown"))
                notifier.show(id, reminder, decision.adjustNote)
                analytics.log(AnalyticsEvent.reminderShown)
            }
            is FireDecision.Skip -> decision.outcome?.let {
                dao.insertReminderLog(ReminderLogEntity(scheduledFor = scheduledFor.toEpochMilli(), shownAt = null, openedAt = null, outcome = it.key))
            }
        }
        scheduler.reschedule()
        return decision
    }

    suspend fun onOpened(logId: Long) {
        dao.markReminderOpened(logId, clock.now().toEpochMilli())
        analytics.log(AnalyticsEvent.reminderOpened)
    }
}

/** Work started from a receiver or trampoline outlives it via goAsync / this process scope. */
internal val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: ReminderHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val scheduledFor = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SCHEDULED_FOR, System.currentTimeMillis()))
        val pending = goAsync()
        reminderScope.launch {
            try {
                handler.onAlarm(scheduledFor)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.vaak.reminder.FIRE"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
    }
}

/** Reschedules after reboot, a time or zone change, and an app update (US-033). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var entitlements: com.vaak.data.repo.EntitlementRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        reminderScope.launch {
            try {
                scheduler.reschedule()
                // The trial alarm (US-062) is lost on reboot too.
                entitlements.rescheduleReminder()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

/**
 * Notification tap: logs `opened` (resets the back-off), then opens the app. An activity trampoline, which Android 12+
 * allows (receiver trampolines are blocked).
 */
class ReminderOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
        if (logId >= 0) {
            val handler = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java).handler()
            reminderScope.launch { handler.onOpened(logId) }
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
        finish()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun handler(): ReminderHandler
    }

    companion object {
        const val EXTRA_LOG_ID = "reminder_log_id"
    }
}
