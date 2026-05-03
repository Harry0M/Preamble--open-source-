package com.theblankstate.preamble.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.R

class WeeklyRecapReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WEEKLY_RECAP = "com.theblankstate.preamble.ACTION_WEEKLY_RECAP"
        const val EXTRA_OPEN_RECAP = "open_recap"
        private const val CHANNEL_ID = "preamble_weekly_recap"
        private const val NOTIFICATION_ID = 2100
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WEEKLY_RECAP) return
        ensureChannel(context)
        postNotification(context)
        WeeklyRecapScheduler.schedule(context)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weekly Recap",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Your week-in-review recap"
        }
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(channel)
    }

    private fun postNotification(context: Context) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_RECAP, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your weekly recap")
            .setContentText("Tap to see how this week went.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notif)
    }
}
