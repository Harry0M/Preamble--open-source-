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

    suspend fun updateNotification(context: Context) {
        val app = context.applicationContext as PreambleApplication
        val pendingTasks = withContext(Dispatchers.IO) {
            app.repository.getPendingTasksToday()
        }
        val pendingCount = pendingTasks.size

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Open app intent
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick add reply action
        val remoteInput = RemoteInput.Builder(KEY_TASK_TEXT)
            .setLabel("Type task...")
            .build()

        val addIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_QUICK_ADD
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val addAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Quick Add",
            addIntent
        ).addRemoteInput(remoteInput).build()

        val contentText = if (pendingCount > 0) {
            "$pendingCount task${if (pendingCount > 1) "s" else ""} pending"
        } else {
            "All done for today!"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Preamble")
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .addAction(addAction)

        // Voice task action
        val voiceIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, VoiceTaskService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val voiceAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "🎙 Voice Task",
            voiceIntent
        ).build()
        builder.addAction(voiceAction)
        builder
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (pendingCount > 0) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("Pending Tasks")
            pendingTasks.take(5).forEach { task ->
                inboxStyle.addLine("• ${task.title}")
            }
            if (pendingCount > 5) {
                inboxStyle.setSummaryText("+${pendingCount - 5} more")
            }
            builder.setStyle(inboxStyle)
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }
}
