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

class WeeklyWrappedReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WEEKLY_WRAPPED = "com.theblankstate.preamble.ACTION_WEEKLY_WRAPPED"
        const val EXTRA_OPEN_WRAPPED = "open_wrapped"
        private const val CHANNEL_ID = "preamble_weekly_wrapped"
        private const val NOTIFICATION_ID = 2100
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WEEKLY_WRAPPED) return
        ensureChannel(context)
        postNotification(context)
        WeeklyWrappedScheduler.schedule(context)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weekly Wrapped",
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
            putExtra(EXTRA_OPEN_WRAPPED, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your week, wrapped.")
            .setContentText("Tap to see how this week went.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notif)
    }
}
