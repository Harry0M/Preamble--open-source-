package com.theblankstate.preamble.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val TaskIdKey = ActionParameters.Key<String>("task_id")

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        val app = context.applicationContext as PreambleApplication
        val dao = app.database.taskDao()

        withContext(Dispatchers.IO) {
            val allTasks = dao.getAllTasks()
            val task = allTasks.find { it.id == taskId } ?: return@withContext
            val updated = task.copy(
                isCompleted = true,
                completedTimestamp = System.currentTimeMillis(),
                updatedTimestamp = System.currentTimeMillis()
            )
            dao.updateTask(updated)
        }

        // Immediately update THIS specific widget instance (faster than updateAll)
        TaskListWidget().update(context, glanceId)
    }
}
