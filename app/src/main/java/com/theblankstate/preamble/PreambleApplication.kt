package com.theblankstate.preamble

import android.app.Application
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.notification.TaskNotificationService
import com.theblankstate.preamble.repository.TaskRepository
import com.theblankstate.preamble.sync.FirebaseTaskSyncManager
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleTasksManager

class PreambleApplication : Application() {

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
        // Start the persistent notification service only if user hasn't disabled it
        if (TaskNotificationService.isEnabled(this)) {
            TaskNotificationService.start(this)
        }
    }
}
