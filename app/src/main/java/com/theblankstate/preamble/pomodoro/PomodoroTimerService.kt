package com.theblankstate.preamble.pomodoro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.R
import com.theblankstate.preamble.analytics.AnalyticsManager
import com.theblankstate.preamble.data.PomodoroSession
import com.theblankstate.preamble.data.PomodoroSessionDao
import com.theblankstate.preamble.data.PreambleDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PomodoroTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var pomodoroSessionDao: PomodoroSessionDao? = null
    private var workPhaseStartTimestamp: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        pomodoroSessionDao = PreambleDatabase.getInstance(applicationContext).pomodoroSessionDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)
                startTimer(PomodoroDefaults.WORK_MINUTES * 60, taskId, taskTitle)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
            ACTION_SKIP -> skipToNext()
        }
        return START_STICKY
    }

    private fun startTimer(durationSeconds: Int, taskId: String?, taskTitle: String?) {
        workPhaseStartTimestamp = System.currentTimeMillis()
        _state.value = PomodoroState(
            isRunning = true,
            remainingSeconds = durationSeconds,
            totalSeconds = durationSeconds,
            taskId = taskId,
            taskTitle = taskTitle,
            currentPhase = PomodoroPhase.WORK
        )
        promoteToForeground()
        startCountdown()

        // PostHog: Focus mode shuru hua — track karo
        AnalyticsManager.trackFocusMode(
            action = "started",
            taskId = taskId
        )
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_state.value.remainingSeconds > 0 && _state.value.isRunning) {
                delay(1000)
                if (!_state.value.isPaused) {
                    _state.value = _state.value.copy(
                        remainingSeconds = _state.value.remainingSeconds - 1
                    )
                    updateNotification()
                }
            }
            if (_state.value.remainingSeconds <= 0 && _state.value.isRunning) {
                onPhaseComplete()
            }
        }
    }

    private fun onPhaseComplete() {
        val current = _state.value
        when (current.currentPhase) {
            PomodoroPhase.WORK -> {
                // Persist completed work session to DB
                val now = System.currentTimeMillis()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val session = PomodoroSession(
                    taskId = current.taskId,
                    taskTitle = current.taskTitle,
                    startTimestamp = workPhaseStartTimestamp,
                    endTimestamp = now,
                    durationSeconds = PomodoroDefaults.WORK_MINUTES * 60,
                    date = sdf.format(java.util.Date(now))
                )
                serviceScope.launch(Dispatchers.IO) {
                    try { pomodoroSessionDao?.insertSession(session) } catch (_: Exception) {}
                }

                // PostHog: Work phase poori hui — duration track karo
                AnalyticsManager.trackFocusMode(
                    action = "finished",
                    durationSeconds = PomodoroDefaults.WORK_MINUTES * 60,
                    taskId = current.taskId
                )

                val newSessions = current.sessionsCompleted + 1
                val nextPhase = if (newSessions % PomodoroDefaults.SESSIONS_BEFORE_LONG_BREAK == 0) {
                    PomodoroPhase.LONG_BREAK
                } else {
                    PomodoroPhase.SHORT_BREAK
                }
                val breakDuration = if (nextPhase == PomodoroPhase.LONG_BREAK) {
                    PomodoroDefaults.LONG_BREAK_MINUTES * 60
                } else {
                    PomodoroDefaults.SHORT_BREAK_MINUTES * 60
                }
                _state.value = current.copy(
                    currentPhase = nextPhase,
                    remainingSeconds = breakDuration,
                    totalSeconds = breakDuration,
                    sessionsCompleted = newSessions
                )
                startCountdown()
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> {
                workPhaseStartTimestamp = System.currentTimeMillis()
                val workDuration = PomodoroDefaults.WORK_MINUTES * 60
                _state.value = current.copy(
                    currentPhase = PomodoroPhase.WORK,
                    remainingSeconds = workDuration,
                    totalSeconds = workDuration
                )
                startCountdown()
            }
        }
        updateNotification()
    }

    private fun pauseTimer() {
        _state.value = _state.value.copy(isPaused = true)
        updateNotification()

        // PostHog: Focus pause hua
        AnalyticsManager.trackFocusMode(
            action = "paused",
            taskId = _state.value.taskId
        )
    }

    private fun resumeTimer() {
        _state.value = _state.value.copy(isPaused = false)
        updateNotification()

        // PostHog: Focus resume hua
        AnalyticsManager.trackFocusMode(
            action = "resumed",
            taskId = _state.value.taskId
        )
    }

    private fun stopTimer() {
        val current = _state.value
        val elapsed = current.totalSeconds - current.remainingSeconds

        // Save partial work session if stopped mid-WORK with any meaningful time elapsed
        if (current.currentPhase == PomodoroPhase.WORK && elapsed >= 120) {
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val session = PomodoroSession(
                taskId = current.taskId,
                taskTitle = current.taskTitle,
                startTimestamp = workPhaseStartTimestamp,
                endTimestamp = now,
                durationSeconds = elapsed,
                date = sdf.format(java.util.Date(now))
            )
            serviceScope.launch(Dispatchers.IO) {
                try { pomodoroSessionDao?.insertSession(session) } catch (_: Exception) {}
            }
        }

        AnalyticsManager.trackFocusMode(
            action = "cancelled",
            durationSeconds = elapsed,
            taskId = current.taskId
        )

        timerJob?.cancel()
        _state.value = PomodoroState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun skipToNext() {
        timerJob?.cancel()
        _state.value = _state.value.copy(remainingSeconds = 0)
        onPhaseComplete()
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val state = _state.value
        val minutes = state.remainingSeconds / 60
        val seconds = state.remainingSeconds % 60
        val timeText = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

        val phaseText = when (state.currentPhase) {
            PomodoroPhase.WORK -> "Work"
            PomodoroPhase.SHORT_BREAK -> "Short Break"
            PomodoroPhase.LONG_BREAK -> "Long Break"
        }

        val title = if (state.taskTitle != null) "Focus: ${state.taskTitle}" else "Pomodoro"
        val contentText = "$phaseText — $timeText" + if (state.isPaused) " (Paused)" else ""

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)

        // Pause/Resume action
        if (state.isPaused) {
            val resumeIntent = PendingIntent.getBroadcast(
                this, 1,
                Intent(this, PomodoroReceiver::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Resume", resumeIntent)
        } else {
            val pauseIntent = PendingIntent.getBroadcast(
                this, 2,
                Intent(this, PomodoroReceiver::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Pause", pauseIntent)
        }

        // Stop action
        val stopIntent = PendingIntent.getBroadcast(
            this, 3,
            Intent(this, PomodoroReceiver::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Stop", stopIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Focus timer notifications"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "preamble_pomodoro"
        const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.theblankstate.preamble.POMODORO_START"
        const val ACTION_PAUSE = "com.theblankstate.preamble.POMODORO_PAUSE"
        const val ACTION_RESUME = "com.theblankstate.preamble.POMODORO_RESUME"
        const val ACTION_STOP = "com.theblankstate.preamble.POMODORO_STOP"
        const val ACTION_SKIP = "com.theblankstate.preamble.POMODORO_SKIP"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"

        private val _state = MutableStateFlow(PomodoroState())
        val state: StateFlow<PomodoroState> = _state.asStateFlow()

        fun start(context: Context, taskId: String? = null, taskTitle: String? = null) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            context.startService(Intent(context, PomodoroTimerService::class.java).apply { action = ACTION_PAUSE })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, PomodoroTimerService::class.java).apply { action = ACTION_RESUME })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PomodoroTimerService::class.java).apply { action = ACTION_STOP })
        }

        fun skip(context: Context) {
            context.startService(Intent(context, PomodoroTimerService::class.java).apply { action = ACTION_SKIP })
        }
    }
}
