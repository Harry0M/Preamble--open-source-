package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.gson.Gson
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.data.SyncMutation
import com.theblankstate.preamble.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class GoogleSyncQueueWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "GoogleSyncQueue"
        private const val WORK_NAME = "google_sync_queue_work"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<GoogleSyncQueueWorker>()
                .addTag("google_sync_queue")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP, // Do not restart/interrupt a running queue; just append/let it complete
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val db = app.database
        val dao = db.syncMutationDao()
        val taskDao = db.taskDao()

        val mutations = dao.getAllPending()
        if (mutations.isEmpty()) {
            Log.d(TAG, "No pending mutations in sync queue.")
            return Result.success()
        }

        Log.i(TAG, "Starting processing of ${mutations.size} pending mutations.")

        for (mutation in mutations) {
            Log.i(TAG, "[Processing] id=${mutation.id} task=${mutation.taskId} action=${mutation.actionType} provider=${mutation.provider}")
            
            // Mark task as syncing optimistically (except for delete where it's already gone)
            if (mutation.actionType != "DELETE") {
                val task = taskDao.getTaskById(mutation.taskId)
                if (task != null && !task.isSyncing) {
                    taskDao.updateTask(task.copy(isSyncing = true, syncFailed = false))
                }
            }

            try {
                when (mutation.actionType) {
                    "CREATE" -> {
                        val task = taskDao.getTaskById(mutation.taskId)
                        if (task == null) {
                            Log.w(TAG, "Task ${mutation.taskId} already deleted locally before creation sync. Skipping.")
                            dao.delete(mutation)
                            continue
                        }
                        if (mutation.provider == "GOOGLE_CALENDAR") {
                            if (!GoogleCalendarManager.isLinked.value) {
                                Log.w(TAG, "Google Calendar unlinked during creation. Skipping.")
                                taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                                dao.delete(mutation)
                                continue
                            }
                            val eventId = GoogleCalendarManager.createCalendarEvent(
                                applicationContext,
                                task.title,
                                task.createdDate,
                                task.deadlineTime,
                                "primary",
                                task.description,
                                task.recurrenceType,
                                task.recurrenceInterval ?: 1,
                                task.recurrenceDays,
                                task.recurrenceEndDate,
                                tags = task.tags
                            )
                            if (eventId != null) {
                                val finalTask = task.copy(
                                    id = "gcal_$eventId",
                                    source = "google_calendar",
                                    googleCalendarId = "primary",
                                    isSyncing = false,
                                    syncFailed = false,
                                    updatedTimestamp = System.currentTimeMillis()
                                )
                                taskDao.insertTask(finalTask)
                                taskDao.deleteTask(task)
                                if (!task.tags.isNullOrBlank()) {
                                    app.repository.saveTagOverride(finalTask.id, task.tags)
                                }
                                Log.d(TAG, "Google Calendar Event created: gcal_$eventId")
                                GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_calendar_create")
                            }
                        } else if (mutation.provider == "GOOGLE_TASKS") {
                            if (!GoogleTasksManager.isLinked.value) {
                                Log.w(TAG, "Google Tasks unlinked during creation. Skipping.")
                                taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                                dao.delete(mutation)
                                continue
                            }
                            val googleId = GoogleTasksManager.createGoogleTask(
                                applicationContext,
                                task.title,
                                task.createdDate
                            )
                            if (googleId != null) {
                                val finalTask = task.copy(
                                    id = "gtask_$googleId",
                                    source = "google_tasks",
                                    isSyncing = false,
                                    syncFailed = false,
                                    updatedTimestamp = System.currentTimeMillis()
                                )
                                taskDao.insertTask(finalTask)
                                taskDao.deleteTask(task)
                                if (!task.tags.isNullOrBlank()) {
                                    app.repository.saveTagOverride(finalTask.id, task.tags)
                                }
                                Log.d(TAG, "Google Task created: gtask_$googleId")
                                GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_task_create")
                            }
                        }
                    }
                    "TOGGLE_COMPLETE" -> {
                        val task = taskDao.getTaskById(mutation.taskId)
                        if (task == null) {
                            Log.w(TAG, "Task ${mutation.taskId} deleted locally before completion sync. Skipping.")
                            dao.delete(mutation)
                            continue
                        }
                        val completed = task.isCompleted
                        if (mutation.provider == "GOOGLE_CALENDAR") {
                            if (!GoogleCalendarManager.isLinked.value) {
                                taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                                dao.delete(mutation)
                                continue
                            }
                            val googleId = mutation.taskId.removePrefix("gcal_")
                            val calendarId = task.googleCalendarId ?: "primary"
                            GoogleCalendarManager.updateCalendarEventCompletion(applicationContext, googleId, calendarId, completed)
                            taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                            GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_calendar_complete")
                        } else if (mutation.provider == "GOOGLE_TASKS") {
                            if (!GoogleTasksManager.isLinked.value) {
                                taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                                dao.delete(mutation)
                                continue
                            }
                            val googleId = mutation.taskId.removePrefix("gtask_")
                            GoogleTasksManager.updateTaskCompletion(applicationContext, googleId, completed)
                            taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                            GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_task_complete")
                        }
                    }
                    "UPDATE" -> {
                        val task = taskDao.getTaskById(mutation.taskId)
                        if (task == null) {
                            Log.w(TAG, "Task ${mutation.taskId} deleted locally before update sync. Skipping.")
                            dao.delete(mutation)
                            continue
                        }
                        if (mutation.provider == "GOOGLE_CALENDAR") {
                            if (!GoogleCalendarManager.isLinked.value) {
                                taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                                dao.delete(mutation)
                                continue
                            }
                            val eventId = mutation.taskId.removePrefix("gcal_")
                            val calendarId = task.googleCalendarId ?: "primary"
                            GoogleCalendarManager.updateCalendarEvent(
                                applicationContext, eventId, calendarId, task.title, task.createdDate,
                                task.deadlineTime, task.description,
                                task.recurrenceType,
                                if (task.recurrenceType != null) task.recurrenceInterval ?: 1 else null,
                                if (task.recurrenceType != null) task.recurrenceDays else null,
                                if (task.recurrenceType != null) task.recurrenceEndDate else null,
                                tags = task.tags
                            )
                            taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                            GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_calendar_update")
                        } else if (mutation.provider == "GOOGLE_TASKS") {
                            if (!GoogleTasksManager.isLinked.value) {
                                taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                                dao.delete(mutation)
                                continue
                            }
                            val googleId = mutation.taskId.removePrefix("gtask_")
                            GoogleTasksManager.updateGoogleTask(applicationContext, googleId, task.title, task.createdDate)
                            taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                            GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_task_update")
                        }
                    }
                    "DELETE" -> {
                        val task = Gson().fromJson(mutation.payloadJson, Task::class.java)
                        if (mutation.provider == "GOOGLE_CALENDAR") {
                            if (!GoogleCalendarManager.isLinked.value) {
                                dao.delete(mutation)
                                continue
                            }
                            val eventId = task.id.removePrefix("gcal_")
                            val calendarId = task.googleCalendarId ?: "primary"
                            GoogleCalendarManager.deleteCalendarEvent(applicationContext, eventId, calendarId)
                            GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_calendar_delete")
                        } else if (mutation.provider == "GOOGLE_TASKS") {
                            if (!GoogleTasksManager.isLinked.value) {
                                dao.delete(mutation)
                                continue
                            }
                            val googleId = task.id.removePrefix("gtask_")
                            GoogleTasksManager.deleteGoogleTask(applicationContext, googleId)
                            GoogleSyncWorker.enqueueImmediate(applicationContext, forceFull = false, reason = "queue_task_delete")
                        }
                    }
                }

                // Success! Delete the mutation from the queue
                dao.delete(mutation)
                Log.d(TAG, "Mutation id=${mutation.id} completed successfully.")

            } catch (e: Throwable) {
                Log.e(TAG, "Error executing mutation id=${mutation.id}", e)
                
                var isTransient = true
                var isSoftSuccess = false
                var errorMsg = e.message ?: "Unknown error"

                if (e is GoogleJsonResponseException) {
                    val status = e.statusCode
                    if (status == 404 || status == 410) {
                        isSoftSuccess = true
                        isTransient = false
                        Log.i(TAG, "Resource not found (status $status). Treating as soft success.")
                    } else if (status == 401 || status == 403) {
                        isTransient = false
                        errorMsg = "Google Account permission / authentication failed ($status)."
                    } else {
                        // Other HTTP status codes (e.g. 400 Bad Request) are typically permanent bugs
                        isTransient = false
                    }
                } else if (e is IOException) {
                    isTransient = true
                } else {
                    isTransient = false
                }

                if (isSoftSuccess) {
                    // Soft success: clean up the mutation and set local state to synced
                    dao.delete(mutation)
                    if (mutation.actionType != "DELETE") {
                        val task = taskDao.getTaskById(mutation.taskId)
                        if (task != null) {
                            taskDao.updateTask(task.copy(isSyncing = false, syncFailed = false))
                        }
                    }
                } else if (isTransient) {
                    // Transient failure: retry with WorkManager backoff
                    val currentAttempt = mutation.attemptCount + 1
                    if (currentAttempt < 5) {
                        dao.update(mutation.copy(attemptCount = currentAttempt))
                        Log.w(TAG, "Transient error. Retrying later. Attempt $currentAttempt/5. Error: $errorMsg")
                        return Result.retry()
                    } else {
                        // Max retries exceeded -> treat as permanent error
                        Log.e(TAG, "Max retries exceeded for transient error. Failing mutation.")
                        handlePermanentFailure(app, mutation, errorMsg)
                    }
                } else {
                    // Permanent failure
                    Log.e(TAG, "Permanent failure. Aborting mutation.")
                    handlePermanentFailure(app, mutation, errorMsg)
                    
                    // Stop executing subsequent mutations since auth/link is broken
                    if (e is GoogleJsonResponseException && (e.statusCode == 401 || e.statusCode == 403)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Sync stopped: $errorMsg Please check your Google account link.", Toast.LENGTH_LONG).show()
                        }
                        return Result.failure()
                    }
                }
            }
        }

        Log.i(TAG, "Successfully processed all sync queue mutations.")
        return Result.success()
    }

    private suspend fun handlePermanentFailure(app: PreambleApplication, mutation: SyncMutation, errorMsg: String) {
        val db = app.database
        val dao = db.syncMutationDao()
        val taskDao = db.taskDao()

        // 1. Delete mutation so it doesn't block the queue
        dao.delete(mutation)

        // 2. If it was a deletion mutation, perform a rollback to bring the task back
        if (mutation.actionType == "DELETE") {
            try {
                val task = Gson().fromJson(mutation.payloadJson, Task::class.java)
                val restoredTask = task.copy(isSyncing = false, syncFailed = true)
                taskDao.insertTask(restoredTask)
                Log.i(TAG, "Rollback: Restored deleted task '${task.title}' locally with syncFailed flag.")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Failed to delete task remotely. Task '${task.title}' restored.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse rollback task JSON", e)
            }
        } else {
            // Set syncFailed = true on the existing task
            val task = taskDao.getTaskById(mutation.taskId)
            if (task != null) {
                taskDao.updateTask(task.copy(isSyncing = false, syncFailed = true))
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Failed to sync task: '${task.title}'.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
