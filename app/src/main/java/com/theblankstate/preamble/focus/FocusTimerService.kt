package com.theblankstate.preamble.focus

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
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.R
import com.theblankstate.preamble.analytics.AnalyticsManager
import com.theblankstate.preamble.data.FocusSession
import com.theblankstate.preamble.data.FocusSessionDao
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

class FocusTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var focusSessionDao: FocusSessionDao? = null
    private var workPhaseStartTimestamp: Long = 0L

    private var timerRepository: com.theblankstate.preamble.repository.TimerSessionRepository? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        focusSessionDao = PreambleDatabase.getInstance(applicationContext).focusSessionDao()
        timerRepository = (applicationContext as? PreambleApplication)?.timerSessionRepository
            ?: com.theblankstate.preamble.repository.TimerSessionRepository(applicationContext, focusSessionDao!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)
                val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, FocusTimerDefaults.WORK_MINUTES * 60)
                startTimer(durationSeconds, taskId, taskTitle)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
            ACTION_FINISH -> finishTimer()
            ACTION_SKIP -> skipToNext()
            ACTION_ADD_TIME -> {
                val extraSeconds = intent.getIntExtra(EXTRA_ADD_SECONDS, 300)
                addTime(extraSeconds)
            }
        }
        return START_STICKY
    }

    private fun addTime(secondsToAdd: Int) {
        val current = _state.value
        if (current.isRunning) {
            val newRemaining = current.remainingSeconds + secondsToAdd
            val newTotal = current.totalSeconds + secondsToAdd
            val newSelected = current.durationSelectedSeconds + secondsToAdd
            _state.value = current.copy(
                remainingSeconds = newRemaining,
                totalSeconds = newTotal,
                durationSelectedSeconds = newSelected
            )
            updateNotification()
        }
    }

    private fun startTimer(durationSeconds: Int, taskId: String?, taskTitle: String?) {
        workPhaseStartTimestamp = System.currentTimeMillis()
        _state.value = FocusTimerState(
            isRunning = true,
            isPaused = false,
            remainingSeconds = durationSeconds,
            totalSeconds = durationSeconds,
            durationSelectedSeconds = durationSeconds,
            taskId = taskId,
            taskTitle = taskTitle,
            currentPhase = FocusPhase.WORK,
            completionStatus = "IN_PROGRESS"
        )
        promoteToForeground()
        startCountdown()

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
            FocusPhase.WORK -> {
                val now = System.currentTimeMillis()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val session = FocusSession(
                    taskId = current.taskId,
                    taskTitle = current.taskTitle,
                    startTimestamp = workPhaseStartTimestamp,
                    endTimestamp = now,
                    durationSeconds = current.durationSelectedSeconds,
                    durationSelectedSeconds = current.durationSelectedSeconds,
                    actualDurationCompletedSeconds = current.durationSelectedSeconds,
                    completionStatus = "COMPLETED",
                    createdTimestamp = workPhaseStartTimestamp,
                    date = sdf.format(java.util.Date(now))
                )
                serviceScope.launch(Dispatchers.IO) {
                    try { timerRepository?.saveSession(session) } catch (_: Exception) {}
                }

                AnalyticsManager.trackFocusMode(
                    action = "finished",
                    durationSeconds = current.durationSelectedSeconds,
                    taskId = current.taskId
                )

                val newSessions = current.sessionsCompleted + 1
                _state.value = current.copy(
                    isRunning = false,
                    isPaused = false,
                    remainingSeconds = 0,
                    sessionsCompleted = newSessions,
                    completionStatus = "COMPLETED"
                )
                timerJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> {
                workPhaseStartTimestamp = System.currentTimeMillis()
                val workDuration = current.durationSelectedSeconds
                _state.value = current.copy(
                    currentPhase = FocusPhase.WORK,
                    remainingSeconds = workDuration,
                    totalSeconds = workDuration
                )
                startCountdown()
            }
        }
        updateNotification()
    }

    private fun finishTimer() {
        val current = _state.value
        val elapsed = current.totalSeconds - current.remainingSeconds
        val actualDuration = if (elapsed > 0) elapsed else current.durationSelectedSeconds
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val session = FocusSession(
            taskId = current.taskId,
            taskTitle = current.taskTitle,
            startTimestamp = workPhaseStartTimestamp,
            endTimestamp = now,
            durationSeconds = current.durationSelectedSeconds,
            durationSelectedSeconds = current.durationSelectedSeconds,
            actualDurationCompletedSeconds = actualDuration,
            completionStatus = "COMPLETED",
            createdTimestamp = workPhaseStartTimestamp,
            date = sdf.format(java.util.Date(now))
        )
        serviceScope.launch(Dispatchers.IO) {
            try { timerRepository?.saveSession(session) } catch (_: Exception) {}
        }

        AnalyticsManager.trackFocusMode(
            action = "finished_early",
            durationSeconds = actualDuration,
            taskId = current.taskId
        )

        timerJob?.cancel()
        _state.value = FocusTimerState(completionStatus = "COMPLETED")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseTimer() {
        _state.value = _state.value.copy(isPaused = true)
        updateNotification()

        AnalyticsManager.trackFocusMode(
            action = "paused",
            taskId = _state.value.taskId
        )
    }

    private fun resumeTimer() {
        _state.value = _state.value.copy(isPaused = false)
        updateNotification()

        AnalyticsManager.trackFocusMode(
            action = "resumed",
            taskId = _state.value.taskId
        )
    }

    private fun stopTimer() {
        val current = _state.value
        val elapsed = current.totalSeconds - current.remainingSeconds

        // Save session if elapsed time is at least 10 seconds
        if (current.currentPhase == FocusPhase.WORK && elapsed >= 10) {
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val session = FocusSession(
                taskId = current.taskId,
                taskTitle = current.taskTitle,
                startTimestamp = workPhaseStartTimestamp,
                endTimestamp = now,
                durationSeconds = current.durationSelectedSeconds,
                durationSelectedSeconds = current.durationSelectedSeconds,
                actualDurationCompletedSeconds = elapsed,
                completionStatus = "STOPPED",
                createdTimestamp = workPhaseStartTimestamp,
                date = sdf.format(java.util.Date(now))
            )
            serviceScope.launch(Dispatchers.IO) {
                try { timerRepository?.saveSession(session) } catch (_: Exception) {}
            }
        }

        AnalyticsManager.trackFocusMode(
            action = "cancelled",
            durationSeconds = elapsed,
            taskId = current.taskId
        )

        timerJob?.cancel()
        _state.value = FocusTimerState(completionStatus = "STOPPED")
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

        val title = if (state.taskTitle != null) "Focus: ${state.taskTitle}" else "Task Timer"
        val contentText = "$timeText" + if (state.isPaused) " (Paused)" else ""

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)

        if (state.isPaused) {
            val resumeIntent = PendingIntent.getBroadcast(
                this, 1,
                Intent(this, FocusTimerReceiver::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notif_play, "Resume", resumeIntent)
        } else {
            val pauseIntent = PendingIntent.getBroadcast(
                this, 2,
                Intent(this, FocusTimerReceiver::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notif_pause, "Pause", pauseIntent)
        }

        val stopIntent = PendingIntent.getBroadcast(
            this, 3,
            Intent(this, FocusTimerReceiver::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(R.drawable.ic_notif_stop, "Stop", stopIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Task timer notifications"
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
        private const val CHANNEL_ID = "preamble_focus_timer"
        const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.theblankstate.preamble.FOCUS_START"
        const val ACTION_PAUSE = "com.theblankstate.preamble.FOCUS_PAUSE"
        const val ACTION_RESUME = "com.theblankstate.preamble.FOCUS_RESUME"
        const val ACTION_STOP = "com.theblankstate.preamble.FOCUS_STOP"
        const val ACTION_FINISH = "com.theblankstate.preamble.FOCUS_FINISH"
        const val ACTION_SKIP = "com.theblankstate.preamble.FOCUS_SKIP"
        const val ACTION_ADD_TIME = "com.theblankstate.preamble.FOCUS_ADD_TIME"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_ADD_SECONDS = "add_seconds"

        private val _state = MutableStateFlow(FocusTimerState())
        val state: StateFlow<FocusTimerState> = _state.asStateFlow()

        fun start(context: Context, taskId: String? = null, taskTitle: String? = null, durationSeconds: Int = 1500) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            context.startService(Intent(context, FocusTimerService::class.java).apply { action = ACTION_PAUSE })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, FocusTimerService::class.java).apply { action = ACTION_RESUME })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FocusTimerService::class.java).apply { action = ACTION_STOP })
        }

        fun finish(context: Context) {
            context.startService(Intent(context, FocusTimerService::class.java).apply { action = ACTION_FINISH })
        }

        fun skip(context: Context) {
            context.startService(Intent(context, FocusTimerService::class.java).apply { action = ACTION_SKIP })
        }

        fun addTime(context: Context, extraSeconds: Int) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_ADD_TIME
                putExtra(EXTRA_ADD_SECONDS, extraSeconds)
            }
            context.startService(intent)
        }
    }
}
