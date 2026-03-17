package com.theblankstate.preamble

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.notification.NotificationKeepAliveScheduler
import com.theblankstate.preamble.notification.NotificationKeepAliveWorker
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.notification.TaskNotificationService
import com.theblankstate.preamble.repository.TaskRepository
import com.theblankstate.preamble.sync.FirebaseTaskSyncManager
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleTasksManager
import com.google.android.gms.ads.MobileAds
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.ads.RewardedAdManager
import com.theblankstate.preamble.ads.AppOpenAdManager
import com.theblankstate.preamble.recurrence.RecurrenceWorker
import com.theblankstate.preamble.sync.GoogleSyncWorker
import com.theblankstate.preamble.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PreambleApplication : Application() {

    /** Application-scoped coroutine scope — survives configuration changes and screen navigations. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { PreambleDatabase.getInstance(this) }
    val syncManager by lazy { FirebaseTaskSyncManager(this, database.taskDao()) }
    val repository by lazy { TaskRepository(database.taskDao(), syncManager) }

    override fun onCreate() {
        super.onCreate()
        FirebaseTaskSyncManager.enableOfflinePersistence()
        syncManager.start()
        TaskNotificationManager.createChannel(this)
        GoogleCalendarManager.init(this)
        GoogleTasksManager.init(this)
        MobileAds.initialize(this) {
            // SDK is ready — preload the app open ad so it's available when onResume fires
            AppOpenAdManager.preload()
        }
        FeatureGateManager.init(this)
        RewardedAdManager.init(this)
        AppOpenAdManager.init(this)
        // Start the persistent notification service only if user hasn't disabled it
        if (TaskNotificationService.isEnabled(this)) {
            TaskNotificationService.start(this)
        }
        // Schedule a periodic keep-alive worker so the OS can't permanently
        // kill the notification service in background
        scheduleNotificationKeepAlive()
        NotificationKeepAliveScheduler.schedule(this)
        // Schedule recurring task instance generation
        scheduleRecurrenceWorker()
        generateRecurrenceInstancesNow()
        // Schedule periodic Google sync (every 15 min)
        scheduleGoogleSyncWorker()
        // Observe task changes to refresh home screen widget
        observeTaskChangesForWidget()
    }

    private fun scheduleNotificationKeepAlive() {
        val request = PeriodicWorkRequestBuilder<NotificationKeepAliveWorker>(
            15, TimeUnit.MINUTES  // minimum WorkManager interval
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NotificationKeepAliveWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleRecurrenceWorker() {
        val request = PeriodicWorkRequestBuilder<RecurrenceWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecurrenceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleGoogleSyncWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<GoogleSyncWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GoogleSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun generateRecurrenceInstancesNow() {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<RecurrenceWorker>().build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun observeTaskChangesForWidget() {
        appScope.launch(Dispatchers.IO) {
            val today = com.theblankstate.preamble.repository.TaskRepository.todayString()
            repository.getTasksForDate(today).collect {
                WidgetUpdater.refresh(this@PreambleApplication)
            }
        }
    }
}
