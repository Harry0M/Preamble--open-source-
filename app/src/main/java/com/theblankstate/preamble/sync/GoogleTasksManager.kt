package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.theblankstate.preamble.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Google Tasks integration:
 * - Uses the same Google Sign-In as GoogleCalendarManager (shared scopes)
 * - Fetches tasks from all user's task lists
 * - Converts Google Tasks to app Task model
 */
object GoogleTasksManager {

    private const val TAG = "GoogleTasksManager"
    private const val PREFS_NAME = "google_tasks_prefs"
    private const val KEY_LINKED = "tasks_linked"
    private const val KEY_LAST_SYNC = "last_tasks_sync_timestamp"

    private val _isLinked = MutableStateFlow(false)
    val isLinked: StateFlow<Boolean> = _isLinked.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long>(0)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isLinked.value = prefs.getBoolean(KEY_LINKED, false)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC, 0)
    }

    /**
     * Mark as linked (called when Google sign-in with Tasks scope succeeds).
     */
    fun onSignInSuccess(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LINKED, true).apply()
        _isLinked.value = true
        Log.d(TAG, "Google Tasks linked")
    }

    /**
     * Unlink Google Tasks.
     */
    fun unlink(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LINKED, false)
            .remove(KEY_LAST_SYNC)
            .apply()
        _isLinked.value = false
        _lastSyncTime.value = 0
        Log.d(TAG, "Google Tasks unlinked")
    }

    /**
     * Fetch all Google Tasks from all task lists and return as app Tasks.
     */
    suspend fun fetchGoogleTasks(context: Context): List<Task> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.e(TAG, "No signed-in account found")
            return@withContext emptyList()
        }

        try {
            _isSyncing.value = true

            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(TasksScopes.TASKS_READONLY)
            )
            credential.selectedAccount = account.account

            val tasksService = Tasks.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("Preamble")
                .build()

            val allTasks = mutableListOf<Task>()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeSdf = SimpleDateFormat("HH:mm", Locale.US)

            // Get all task lists
            val taskListsResult = tasksService.tasklists().list()
                .setMaxResults(100)
                .execute()
            val taskLists = taskListsResult.items ?: emptyList()

            Log.d(TAG, "Found ${taskLists.size} task lists")

            for (taskList in taskLists) {
                try {
                    var pageToken: String? = null
                    do {
                        val tasksRequest = tasksService.tasks().list(taskList.id)
                            .setMaxResults(100)
                            .setShowCompleted(true)
                            .setShowHidden(true)
                            .setPageToken(pageToken)

                        val tasksResult = tasksRequest.execute()
                        val googleTasks = tasksResult.items ?: emptyList()

                        for (gTask in googleTasks) {
                            val task = googleTaskToAppTask(gTask, taskList.title, sdf, timeSdf)
                            if (task != null) {
                                allTasks.add(task)
                            }
                        }

                        pageToken = tasksResult.nextPageToken
                    } while (pageToken != null)

                    Log.d(TAG, "Fetched tasks from list: ${taskList.title}")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error fetching tasks from list ${taskList.title}", e)
                }
            }

            // Update last sync time
            val syncTime = System.currentTimeMillis()
            _lastSyncTime.value = syncTime
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, syncTime)
                .apply()

            Log.d(TAG, "Total Google Tasks fetched: ${allTasks.size}")
            allTasks
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching Google Tasks", e)
            emptyList()
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Convert a Google Task to our app Task model.
     *
     * Google Tasks API notes:
     * - `due` is date-only (time portion is always midnight UTC, no time support)
     * - `status` is "completed" or "needsAction"
     * - `completed` is RFC 3339 timestamp when completed
     */
    private fun googleTaskToAppTask(
        gTask: com.google.api.services.tasks.model.Task,
        listName: String,
        sdf: SimpleDateFormat,
        timeSdf: SimpleDateFormat
    ): Task? {
        val title = gTask.title
        if (title.isNullOrBlank()) return null

        val updatedAt = parseRfc3339Millis(gTask.updated) ?: System.currentTimeMillis()
        val isCompleted = gTask.status == "completed"
        val completedAt = if (isCompleted) {
            parseRfc3339Millis(gTask.completed) ?: updatedAt
        } else null

        // Determine date — Google Tasks due is date-only (no time support)
        // Parse with UTC timezone to avoid date shifting
        val taskDate: String = if (gTask.due != null) {
            try {
                // Extract just the date part to avoid timezone shifting
                // Google sends "2026-03-10T00:00:00.000Z" — we want "2026-03-10"
                val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val dueMillis = parseRfc3339Millis(gTask.due) ?: updatedAt
                utcSdf.format(Date(dueMillis))
            } catch (_: Exception) {
                sdf.format(Date())
            }
        } else {
            // No due date — show on today's list
            sdf.format(Date())
        }

        return Task(
            id = "gtask_${gTask.id}",
            title = "✅ $title",
            isCompleted = isCompleted,
            createdDate = taskDate,
            createdTimestamp = updatedAt,
            completedTimestamp = completedAt,
            deadlineTime = null, // Google Tasks has no time support
            updatedTimestamp = updatedAt,
            source = "google_tasks"
        )
    }

    private fun parseRfc3339Millis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            DateTime.parseRfc3339(value).value
        } catch (_: Throwable) {
            null
        }
    }
}
