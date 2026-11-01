package com.vaak.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vaak.data.R
import com.vaak.data.prefs.ReminderSettings
import com.vaak.data.prefs.Routine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The notification channels. The trial channel is a stub for M5 (US-062 trial reminder, transactional). */
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val TRIAL = "trial"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_reminders_desc) },
                NotificationChannel(TRIAL, context.getString(R.string.channel_trial), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_trial_desc) },
            ),
        )
    }
}

/** Reminder copy shared by the S06 preview and the notification, so the preview is exactly what arrives (S06). */
object ReminderCopy {
    fun routineLabel(res: Resources, routine: Routine): String = res.getString(
        when (routine) {
            Routine.MORNING_TEA -> R.string.routine_morning_tea
            Routine.COMMUTE -> R.string.routine_commute
            Routine.LUNCH -> R.string.routine_lunch
            Routine.WORKOUT -> R.string.routine_workout
            Routine.DINNER -> R.string.routine_dinner
            Routine.CUSTOM -> R.string.routine_custom
        },
    )

    /** "After your morning tea" for a chip; "After walking the dog" for a custom phrase (used as-is). */
    fun phrase(res: Resources, reminder: ReminderSettings): String =
        if (reminder.routine == Routine.CUSTOM) {
            res.getString(R.string.reminder_phrase_custom, reminder.customPhrase.orEmpty())
        } else {
            res.getString(R.string.reminder_phrase_preset, routineLabel(res, reminder.routine))
        }

    /** The bold phrase, then ": today's talk is ready" (☕ only for tea). */
    fun body(res: Resources, reminder: ReminderSettings): CharSequence {
        val phrase = phrase(res, reminder)
        val text = res.getString(
            if (reminder.routine == Routine.MORNING_TEA) R.string.reminder_body_tea else R.string.reminder_body,
            phrase,
        )
        return SpannableStringBuilder(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, phrase.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

/** Posts the single daily reminder (one notification id, so there's never more than one on screen). */
@Singleton
class ReminderNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationChannels.REMINDERS)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(logId: Long, reminder: ReminderSettings, adjustNote: Boolean) {
        if (!canPost()) return
        NotificationChannels.ensure(context)
        val res = context.resources
        val body = ReminderCopy.body(res, reminder)
        val open = PendingIntent.getActivity(
            context,
            logId.toInt(),
            Intent(context, ReminderOpenActivity::class.java).putExtra(ReminderOpenActivity.EXTRA_LOG_ID, logId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_vaak)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (adjustNote) {
            val adjust = res.getString(R.string.reminder_adjust)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(SpannableStringBuilder(body).append("\n").append(adjust)))
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: the reminder is simply not shown.
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 3301
    }
}
