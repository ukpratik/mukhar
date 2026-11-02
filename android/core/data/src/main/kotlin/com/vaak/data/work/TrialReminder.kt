package com.vaak.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vaak.data.R
import com.vaak.data.billing.PlayLinks
import com.vaak.data.prefs.SettingsStore
import com.vaak.domain.Clock
import com.vaak.domain.billing.EntitlementRules
import com.vaak.domain.billing.EntitlementSnapshot
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The trial reminder alarm (US-062): one, at expiry − 24 h. Faked in repository tests. */
interface TrialReminderScheduler {
    fun schedule(at: Instant)
    fun cancel()
}

/**
 * `setAndAllowWhileIdle` on the trial channel (transactional: it's sent even when daily reminders are off). Like the
 * daily reminder, the alarm runs on the wall clock, so a shifted debug clock is converted through the delay.
 */
@Singleton
class AlarmTrialReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : TrialReminderScheduler {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        val delay = (at.toEpochMilli() - clock.now().toEpochMilli()).coerceAtLeast(0L)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pendingIntent())
    }

    override fun cancel() = alarms.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, TrialReminderReceiver::class.java).setAction(TrialReminderReceiver.ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 3310
    }
}

/** Posts "Your trial ends tomorrow" if the trial is still converting when the alarm fires. */
@Singleton
class TrialReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun onAlarm() {
        val e = settings.entitlement.first()
        val at = EntitlementRules.reminderAt(e, clock.now()) ?: return
        if (clock.now().isBefore(at.minusSeconds(EARLY_TOLERANCE_S))) return
        show(e)
    }

    private fun show(e: EntitlementSnapshot) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        NotificationChannels.ensure(context)
        val res = context.resources
        val expiry = Instant.ofEpochMilli(e.expiryMs ?: return).atZone(clock.zone())
        val date = expiry.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
        val price = e.priceLabel
        val body = if (price != null) res.getString(R.string.trial_notif_body_price, price, date) else res.getString(R.string.trial_notif_body, date)
        val manage = PendingIntent.getActivity(
            context,
            REQUEST_MANAGE,
            Intent(Intent.ACTION_VIEW, PlayLinks.manageSubscription(context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.TRIAL)
            .setSmallIcon(R.drawable.ic_stat_vaak)
            .setContentTitle(res.getString(R.string.trial_notif_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(manage)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notifications denied: the day-6 banner on Today is the guarantee (US-062).
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 3311
        const val REQUEST_MANAGE = 3312
        const val EARLY_TOLERANCE_S = 15 * 60L
    }
}

@AndroidEntryPoint
class TrialReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: TrialReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        reminderScope.launch {
            try {
                notifier.onAlarm()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.vaak.trial.REMIND"
    }
}
