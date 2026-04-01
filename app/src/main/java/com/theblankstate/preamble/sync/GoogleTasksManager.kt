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
import com.google.gson.Gson
import com.theblankstate.preamble.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Manages Google Tasks integration:
 * - Uses the same Google Sign-In as GoogleCalendarManager (shared scopes)
 * - Fetches tasks from all user's task lists
 * - Creates, updates and syncs tasks (read-write)
 * - Converts Google Tasks to app Task model
 */
object GoogleTasksManager {

    private const val TAG = "GoogleTasksManager"
    private const val PREFS_NAME = "google_tasks_prefs"
    private const val KEY_LINKED = "tasks_linked"
    private const val KEY_LAST_SYNC = "last_tasks_sync_timestamp"
    private const val KEY_SYNC_ANCHOR = "last_tasks_sync_anchor_timestamp"
    private const val KEY_SYNC_VOICE = "sync_voice_to_google_tasks"
    private const val KEY_AUTO_DELETE = "auto_delete_google_tasks"
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 1000L
    private const val INCREMENTAL_OVERLAP_MS = 5_000L

    private val _isLinked = MutableStateFlow(false)
    val isLinked: StateFlow<Boolean> = _isLinked.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long>(0)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _syncVoiceTasks = MutableStateFlow(false)
    val syncVoiceTasks: StateFlow<Boolean> = _syncVoiceTasks.asStateFlow()

    private val _autoDeleteGoogleTasks = MutableStateFlow(false)
    val autoDeleteGoogleTasks: StateFlow<Boolean> = _autoDeleteGoogleTasks.asStateFlow()

    @Volatile
    private var lastSyncAnchorMs: Long = 0L

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isLinked.value = prefs.getBoolean(KEY_LINKED, false)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC, 0)
        lastSyncAnchorMs = prefs.getLong(KEY_SYNC_ANCHOR, 0)
        _syncVoiceTasks.value = prefs.getBoolean(KEY_SYNC_VOICE, false)
        _autoDeleteGoogleTasks.value = prefs.getBoolean(KEY_AUTO_DELETE, false)
    }

    fun setSyncVoiceTasks(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYNC_VOICE, enabled).apply()
        _syncVoiceTasks.value = enabled
    }

    fun setAutoDeleteGoogleTasks(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_DELETE, enabled).apply()
        _autoDeleteGoogleTasks.value = enabled
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
            .remove(KEY_SYNC_ANCHOR)
            .apply()
        _isLinked.value = false
        _lastSyncTime.value = 0
        lastSyncAnchorMs = 0
        Log.d(TAG, "Google Tasks unlinked")
    }

    fun resetSyncState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_SYNC)
            .remove(KEY_SYNC_ANCHOR)
            .apply()
        _lastSyncTime.value = 0
        lastSyncAnchorMs = 0
        Log.d(TAG, "Google Tasks sync state reset")
    }

    fun incrementalUpdatedAfterRfc(): String? {
        val anchor = when {
            lastSyncAnchorMs > 0L -> lastSyncAnchorMs
            _lastSyncTime.value > 0L -> (_lastSyncTime.value - INCREMENTAL_OVERLAP_MS).coerceAtLeast(0L)
            else -> return null
        }
        return DateTime(anchor).toStringRfc3339()
    }

    /**
     * Fetch all Google Tasks from all task lists and return as app Tasks.
     * @param updatedAfter If non-null, only fetch tasks updated after this RFC 3339 timestamp (for quick sync)
     */
    suspend fun fetchGoogleTasks(
        context: Context,
        updatedAfter: String? = null,
        onProgressUpdate: suspend (List<Task>) -> Unit = {}
    ): List<Task> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.e(TAG, "No signed-in account found")
            return@withContext emptyList()
        }

        if (_isSyncing.value) {
            Log.d(TAG, "Tasks sync already in progress, skipping concurrent request")
            return@withContext emptyList()
        }

        try {
            _isSyncing.value = true
            val fetchStartedAt = System.currentTimeMillis()

            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(TasksScopes.TASKS)
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
                val thisListTasks = mutableListOf<Task>()
                try {
                    var pageToken: String? = null
                    do {
                        val tasksRequest = tasksService.tasks().list(taskList.id)
                            .setMaxResults(100)
                            .setShowCompleted(true)
                            .setShowHidden(true)
                            .setShowDeleted(true)
                            .setPageToken(pageToken)

                        // For quick sync, only fetch tasks updated since last sync
                        if (updatedAfter != null) {
                            tasksRequest.updatedMin = updatedAfter
                        }

                        val tasksResult = tasksRequest.execute()
                        val googleTasks = tasksResult.items ?: emptyList()
                        val pageTasks = mutableListOf<Task>()

                        for (gTask in googleTasks) {
                            val task = googleTaskToAppTask(gTask, taskList.title, sdf, timeSdf)
                            if (task != null) {
                                allTasks.add(task)
                                thisListTasks.add(task)
                                pageTasks.add(task)
                            }
                        }

                        if (pageTasks.isNotEmpty()) {
                            try {
                                onProgressUpdate(pageTasks)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in progressive sync update", e)
                            }
                        }

                        pageToken = tasksResult.nextPageToken
                    } while (pageToken != null)

                    Log.d(TAG, "Fetched tasks from list: ${taskList.title}")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error fetching tasks from list ${taskList.title}", e)
                    throw e // Crucial: Do not swallow! Swallowing causes TaskRepository to delete all tasks in this list!
                }
            }

            // Update last sync time
            val syncTime = System.currentTimeMillis()
            val nextAnchor = (fetchStartedAt - INCREMENTAL_OVERLAP_MS).coerceAtLeast(0L)
            _lastSyncTime.value = syncTime
            lastSyncAnchorMs = nextAnchor
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, syncTime)
                .putLong(KEY_SYNC_ANCHOR, nextAnchor)
                .apply()

            Log.d(TAG, "Total Google Tasks fetched: ${allTasks.size}")
            allTasks
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching Google Tasks", e)
            throw e // Throw to abort sync, rather than returning emptyList() which causes full task deletion!
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
        val localSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
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
                localSdf.format(Date())
            }
        } else if (isCompleted && completedAt != null) {
            // No due date but completed — show on the date it was completed
            localSdf.format(Date(completedAt))
        } else {
            // No due date, not completed — show on today's list
            localSdf.format(Date())
        }

        return Task(
            id = "gtask_${gTask.id}",
            title = title,
            isCompleted = isCompleted,
            createdDate = taskDate,
            createdTimestamp = updatedAt,
            completedTimestamp = completedAt,
            deadlineTime = null, // Google Tasks has no time support
            updatedTimestamp = updatedAt,
            source = "google_tasks",
            deletedFromGoogle = gTask.deleted ?: false,
            tags = null, // Tags applied from local tag override store during sync
            // CRITICAL: Extract notes field as description
            description = gTask.notes,
            // Extract web view link if available
            webViewLink = gTask.webViewLink,
            // Extract links array as JSON
            taskLinksJson = gTask.links?.takeIf { it.isNotEmpty() }?.map { link ->
                mapOf(
                    "type" to (link.type ?: ""),
                    "description" to (link.description ?: ""),
                    "link" to (link.link ?: "")
                )
            }?.let { Gson().toJson(it) }
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

    // ── Write Operations ──

    /**
     * Retry a suspend block with exponential backoff for transient failures.
     */
    private suspend fun <T> retryOnFailure(block: suspend () -> T): T {
        var lastException: Throwable? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Attempt $attempt failed, retrying...", e)
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastException!!
    }

    /**
     * Build a Tasks service with read-write scope.
     */
    private fun buildTasksService(context: Context): Tasks? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(TasksScopes.TASKS)
        )
        credential.selectedAccount = account.account
        return Tasks.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Preamble").build()
    }

    /**
     * Get the user's default (first) task list ID.
     */
    private fun getDefaultTaskListId(service: Tasks): String? {
        return try {
            service.tasklists().list().setMaxResults(1).execute().items?.firstOrNull()?.id
        } catch (_: Throwable) { null }
    }

    /**
     * Create a new task in Google Tasks.
     * @param title Task title
     * @param date Due date in "yyyy-MM-dd" format (optional)
     * @return The Google Task ID (without "gtask_" prefix), or null on failure
     */
    suspend fun createGoogleTask(
        context: Context,
        title: String,
        date: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext null
        try {
            retryOnFailure {
                val service = buildTasksService(context) ?: error("No Tasks service")
                val listId = getDefaultTaskListId(service) ?: error("No task list")

                val gTask = com.google.api.services.tasks.model.Task()
                    .setTitle(title)
                    .setStatus("needsAction")

                if (date != null) {
                    val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val parsed = utcSdf.parse(date)
                    if (parsed != null) {
                        gTask.due = DateTime(parsed, TimeZone.getTimeZone("UTC")).toStringRfc3339()
                    }
                }

                val created = service.tasks().insert(listId, gTask).execute()
                Log.d(TAG, "Created Google Task: ${created.id}")
                created.id
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create Google Task after retries", e)
            null
        }
    }

    /**
     * Update completion status of a Google Task.
     * @param googleTaskId The raw Google Task ID (without "gtask_" prefix)
     * @param completed Whether the task is completed
     */
    /**
     * Delete a Google Task by its raw Google Task ID.
     * @param googleTaskId The raw Google Task ID (without "gtask_" prefix)
     */
    suspend fun deleteGoogleTask(
        context: Context,
        googleTaskId: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext false
        try {
            retryOnFailure {
                val service = buildTasksService(context) ?: error("No Tasks service")
                val listId = getDefaultTaskListId(service) ?: error("No task list")
                service.tasks().delete(listId, googleTaskId).execute()
                Log.d(TAG, "Deleted Google Task $googleTaskId")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete Google Task $googleTaskId after retries", e)
            false
        }
    }

    /**
     * Update a Google Task's title and due date.
     * @param googleTaskId The raw Google Task ID (without "gtask_" prefix)
     * @param title New title
     * @param date Due date in "yyyy-MM-dd" format (optional)
     */
    suspend fun updateGoogleTask(
        context: Context,
        googleTaskId: String,
        title: String,
        date: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext false
        try {
            retryOnFailure {
                val service = buildTasksService(context) ?: error("No Tasks service")
                val listId = getDefaultTaskListId(service) ?: error("No task list")

                val gTask = service.tasks().get(listId, googleTaskId).execute()
                gTask.title = title
                if (date != null) {
                    val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val parsed = utcSdf.parse(date)
                    if (parsed != null) {
                        gTask.due = DateTime(parsed, TimeZone.getTimeZone("UTC")).toStringRfc3339()
                    }
                }
                service.tasks().update(listId, googleTaskId, gTask).execute()
                Log.d(TAG, "Updated Google Task $googleTaskId → title=$title, date=$date")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update Google Task $googleTaskId after retries", e)
            false
        }
    }

    suspend fun updateTaskCompletion(
        context: Context,
        googleTaskId: String,
        completed: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext false
        try {
            retryOnFailure {
                val service = buildTasksService(context) ?: error("No Tasks service")
                val listId = getDefaultTaskListId(service) ?: error("No task list")

                val gTask = service.tasks().get(listId, googleTaskId).execute()
                gTask.status = if (completed) "completed" else "needsAction"
                if (!completed) {
                    gTask.completed = null
                }
                service.tasks().update(listId, googleTaskId, gTask).execute()
                Log.d(TAG, "Updated Google Task $googleTaskId → completed=$completed")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update Google Task $googleTaskId after retries", e)
            false
        }
    }
}
