package io.github.ukpratik.mukhar.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ukpratik.mukhar.data.R
import io.github.ukpratik.mukhar.data.util.AppVisibility

/** "Results ready" and "talk paused" notifications (US-072, S09 notes). Silent when notifications aren't allowed. */
interface TalkNotifier {
    fun resultsReady(sessionId: String)
    fun talkPaused()
    fun clearPaused()
}

class AndroidTalkNotifier(private val context: Context) : TalkNotifier {
    override fun resultsReady(sessionId: String) {
        if (AppVisibility.isForeground) return
        post(ID_RESULTS, context.getString(R.string.notify_results_title), context.getString(R.string.notify_results_body))
    }

    override fun talkPaused() =
        post(ID_PAUSED, context.getString(R.string.notify_paused_title), context.getString(R.string.notify_paused_body))

    override fun clearPaused() = NotificationManagerCompat.from(context).cancel(ID_PAUSED)

    private fun post(id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // asked for in onboarding (M3); denial is respected
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tap = launch?.let { PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_talk)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call: nothing to show.
        }
    }

    private companion object {
        const val CHANNEL = "talks"
        const val ID_RESULTS = 1001
        const val ID_PAUSED = 1002
    }
}
