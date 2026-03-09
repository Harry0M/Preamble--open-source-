package com.theblankstate.preamble.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TaskNotificationManager {

    private const val CHANNEL_ID = "preamble_tasks"
    private const val NOTIFICATION_ID = 1001
    const val ACTION_QUICK_ADD = "com.theblankstate.preamble.ACTION_QUICK_ADD"
        const val ACTION_NOTIFICATION_DISMISSED = "com.theblankstate.preamble.ACTION_NOTIFICATION_DISMISSED"
    const val KEY_TASK_TEXT = "key_task_text"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tasks",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Daily task notifications"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    @Deprecated(
        "Use TaskNotificationService instead for persistent notification",
        ReplaceWith("TaskNotificationService.start(context)", "com.theblankstate.preamble.notification.TaskNotificationService")
    )
    suspend fun updateNotification(context: Context) {
        // Notification is now handled by TaskNotificationService
        // This method is kept for backward compatibility but does nothing
    }
}
