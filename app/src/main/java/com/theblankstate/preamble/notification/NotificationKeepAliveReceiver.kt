package com.theblankstate.preamble.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationKeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEEP_ALIVE) return

        // Always reschedule the next tick first
        NotificationKeepAliveScheduler.schedule(context)

        if (!TaskNotificationService.isEnabled(context)) return

        val nm = context.getSystemService(NotificationManager::class.java)
        val isVisible = nm.activeNotifications.any {
            it.id == TaskNotificationService.NOTIFICATION_ID
        }

        if (!isVisible) {
            Log.d(TAG, "Notification missing - restarting service")
            TaskNotificationService.start(context)
        }
    }

    companion object {
        const val ACTION_KEEP_ALIVE = "com.theblankstate.preamble.ACTION_KEEP_ALIVE"
        private const val TAG = "NotifKeepAlive"
    }
}
