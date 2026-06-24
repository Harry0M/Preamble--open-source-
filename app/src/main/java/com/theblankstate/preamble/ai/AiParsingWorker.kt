package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.PreambleApplication
import com.google.gson.Gson
import com.theblankstate.preamble.collab.AssigneeResolution
import com.theblankstate.preamble.collab.AssigneeResolver
import com.theblankstate.preamble.collab.DefaultAssigneeResolver
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.repository.WorkspaceRepository
import com.theblankstate.preamble.data.Subtask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Background worker that retries AI parsing for tasks that failed due to network
 * or other transient errors. The raw task is already in the DB with isSyncing=true.
 * On success, silently updates the task with AI-refined data (title, date, time, tags,
 * priority, recurrence, description, subtasks).
 * On network failure, returns Result.retry() for exponential backoff.
 * On non-network failure (bad input, API error), saves task as-is and returns Result.success().
 *
 * CLOUD MODE: When user is logged in, routes through Cloud Function (aiParseTask).
 *   - No API keys needed in APK
 *   - Same prompt system, same quality
 *   - Falls back to local if cloud fails
 */
class AiParsingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val rawText = inputData.getString("rawText") ?: return Result.failure()
        val userOverrides = inputData.getString("userOverrides")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

        val task = app.repository.getTaskById(taskId)
        if (task == null) {
            // Task was deleted by user in the meantime, nothing to do
            return Result.success()
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val subtaskIntensity = applicationContext.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
            .getInt("ai_subtask_intensity", 0)

        // Try cloud first if logged in
        if (FirebaseAuth.getInstance().currentUser != null) {
            try {
                val result = CloudAiService.parseTask(
                    rawText = rawText,
                    subtaskIntensity = subtaskIntensity,
                    isNotificationEdit = false,
                )

                if (result != null && result.toolCalls.isNotEmpty()) {
                    applyToolCalls(app, task, taskId, result.toolCalls, today, userOverrides)
                    Log.d(TAG, "Cloud AI parsing succeeded for task $taskId")
                    return Result.success()
                }
                // Cloud returned no tool calls — fall through to local
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Cloud: Network error for task $taskId, will retry", e)
                return Result.retry()
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Cloud: Timeout for task $taskId, will retry", e)
                return Result.retry()
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Cloud: I/O error for task $taskId, will retry", e)
                return Result.retry()
            } catch (e: Exception) {
                Log.w(TAG, "Cloud parsing failed, trying local fallback", e)
                // Fall through to local
            }
        }

        // LOCAL FALLBACK — direct API call (for offline or not logged in)
        val apiKey = BuildConfig.AI_API_KEY
        if (apiKey.isBlank()) {
            // No AI configured — just clear syncing and keep raw task
            app.repository.updateTask(task.copy(isSyncing = false))
            return Result.success()
        }

        val provider: AiProvider = AiProviderFactory.main() ?: run {
            Log.e(TAG, "AI provider unavailable")
            app.repository.updateTask(task.copy(isSyncing = false))
            return Result.success()
        }

        return try {
            val systemMsg = ChatMessage("system",
                AiPromptFactory.buildSystemPrompt(subtaskIntensity = subtaskIntensity)
            )
            val userMsg = ChatMessage("user", rawText)

            val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)

            if (!response.toolCalls.isNullOrEmpty()) {
                applyLocalToolCalls(app, task, taskId, response.toolCalls, today, userOverrides)
            } else {
                // No tool calls — AI couldn't parse input, save raw task as-is
                Log.w(TAG, "AI returned no tool calls for task $taskId, saving as-is")
                app.repository.updateTask(task.copy(isSyncing = false))
            }

            Log.d(TAG, "Local AI parsing succeeded for task $taskId")
            Result.success()

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Network error for task $taskId, will retry", e)
            Result.retry()
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout for task $taskId, will retry", e)
            Result.retry()
        } catch (e: java.io.IOException) {
            Log.e(TAG, "I/O error for task $taskId, will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "AI parsing failed (non-retryable) for task $taskId, saving raw", e)
            app.repository.updateTask(task.copy(isSyncing = false))
            Result.success()
        }
    }

    /**
     * Apply tool calls from CLOUD response (CloudToolCall format).
     */
    private suspend fun applyToolCalls(
        app: PreambleApplication,
        task: com.theblankstate.preamble.data.Task,
        taskId: String,
        toolCalls: List<CloudToolCall>,
        today: String,
        userOverrides: Set<String> = emptySet(),
    ) {
        for (call in toolCalls) {
            when (call.name) {
                "add_task", "set_reminder" -> {
                    applyParsedTask(app, task, taskId, call.args, today, userOverrides)
                }
                else -> {
                    app.repository.updateTask(task.copy(isSyncing = false))
                }
            }
        }
    }

    /**
     * Apply tool calls from LOCAL response (ToolCall format).
     */
    private suspend fun applyLocalToolCalls(
        app: PreambleApplication,
        task: com.theblankstate.preamble.data.Task,
        taskId: String,
        toolCalls: List<ToolCall>,
        today: String,
        userOverrides: Set<String> = emptySet(),
    ) {
        for (call in toolCalls) {
            when (call.name) {
                "add_task", "set_reminder" -> {
                    applyParsedTask(app, task, taskId, call.arguments, today, userOverrides)
                }
                else -> {
                    app.repository.updateTask(task.copy(isSyncing = false))
                }
            }
        }
    }

    /**
     * Shared logic: apply parsed task arguments to the existing task entity.
     */
    private suspend fun applyParsedTask(
        app: PreambleApplication,
        task: com.theblankstate.preamble.data.Task,
        taskId: String,
        args: Map<String, String>,
        today: String,
        userOverrides: Set<String> = emptySet(),
    ) {
        Log.d(TAG, "applyParsedTask: taskId=$taskId, args=$args, userOverrides=$userOverrides")
        val rawText = task.title
        // Title is always refined by AI (that's the whole point)
        val refinedTitle = args["title"] ?: rawText
        // Respect user overrides: if user set a field manually, keep their value
        val date = if ("date" in userOverrides) null else args["date"]
        val time = if ("time" in userOverrides) null else (args["deadline_time"] ?: args["time"])
        val tags = if ("tags" in userOverrides) null else args["tags"]
        val priority = if ("priority" in userOverrides) task.priority else (args["priority"]?.toIntOrNull() ?: 0)
        val recurrence = args["recurrence"]
        val description = if ("description" in userOverrides) null else args["description"]
        val subtasksList = if ("subtasks" in userOverrides) emptyList() else TaskTools.parseSubtasks(args["subtasks"])
        val isHabit = args["is_habit"]?.lowercase()?.let { it == "true" || it == "1" } ?: false
        val isEvent = args["is_event"]?.lowercase()?.let { it == "true" || it == "1" } ?: false
        val eventIcon = args["event_icon"]
        val eventColor = args["event_color"]
        val recurrenceInterval = args["recurrence_interval"]?.toIntOrNull() ?: 1
        val recurrenceDays = args["recurrence_days"]?.takeIf { it.isNotBlank() }

        val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly") }
        val effectiveRecurrenceCheck = if (isHabit && validRecurrence == null) "daily" else validRecurrence

        val rolloverDecision = TaskTools.decideRollover(
            rolloverArg = args["rollover"],
            title = refinedTitle,
            date = date,
            deadlineTime = time,
            recurrence = effectiveRecurrenceCheck,
            today = today
        )
        val effectiveRecurrence = effectiveRecurrenceCheck ?: if (rolloverDecision) "rollover" else task.recurrenceType
        val finalIsHabit = isHabit && effectiveRecurrence != null && effectiveRecurrence != "rollover"

        var updated = task.copy(
            title = refinedTitle,
            createdDate = date ?: task.createdDate,
            deadlineTime = time ?: task.deadlineTime,
            tags = tags ?: task.tags,
            priority = priority,
            description = description ?: task.description,
            recurrenceType = effectiveRecurrence,
            recurrenceInterval = if (effectiveRecurrence != null && effectiveRecurrence != "rollover") recurrenceInterval else null,
            recurrenceDays = if (effectiveRecurrence != null && effectiveRecurrence != "rollover") recurrenceDays else null,
            isHabit = finalIsHabit,
            isEvent = isEvent,
            eventIcon = eventIcon,
            eventColor = eventColor,
            isSyncing = false,
            updatedTimestamp = System.currentTimeMillis()
        )
        // Auto-set default reminder if none exist
        if (updated.remindersJson == null) {
            val defaultReminder = if (time != null) {
                // Tasks with deadline: 10-min-before reminder
                com.theblankstate.preamble.data.Reminder.DEFAULT
            } else {
                // All-day tasks (no deadline): 9 AM morning reminder
                com.theblankstate.preamble.data.Reminder.defaultAllDay(updated.createdDate)
            }
            if (defaultReminder != null) {
                updated = updated.copy(remindersJson = com.theblankstate.preamble.data.Reminder.toJson(listOf(defaultReminder)))
            }
        }
        val workspaceRepository = WorkspaceRepository()
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid

        // The parse phase only refines and persists the task's attributes and clears
        // isSyncing. The durable canonical-document send for an explicitly-confirmed
        // collaborative task is no longer performed here — it is the responsibility of the
        // chained CollaborativeSendWorker, so the send no longer depends on parsing
        // producing tool calls (Requirements 23.4, 23.7).
        app.repository.updateTask(updated)
        if (tags != null) {
            app.repository.saveTagOverride(taskId, tags)
        }
        // Schedule alarm reminders
        if (updated.remindersJson != null) {
            com.theblankstate.preamble.notification.TaskAlarmManager.scheduleReminders(applicationContext, updated)
            Log.d(TAG, "Scheduled reminders for task $taskId")
        }
        // Add subtasks if AI generated them
        if (subtasksList.isNotEmpty()) {
            app.repository.addSubtasks(taskId, subtasksList)
            Log.d(TAG, "Added ${subtasksList.size} subtasks to task $taskId")
        }

        val taskWithSubtasks = packSubtasks(app, updated, taskId)

        // Voice/notification/task-sheet raw text can assign friends by natural language.
        // This happens after normal parsing so the core task creation flow stays unchanged.
        // The Assignee_Resolver runs client-side, bounded by a 30 s budget, so normal
        // task creation is never blocked or delayed (Requirements 9.1, 9.2, 9.9).
        if (taskWithSubtasks.collabAdminUid == null && currentUid != null) {
            resolveAndAssignFriends(app, workspaceRepository, taskWithSubtasks, rawText, currentUid)
        }
    }

    /**
     * Runs the client-side [AssigneeResolver] over the already-saved task's raw text and,
     * only when the resolution is [AssigneeResolution.Assigned], promotes the task to a
     * collaborative task via [WorkspaceRepository.assignTaskToMultiple].
     *
     * The whole phase is bounded by `withTimeout(30_000)` so it can never block or delay
     * the already-completed own-copy save (Requirements 9.1, 9.2). Every non-`Assigned`
     * outcome leaves the task non-collaborative; `Ambiguous`, `Failed`, and timeout surface
     * a message to the user (Requirements 9.4, 9.7, 9.8). Resolution runs entirely on-device
     * and no longer depends on the removed `aiResolveAssignees` Cloud Function (Requirement 9.9).
     */
    private suspend fun resolveAndAssignFriends(
        app: PreambleApplication,
        workspaceRepository: WorkspaceRepository,
        task: com.theblankstate.preamble.data.Task,
        rawText: String,
        currentUid: String,
    ) {
        val friends = runCatching { workspaceRepository.getFriendsOnce() }
            .onFailure { Log.w(TAG, "Could not load friends for assignment resolution", it) }
            .getOrDefault(emptyList())
        if (friends.isEmpty()) return

        val resolution: AssigneeResolution = try {
            withTimeout(ASSIGNEE_RESOLUTION_TIMEOUT_MS) {
                assigneeResolver.resolve(rawText, friends)
            }
        } catch (e: TimeoutCancellationException) {
            // Timeout: leave the saved own copy unchanged and surface a message (Requirement 9.8).
            Log.w(TAG, "Assignee resolution timed out for task ${task.id}", e)
            AssigneeResolution.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Assignee resolution failed for task ${task.id}", e)
            AssigneeResolution.Failed
        }

        when (resolution) {
            is AssigneeResolution.Assigned -> {
                assignResolvedFriends(app, workspaceRepository, task, resolution.friends, currentUid)
            }
            // No assignment intended or an intended assignee matched no friend: keep the task
            // as a normal non-collaborative task with no message (Requirements 9.5, 9.6).
            AssigneeResolution.NoAssignee,
            AssigneeResolution.Unmatched -> Unit
            // An intended name matched more than one friend (Requirement 9.7).
            is AssigneeResolution.Ambiguous ->
                surfaceMessage("Couldn't assign the task: \"${resolution.term}\" matches more than one friend.")
            // Resolution failed or timed out (Requirement 9.8).
            AssigneeResolution.Failed ->
                surfaceMessage("Couldn't determine who to assign this task to. It was saved without an assignee.")
        }
    }

    /**
     * Promotes [task] to a collaborative task assigned to the resolved [assignees] and
     * mirrors the collaborative state into the admin's local copy (Requirement 9.3).
     */
    private suspend fun assignResolvedFriends(
        app: PreambleApplication,
        workspaceRepository: WorkspaceRepository,
        task: com.theblankstate.preamble.data.Task,
        assignees: List<Friend>,
        currentUid: String,
    ) {
        if (assignees.isEmpty()) return
        val adminProfile = com.theblankstate.preamble.data.UserProfileStore
            .load(applicationContext)
        val adminName = adminProfile.name
            ?.takeIf { it.isNotBlank() }
            ?: "Preamble user"
        val adminPhotoUrl = adminProfile.photoUrl
        val now = System.currentTimeMillis()
        val collabTask = task.copy(
            collabAdminUid = currentUid,
            collabAdminName = adminName,
            collabAssigneesJson = Gson().toJson(assignees.map { friend ->
                com.theblankstate.preamble.data.CollabAssigneeStatus(
                    uid = friend.uid,
                    name = friend.name,
                    photoUrl = friend.photoUrl,
                    status = "pending",
                    isCompleted = false,
                    completedTimestamp = null,
                    assignedTimestamp = now
                )
            }),
            assignedByUid = currentUid,
            assignedByName = adminName,
            assignedToUid = assignees.firstOrNull()?.uid,
            assignedToName = assignees.firstOrNull()?.name,
            assignmentStatus = "accepted"
        )
        workspaceRepository.assignTaskToMultiple(
            assignees = assignees,
            task = collabTask,
            adminName = adminName,
            adminPhotoUrl = adminPhotoUrl
        ).onFailure { error ->
            Log.e(TAG, "Error publishing natural-language collaborative task after AI parsing", error)
            surfaceMessage("The collaborative assignment could not be completed.")
        }
        app.repository.updateTask(collabTask)
    }

    /** Surfaces a short user-facing message from the background worker. */
    private suspend fun surfaceMessage(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun packSubtasks(
        app: PreambleApplication,
        task: com.theblankstate.preamble.data.Task,
        taskId: String,
    ): com.theblankstate.preamble.data.Task {
        val subtasks = app.repository.getSubtasksForParentSync(taskId)
        val subtaskObjects = subtasks.map { Subtask(id = it.id, title = it.title, isCompleted = it.isCompleted) }
        return task.copy(subtasksJson = Gson().toJson(subtaskObjects))
    }

    companion object {
        private const val TAG = "AiParsingWorker"

        /** Upper bound for the separate assignee-resolution phase (Requirements 9.2, 9.8). */
        private const val ASSIGNEE_RESOLUTION_TIMEOUT_MS = 30_000L

        /**
         * Client-side resolver used by the separate assignee-resolution phase. It runs
         * entirely on-device and does not call the removed `aiResolveAssignees` Cloud
         * Function (Requirement 9.9).
         */
        private val assigneeResolver: AssigneeResolver = DefaultAssigneeResolver()
    }
}
