package com.theblankstate.preamble

import android.app.Application
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.repository.TaskRepository

class PreambleApplication : Application() {

    val database by lazy { PreambleDatabase.getInstance(this) }
    val repository by lazy { TaskRepository(database.taskDao()) }

    override fun onCreate() {
        super.onCreate()
        TaskNotificationManager.createChannel(this)
    }
}
