package com.theblankstate.preamble.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.theblankstate.preamble.R

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskTitle = intent.getStringExtra("task_title") ?: "Task Reminder"
        val notificationId = taskTitle.hashCode()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Use saved alarm tone or fall back to defaults
        val prefs = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
        val savedToneUri = prefs.getString("alarm_tone_uri", null)
        val alarmSound = if (savedToneUri != null) {
            Uri.parse(savedToneUri)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "task_reminders",
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for task deadlines"
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            nm.createNotificationChannel(channel)
        }

        // Build dismiss intent
        val dismissIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "task_reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Task Reminder")
            .setContentText(taskTitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(false)
            .setOngoing(true)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss", dismissPendingIntent)
            .build()

        nm.notify(notificationId, notification)

        // Play ringtone and store reference so AlarmDismissReceiver can stop it
        try {
            val ringtone = RingtoneManager.getRingtone(context, alarmSound)
            AlarmDismissReceiver.activeRingtone = ringtone
            ringtone?.play()
        } catch (_: Exception) { }
    }
}
