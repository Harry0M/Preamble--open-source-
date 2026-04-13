package com.theblankstate.preamble.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.R
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Fires daily at 6 AM. Counts today's pending tasks (all types including rollover).
 * Shows a single beautiful summary notification with a unique style for each day of the week.
 * Then reschedules itself for tomorrow 6 AM.
 */
class EveningReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.theblankstate.preamble.EVENING_REMINDER"
        const val CHANNEL_ID = "preamble_evening_reminder"
        const val NOTIFICATION_ID = 7002
        private const val TAG = "EveningReminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        Log.d(TAG, "Morning reminder fired")
        val pendingResult = goAsync()
        val app = context.applicationContext as PreambleApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = TaskRepository.todayString()
                val pendingTasks = app.database.taskDao().getPendingTasksForDate(today)

                // Only notify if there are pending tasks
                if (pendingTasks.isNotEmpty()) {
                    val count = pendingTasks.size
                    ensureChannel(context)
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(context, count, pendingTasks.map { it.title }))
                    Log.d(TAG, "Morning notification posted: $count tasks")
                } else {
                    Log.d(TAG, "All tasks done — skipping morning notification")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in evening reminder", e)
            } finally {
                // Reschedule for tomorrow 6 PM
                EveningReminderScheduler.schedule(context)
                pendingResult.finish()
            }
        }
    }

    private fun buildNotification(context: Context, count: Int, taskTitles: List<String>) =
        run {
            val cal = Calendar.getInstance()
            val dow = cal.get(Calendar.DAY_OF_WEEK)

            val (title, body) = getDayStyle(dow, count)

            // Build BigTextStyle with task list
            val listLines = taskTitles.take(5).joinToString("\n") { "  •  $it" }
            val overflow = if (count > 5) "\n  +${count - 5} more…" else ""
            val bigText = "$body\n\n$listLines$overflow"

            val openIntent = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText).setBigContentTitle(title))
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .build()
        }

    /**
     * 7 unique styles — one per day of week.
     * Returns Pair(title, bodyText).
     */
    private fun getDayStyle(dayOfWeek: Int, count: Int): Pair<String, String> {
        val n = count
        val tasks = if (n == 1) "task" else "tasks"
        return when (dayOfWeek) {
            Calendar.MONDAY -> Pair(
                "Monday, let's go! 🚀",
                "Good morning — $n $tasks ahead of you today. New week, fresh start."
            )
            Calendar.TUESDAY -> Pair(
                "Tuesday Briefing ☀️",
                "Rise and plan — $n $tasks on your plate today. You've got this."
            )
            Calendar.WEDNESDAY -> Pair(
                "Midweek Morning ⚡",
                "Good morning! $n $tasks today. Halfway through the week — keep the rhythm."
            )
            Calendar.THURSDAY -> Pair(
                "Thursday Check-In �️",
                "Almost Friday! $n $tasks to tackle today. Finish strong."
            )
            Calendar.FRIDAY -> Pair(
                "Friday Morning 🎯",
                "Last sprint of the week — $n $tasks today. Clear the board before the weekend."
            )
            Calendar.SATURDAY -> Pair(
                "Saturday Morning 🌿",
                "No rush, but $n $tasks are waiting. Even slow progress counts."
            )
            Calendar.SUNDAY -> Pair(
                "Sunday Sunrise 🌅",
                "A calm morning, $n $tasks on the list. Set the tone for your week."
            )
            else -> Pair(
                "Morning Briefing �",
                "Good morning — $n $tasks are waiting for you today."
            )
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Morning Briefing",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily 6 AM morning briefing of pending tasks"
            enableVibration(false)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }
}
