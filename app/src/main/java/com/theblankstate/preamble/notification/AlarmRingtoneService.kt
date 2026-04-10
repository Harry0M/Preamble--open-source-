package com.theblankstate.preamble.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Industry-grade alarm foreground service.
 *
 * Architecture (same as Google Clock / Samsung Clock):
 * 1. AlarmReceiver.onReceive() starts this service
 * 2. Service acquires wakelock, plays ringtone, vibrates, shows fullScreenIntent notification
 * 3. User actions (Stop/Complete/Snooze) are handled as service commands
 * 4. Auto-stops after MAX_ALARM_DURATION_MS as a failsafe
 */
class AlarmRingtoneService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var isAlarmActive = false

    companion object {
        private const val TAG = "PreambleAlarm"
        private const val CHANNEL_ID = "task_reminders"
        private const val MAX_ALARM_DURATION_MS = 5 * 60 * 1000L // 5 minutes failsafe

        const val ACTION_START = "com.theblankstate.preamble.ALARM_START"
        const val ACTION_STOP = "com.theblankstate.preamble.ALARM_STOP"
        const val ACTION_COMPLETE = "com.theblankstate.preamble.ALARM_COMPLETE"
        const val ACTION_SNOOZE = "com.theblankstate.preamble.ALARM_SNOOZE"

        fun startAlarm(context: Context, taskId: String, taskTitle: String) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_START
                putExtra("task_id", taskId)
                putExtra("task_title", taskTitle)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AlarmRingtoneService", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getStringExtra("task_id")
        val taskTitle = intent?.getStringExtra("task_title") ?: "Task Reminder"

        Log.d(TAG, "Service onStartCommand: action=$action, taskId=$taskId, title=$taskTitle")

        when (action) {
            ACTION_START -> {
                if (taskId != null) {
                    // Verify task still exists and is not completed/deleted
                    verifyAndStartAlarm(taskId, taskTitle)
                }
            }
            ACTION_STOP -> {
                stopAlarm()
                stopSelf()
            }
            ACTION_COMPLETE -> {
                if (taskId != null) {
                    completeTask(taskId)
                }
                stopAlarm()
                stopSelf()
            }
            ACTION_SNOOZE -> {
                if (taskId != null) {
                    snoozeTask(taskId, taskTitle)
                }
                stopAlarm()
                stopSelf()
            }
            else -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Before ringing, verify the task still exists in DB and is not deleted/completed.
     * This prevents orphaned alarms from ringing for deleted tasks.
     */
    private fun verifyAndStartAlarm(taskId: String, taskTitle: String) {
        val app = applicationContext as PreambleApplication
        CoroutineScope(Dispatchers.IO).launch {
            val task = app.database.taskDao().getTaskById(taskId)
            if (task == null || task.isCompleted || task.isAlarmPaused) {
                Log.d(TAG, "Task '$taskTitle' no longer exists/active — skipping alarm")
                // Cancel any remaining alarms for this task
                TaskAlarmManager.cancelAllReminders(this@AlarmRingtoneService, taskId)
                stopSelf()
                return@launch
            }
            // Task exists and is active — ring the alarm on main thread
            android.os.Handler(mainLooper).post {
                startAlarmForeground(taskId, taskTitle)
            }
        }
    }

    private fun startAlarmForeground(taskId: String, taskTitle: String) {
        if (isAlarmActive) {
            // Stop previous alarm before starting new one
            stopAlarm()
        }

        Log.d(TAG, "Starting alarm for '$taskTitle'")
        isAlarmActive = true

        // Acquire wakelock to keep CPU awake during alarm
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "preamble:alarm_wakelock"
        ).apply {
            acquire(MAX_ALARM_DURATION_MS)
        }

        val notificationId = taskId.hashCode()

        // Create notification channel
        createNotificationChannel()

        // Build action PendingIntents
        val stopPi = buildActionPendingIntent(ACTION_STOP, taskId, taskTitle, notificationId + 1)
        val completePi = buildActionPendingIntent(ACTION_COMPLETE, taskId, taskTitle, notificationId + 2)
        val snoozePi = buildActionPendingIntent(ACTION_SNOOZE, taskId, taskTitle, notificationId + 3)

        // Full screen intent — opens app when tapped
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("task_id", taskId)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, notificationId + 4, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Task Reminder")
            .setContentText(taskTitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setDefaults(0) // No default sound — we play our own
            .setFullScreenIntent(fullScreenPi, true) // Shows over lock screen
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPi)
            .addAction(R.drawable.ic_launcher_foreground, "✓ Complete", completePi)
            .addAction(R.drawable.ic_launcher_foreground, "⏰ Snooze", snoozePi)
            .setVibrate(longArrayOf(0)) // Minimal vibrate to show heads-up
            .build()

        startForeground(notificationId, notification)

        // Play alarm ringtone
        playAlarmSound()

        // Start vibration
        startVibration()

        // Auto-stop after 5 minutes (failsafe)
        android.os.Handler(mainLooper).postDelayed({
            if (isAlarmActive) {
                Log.d(TAG, "Auto-stopping alarm after ${MAX_ALARM_DURATION_MS / 1000}s")
                stopAlarm()
                stopSelf()
            }
        }, MAX_ALARM_DURATION_MS)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for task deadlines"
                // Don't set channel sound — service plays its own audio
                setSound(null, null)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun playAlarmSound() {
        try {
            val prefs = getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            val savedToneUri = prefs.getString("alarm_tone_uri", null)
            val alarmUri = if (savedToneUri != null) {
                Uri.parse(savedToneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingtoneService, alarmUri)
                isLooping = true // Loop until user dismisses
                prepare()
                start()
            }
            Log.d(TAG, "Alarm sound started: $alarmUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 1000) // Vibrate pattern with pauses
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat from index 0
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "Stopping alarm")
        isAlarmActive = false

        // Stop audio
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) { }
        mediaPlayer = null

        // Stop vibration
        try {
            vibrator?.cancel()
        } catch (_: Exception) { }
        vibrator = null

        // Release wakelock
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) { }
        wakeLock = null
    }

    private fun completeTask(taskId: String) {
        Log.d(TAG, "Completing task $taskId")
        val app = applicationContext as PreambleApplication
        CoroutineScope(Dispatchers.IO).launch {
            val task = app.database.taskDao().getTaskById(taskId)
            if (task != null && !task.isCompleted) {
                app.repository.toggleTask(task)
                // Cancel all remaining reminders for this task
                TaskAlarmManager.cancelAllReminders(this@AlarmRingtoneService, taskId)
            }
        }
    }

    private fun snoozeTask(taskId: String, taskTitle: String) {
        val snoozeTimeMs = System.currentTimeMillis() + (10 * 60 * 1000L) // 10 minutes
        Log.d(TAG, "Snoozing task $taskId for 10 minutes")

        val app = applicationContext as PreambleApplication
        CoroutineScope(Dispatchers.IO).launch {
            val task = app.database.taskDao().getTaskById(taskId)
            if (task != null) {
                val updated = task.copy(
                    customAlarmTimeMs = snoozeTimeMs,
                    isAlarmPaused = false,
                    updatedTimestamp = System.currentTimeMillis()
                )
                app.repository.updateTask(updated)
                TaskAlarmManager.scheduleAlarm(this@AlarmRingtoneService, taskId, taskTitle, snoozeTimeMs)
            }
        }
    }

    private fun buildActionPendingIntent(action: String, taskId: String, taskTitle: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AlarmRingtoneService::class.java).apply {
            this.action = action
            putExtra("task_id", taskId)
            putExtra("task_title", taskTitle)
        }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
