package com.theblankstate.preamble.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.analytics.AnalyticsManager
import com.theblankstate.preamble.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceTaskService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var aiProvider: com.theblankstate.preamble.ai.AiProvider? = null

    private fun getOrCreateProvider(): com.theblankstate.preamble.ai.AiProvider? {
        aiProvider?.let { return it }
        val apiKey = com.theblankstate.preamble.BuildConfig.AI_API_KEY
        if (apiKey.isBlank()) return null
        val providerName = com.theblankstate.preamble.BuildConfig.AI_PROVIDER
        val provider = when (com.theblankstate.preamble.ai.AiProviderType.valueOf(providerName)) {
            com.theblankstate.preamble.ai.AiProviderType.MISTRAL ->
                com.theblankstate.preamble.ai.MistralProvider(apiKey)
            com.theblankstate.preamble.ai.AiProviderType.OPENAI ->
                com.theblankstate.preamble.ai.OpenAiProvider(apiKey)
            com.theblankstate.preamble.ai.AiProviderType.GEMINI ->
                com.theblankstate.preamble.ai.GeminiProvider(apiKey)
            com.theblankstate.preamble.ai.AiProviderType.CLAUDE ->
                com.theblankstate.preamble.ai.ClaudeProvider(apiKey)
        }
        aiProvider = provider
        return provider
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val textCommand = intent?.getStringExtra(EXTRA_TEXT_COMMAND)
        val existingTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val isNotification = intent?.getBooleanExtra(EXTRA_IS_NOTIFICATION, false) ?: false
        val isVoice = textCommand.isNullOrBlank()

        createVoiceChannel()
        startForegroundService(isVoice)
        
        if (!isVoice) {
            // Text command from notification or external source
            if (intent != null) {
                AnalyticsManager.trackNotificationAction("quick_add")
            }
            saveTask(textCommand!!, existingTaskId, isNotification)
        } else {
            // Voice mode: listen via microphone
            if (intent != null) {
                AnalyticsManager.trackNotificationAction("voice")
            }
            startListening()
        }
        
        return START_NOT_STICKY
    }

    private fun createVoiceChannel() {
        val channel = NotificationChannel(
            VOICE_CHANNEL_ID,
            "Voice Input",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Voice task recording"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundService(isVoice: Boolean) {
        val notification = NotificationCompat.Builder(this, VOICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (isVoice) "Listening..." else "Saving Task...")
            .setContentText(if (isVoice) "Preamble is recording your voice task" else "Please wait")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (isVoice) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 2048 // FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            try {
                startForeground(VOICE_NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.w("VoiceTaskService", "startForeground with SHORT_SERVICE failed, trying SPECIAL_USE", e)
                try {
                    startForeground(VOICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } catch (e2: Exception) {
                    startForeground(VOICE_NOTIFICATION_ID, notification)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (isVoice) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(VOICE_NOTIFICATION_ID, notification, type)
        } else {
            startForeground(VOICE_NOTIFICATION_ID, notification)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@VoiceTaskService, "Listening...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e("VoiceTaskService", "Speech error: $error")
                Toast.makeText(this@VoiceTaskService, "Could not hear, try again", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    // OPTIMISTIC: Insert raw task immediately so it appears in the UI instantly
                    val app = applicationContext as PreambleApplication
                    val now = System.currentTimeMillis()
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                    val rawTask = com.theblankstate.preamble.data.Task(
                        title = spokenText,
                        createdDate = today,
                        createdTimestamp = now,
                        updatedTimestamp = now,
                        isSyncing = true
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        app.repository.insertTask(rawTask)
                    }
                    saveTask(spokenText, rawTask.id)
                } else {
                    Toast.makeText(this@VoiceTaskService, "No speech detected", Toast.LENGTH_SHORT).show()
                    stopSelf()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    /**
     * AI-parse the raw text and update or create the task.
     * If existingTaskId is non-null, the raw task is already in DB (optimistic insert);
     * we just refine it in place. Otherwise, we save fresh.
     */
    private fun saveTask(title: String, existingTaskId: String? = null, isNotification: Boolean = false) {
        val app = applicationContext as PreambleApplication

        CoroutineScope(Dispatchers.IO).launch {
            Log.d("PreambleAI", "═══ VoiceTaskService.saveTask() ═══")
            if (isNotification) {
                AnalyticsManager.trackAiParserUsed("text_quick_add")
            } else {
                AnalyticsManager.trackAiParserUsed("voice")
            }
            Log.d("PreambleAI", "  Input: '$title'")
            Log.d("PreambleAI", "  ExistingTaskId: $existingTaskId")

            // Match the in-app voice/add-sheet behavior: logged-in users parse through
            // the Cloud Function instead of relying on API keys embedded in the APK.
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null && existingTaskId != null) {
                val aiNotifEditEnabled = getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean("ai_notif_edit", false)
                if (!isNotification || !aiNotifEditEnabled) {
                    Log.d("PreambleAI", "  Routing optimistic task through cloud AiParsingWorker")
                    scheduleAiRetry(existingTaskId, title)
                    CoroutineScope(Dispatchers.Main).launch {
                        stopSelf()
                    }
                    return@launch
                }
            }
            
            val provider = getOrCreateProvider()
            Log.d("PreambleAI", "  Provider: ${provider?.name ?: "NULL (no AI configured)"}")
            
            if (provider != null) {
                try {
                    // Fetch existing tasks for AI context (modify/delete operations)
                    val allTasks = app.repository.tasksFlow.firstOrNull() ?: emptyList()
                    val subtaskIntensity = applicationContext.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                            .getInt("ai_subtask_intensity", 0)
                    Log.d("PreambleAI", "  SubtaskIntensity: $subtaskIntensity")
                    Log.d("PreambleAI", "  Context tasks: ${allTasks.size}")

                    val aiNotifEditEnabled = getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                            .getBoolean("ai_notif_edit", false)
                    val toolsToUse = if (isNotification && !aiNotifEditEnabled) {
                        com.theblankstate.preamble.ai.TaskTools.tools
                            .filter { it.name in listOf("add_task", "set_reminder") }
                    } else {
                        com.theblankstate.preamble.ai.TaskTools.tools
                    }

                    val systemMsg = com.theblankstate.preamble.ai.ChatMessage("system",
                        com.theblankstate.preamble.ai.AiPromptFactory.buildSystemPrompt(
                            allTasks,
                            subtaskIntensity = subtaskIntensity,
                            isNotificationEdit = isNotification && aiNotifEditEnabled
                        )
                    )
                    val userMsg = com.theblankstate.preamble.ai.ChatMessage("user", title)

                    Log.d("PreambleAI", "  Calling AI... (notif=$isNotification, editEnabled=$aiNotifEditEnabled, tools=${toolsToUse.map { it.name }})")
                    val response = provider.chat(
                        listOf(systemMsg, userMsg),
                        toolsToUse
                    )
                    Log.d("PreambleAI", "  AI response: text=${response.text?.take(100)}, toolCalls=${response.toolCalls?.size ?: 0}")

                    if (!response.toolCalls.isNullOrEmpty()) {
                        for (call in response.toolCalls) {
                            when (call.name) {
                                "add_task", "set_reminder" -> {
                                    val taskTitle = call.arguments["title"] ?: title
                                    val date = call.arguments["date"]
                                    val time = call.arguments["deadline_time"] ?: call.arguments["time"]
                                    val tags = call.arguments["tags"]
                                    val priority = call.arguments["priority"]?.toIntOrNull() ?: 0
                                    val recurrence = call.arguments["recurrence"]
                                    val description = call.arguments["description"]
                                    val subtasksList = com.theblankstate.preamble.ai.TaskTools.parseSubtasks(call.arguments["subtasks"])
                                    
                                    val isHabit = call.arguments["is_habit"]?.lowercase()?.let { it == "true" || it == "1" } ?: false
                                    val isEvent = call.arguments["is_event"]?.lowercase()?.let { it == "true" || it == "1" } ?: false
                                    val eventIcon = call.arguments["event_icon"]
                                    val eventColor = call.arguments["event_color"]
                                    val recurrenceInterval = call.arguments["recurrence_interval"]?.toIntOrNull() ?: 1
                                    val recurrenceDays = call.arguments["recurrence_days"]?.takeIf { it.isNotBlank() }

                                    // Unified rollover decision (same as in-app path)
                                    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
                                    val validRecurrenceCheck = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly") }
                                    val effectiveRecurrenceCheck = if (isHabit && validRecurrenceCheck == null) "daily" else validRecurrenceCheck
                                    
                                    val rolloverDecision = call.name == "add_task" && com.theblankstate.preamble.ai.TaskTools.decideRollover(
                                        rolloverArg = call.arguments["rollover"],
                                        title = taskTitle,
                                        date = date,
                                        deadlineTime = time,
                                        recurrence = effectiveRecurrenceCheck,
                                        today = todayStr
                                    )
                                    // Effective recurrenceType sent to DB: real recurrence wins, else "rollover" sentinel, else null
                                    val effectiveRecurrence = effectiveRecurrenceCheck ?: if (rolloverDecision) "rollover" else null
                                    val finalIsHabit = isHabit && effectiveRecurrence != null && effectiveRecurrence != "rollover"

                                    Log.d("PreambleAI", "  Parsed: title='$taskTitle', date=$date, time=$time, tags=$tags, pri=$priority, desc=${description?.take(50)}, subtasks=${subtasksList.size}, rollover=$rolloverDecision, effRec=$effectiveRecurrence, isHabit=$finalIsHabit, isEvent=$isEvent, interval=$recurrenceInterval, days=$recurrenceDays")

                                    // set_reminder from notification (only when feature is ON):
                                    // if an existing task matches the title, add the alarm to THAT task.
                                    val handledAsReminderOnExisting = if (call.name == "set_reminder" && isNotification && aiNotifEditEnabled && time != null) {
                                        val allCurrentTasks = app.repository.tasksFlow.firstOrNull() ?: emptyList()
                                        val matchedTask = com.theblankstate.preamble.ai.TaskTools.findMatchingTask(taskTitle, allCurrentTasks)
                                        if (matchedTask != null) {
                                            Log.d("PreambleAI", "  set_reminder → adding alarm to existing task '${matchedTask.title}'")
                                            val triggerMs = timeStringToEpochMs(time, date)
                                            if (triggerMs != null) {
                                                val currentReminders = matchedTask.localReminders.toMutableList()
                                                if (currentReminders.size < com.theblankstate.preamble.data.Reminder.MAX_REMINDERS) {
                                                    currentReminders.add(com.theblankstate.preamble.data.Reminder(epochMs = triggerMs, type = "exact"))
                                                    val updated = matchedTask.copy(
                                                        remindersJson = com.theblankstate.preamble.data.Reminder.toJson(currentReminders),
                                                        updatedTimestamp = System.currentTimeMillis()
                                                    )
                                                    app.repository.updateTask(updated)
                                                    TaskAlarmManager.scheduleReminders(applicationContext, updated)
                                                }
                                            }
                                            cleanupPlaceholder(app, existingTaskId)
                                            CoroutineScope(Dispatchers.Main).launch {
                                                Toast.makeText(applicationContext, "✓ Reminder added to: ${matchedTask.title}", Toast.LENGTH_SHORT).show()
                                            }
                                            true
                                        } else false
                                    } else false

                                    if (!handledAsReminderOnExisting) {
                                        if (existingTaskId != null) {
                                            updateExistingTask(app, existingTaskId, taskTitle, date, time, tags, priority, effectiveRecurrence, description, isHabit = finalIsHabit, isEvent = isEvent, eventIcon = eventIcon, eventColor = eventColor, recurrenceInterval = recurrenceInterval, recurrenceDays = recurrenceDays)
                                            if (subtasksList.isNotEmpty()) {
                                                app.repository.addSubtasks(existingTaskId, subtasksList)
                                            }
                                        } else {
                                            val savedTaskId = saveInterpretedTask(app, taskTitle, date, time, tags, priority, effectiveRecurrence, description, isHabit = finalIsHabit, isEvent = isEvent, eventIcon = eventIcon, eventColor = eventColor, recurrenceInterval = recurrenceInterval, recurrenceDays = recurrenceDays)
                                            if (savedTaskId != null && subtasksList.isNotEmpty()) {
                                                app.repository.addSubtasks(savedTaskId, subtasksList)
                                            }
                                        }
                                    }
                                }
                                "modify_task" -> {
                                    val targetTitle = call.arguments["target_title"] ?: title
                                    val newTitle = call.arguments["new_title"]
                                    val newDate = call.arguments["new_date"]
                                    val newTime = call.arguments["new_time"]
                                    val newPriority = call.arguments["new_priority"]?.toIntOrNull()
                                    val newTags = call.arguments["new_tags"]
                                    Log.d("PreambleAI", "  modify_task: target='$targetTitle'")
                                    val modified = modifyInterpretedTask(app, targetTitle, newTitle, newDate, newTime, newPriority, newTags)
                                    // Remove optimistic placeholder since we modified an existing task
                                    cleanupPlaceholder(app, existingTaskId)
                                    if (isNotification) {
                                        val msg = if (modified) "✓ Task updated: ${newTitle ?: targetTitle}" else "⚠ Task not found: $targetTitle"
                                        CoroutineScope(Dispatchers.Main).launch {
                                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                "delete_task" -> {
                                    val targetTitle = call.arguments["title"] ?: title
                                    Log.d("PreambleAI", "  delete_task: target='$targetTitle'")
                                    val deleted = deleteInterpretedTask(app, targetTitle)
                                    // Remove optimistic placeholder
                                    cleanupPlaceholder(app, existingTaskId)
                                    if (isNotification) {
                                        val msg = if (deleted) "✓ Task deleted: $targetTitle" else "⚠ Task not found: $targetTitle"
                                        CoroutineScope(Dispatchers.Main).launch {
                                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                "complete_task" -> {
                                    val targetTitle = call.arguments["title"] ?: title
                                    Log.d("PreambleAI", "  complete_task: target='$targetTitle'")
                                    val completed = completeInterpretedTask(app, targetTitle)
                                    // Remove optimistic placeholder
                                    cleanupPlaceholder(app, existingTaskId)
                                    if (isNotification) {
                                        val msg = if (completed) "✓ Task completed: $targetTitle" else "⚠ Task not found: $targetTitle"
                                        CoroutineScope(Dispatchers.Main).launch {
                                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                "list_tasks" -> {
                                    // List is informational — just clean up placeholder
                                    Log.d("PreambleAI", "  list_tasks — cleaning up placeholder")
                                    cleanupPlaceholder(app, existingTaskId)
                                }
                                else -> {
                                    if (existingTaskId != null) {
                                        clearSyncing(app, existingTaskId)
                                    } else {
                                        saveInterpretedTask(app, title)
                                    }
                                }
                            }
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            stopSelf()
                        }
                    } else {
                        // No tool calls — AI couldn't parse, save task as-is
                        Log.w("VoiceTaskService", "AI returned no tool calls, saving raw task as-is")
                        if (existingTaskId != null) {
                            clearSyncing(app, existingTaskId)
                        } else {
                            saveInterpretedTask(app, title)
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            stopSelf()
                        }
                    }
                } catch (e: java.net.UnknownHostException) {
                    // Network error — schedule background retry
                    Log.e("VoiceTaskService", "Network error, scheduling retry", e)
                    if (existingTaskId != null) {
                        scheduleAiRetry(existingTaskId, title)
                    } else {
                        saveInterpretedTask(app, title)
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        stopSelf()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Timeout — schedule background retry
                    Log.e("VoiceTaskService", "Timeout error, scheduling retry", e)
                    if (existingTaskId != null) {
                        scheduleAiRetry(existingTaskId, title)
                    } else {
                        saveInterpretedTask(app, title)
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        stopSelf()
                    }
                } catch (e: Exception) {
                    // Non-network error (parse failure, API error, etc.) — save raw task as-is, don't retry
                    Log.e("VoiceTaskService", "AI processing failed, saving raw task", e)
                    if (existingTaskId != null) {
                        clearSyncing(app, existingTaskId)
                    } else {
                        saveInterpretedTask(app, title)
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        stopSelf()
                    }
                }
            } else {
                // No AI provider — keep the raw task as-is
                if (existingTaskId != null) {
                    clearSyncing(app, existingTaskId)
                } else {
                    saveInterpretedTask(app, title)
                }
                CoroutineScope(Dispatchers.Main).launch {
                    stopSelf()
                }
            }
        }
    }

    /** Schedule a background WorkManager job to retry AI parsing with exponential backoff */
    private fun scheduleAiRetry(taskId: String, rawText: String) {
        val data = androidx.work.Data.Builder()
            .putString("taskId", taskId)
            .putString("rawText", rawText)
            .build()
        val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.ai.AiParsingWorker>()
            .setInputData(data)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30_000L,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        androidx.work.WorkManager.getInstance(applicationContext).enqueue(req)
        Log.d("VoiceTaskService", "Scheduled AiParsingWorker for task $taskId")
    }

    /** Silently update an already-inserted optimistic task with AI-refined data */
    private suspend fun updateExistingTask(
        app: PreambleApplication,
        taskId: String,
        refinedTitle: String,
        date: String?,
        time: String?,
        tags: String?,
        priority: Int,
        recurrence: String? = null,
        description: String? = null,
        isHabit: Boolean = false,
        isEvent: Boolean = false,
        eventIcon: String? = null,
        eventColor: String? = null,
        recurrenceInterval: Int? = null,
        recurrenceDays: String? = null
    ) {
        val existing = app.repository.getTaskById(taskId) ?: return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly", "rollover") }
        val updated = existing.copy(
            title = refinedTitle,
            createdDate = date ?: today,
            deadlineTime = time,
            tags = tags,
            priority = priority,
            description = description ?: existing.description,
            recurrenceType = validRecurrence ?: existing.recurrenceType,
            recurrenceInterval = if (validRecurrence != null && validRecurrence != "rollover") recurrenceInterval ?: existing.recurrenceInterval else null,
            recurrenceDays = if (validRecurrence != null && validRecurrence != "rollover") recurrenceDays ?: existing.recurrenceDays else null,
            isHabit = isHabit,
            isEvent = isEvent,
            eventIcon = eventIcon,
            eventColor = eventColor,
            isSyncing = false,
            updatedTimestamp = System.currentTimeMillis()
        )
        app.repository.updateTask(updated)
        if (tags != null) {
            app.repository.saveTagOverride(taskId, tags)
        }
        // Schedule alarm if task has a deadline time
        scheduleAlarmForTask(updated)
    }

    /** Just flip isSyncing=false on a raw placeholder task */
    private suspend fun clearSyncing(app: PreambleApplication, taskId: String) {
        val existing = app.repository.getTaskById(taskId) ?: return
        app.repository.updateTask(existing.copy(isSyncing = false, updatedTimestamp = System.currentTimeMillis()))
    }

    /** Returns true if a matching task was found and modified */
    private suspend fun modifyInterpretedTask(
        app: PreambleApplication,
        targetTitle: String,
        newTitle: String?,
        newDate: String?,
        newTime: String?,
        newPriority: Int?,
        newTags: String?
    ): Boolean {
        val allTasks = app.repository.tasksFlow.firstOrNull() ?: emptyList()
        val task = com.theblankstate.preamble.ai.TaskTools.findMatchingTask(targetTitle, allTasks)
        if (task != null) {
            val updated = task.copy(
                title = newTitle ?: task.title,
                createdDate = newDate ?: task.createdDate,
                deadlineTime = newTime ?: task.deadlineTime,
                priority = newPriority ?: task.priority,
                tags = newTags ?: task.tags,
                updatedTimestamp = System.currentTimeMillis()
            )
            app.repository.updateTask(updated)
            if (newTags != null) {
                app.repository.saveTagOverride(updated.id, newTags)
            }
            // Reschedule reminders if time changed
            if (newTime != null) {
                TaskAlarmManager.scheduleReminders(applicationContext, updated)
            }
            Log.d("PreambleAI", "  Modified task '${task.title}' → '${updated.title}'")
            return true
        } else {
            Log.w("PreambleAI", "  No matching task found for modify '$targetTitle', creating new")
            saveInterpretedTask(app, newTitle ?: targetTitle, newDate, newTime, newTags, newPriority ?: 0)
            return false
        }
    }

    /** Delete a task found by title match. Returns true if found and deleted. */
    private suspend fun deleteInterpretedTask(app: PreambleApplication, targetTitle: String): Boolean {
        val allTasks = app.repository.tasksFlow.firstOrNull() ?: emptyList()
        val task = com.theblankstate.preamble.ai.TaskTools.findMatchingTask(targetTitle, allTasks)
        if (task != null) {
            TaskAlarmManager.cancelAllReminders(applicationContext, task.id)
            app.repository.markAsDeleted(task.id)
            app.repository.deleteTask(task)
            Log.d("PreambleAI", "  Deleted task '${task.title}'")
            return true
        } else {
            Log.w("PreambleAI", "  No matching task found for delete '$targetTitle'")
            return false
        }
    }

    /** Complete a task found by title match. Returns true if found and completed. */
    private suspend fun completeInterpretedTask(app: PreambleApplication, targetTitle: String): Boolean {
        val allTasks = app.repository.tasksFlow.firstOrNull() ?: emptyList()
        val task = com.theblankstate.preamble.ai.TaskTools.findMatchingTask(targetTitle, allTasks)
        if (task != null && !task.isCompleted) {
            app.repository.toggleTask(task)
            TaskAlarmManager.cancelAllReminders(applicationContext, task.id)
            Log.d("PreambleAI", "  Completed task '${task.title}'")
            return true
        } else {
            Log.w("PreambleAI", "  No matching task found for complete '$targetTitle'")
            return false
        }
    }

    /** Remove the optimistic placeholder task that was inserted before AI processing */
    private suspend fun cleanupPlaceholder(app: PreambleApplication, existingTaskId: String?) {
        if (existingTaskId != null) {
            val placeholder = app.repository.getTaskById(existingTaskId)
            if (placeholder != null) {
                app.repository.deleteTask(placeholder)
                Log.d("PreambleAI", "  Cleaned up optimistic placeholder '$existingTaskId'")
            }
        }
    }

    /**
     * Save a new interpreted task. Returns the task ID for subtask attachment,
     * or null if creation failed.
     */
    private suspend fun saveInterpretedTask(
        app: PreambleApplication,
        title: String,
        date: String? = null,
        time: String? = null,
        tags: String? = null,
        priority: Int = 0,
        recurrence: String? = null,
        description: String? = null,
        isHabit: Boolean = false,
        isEvent: Boolean = false,
        eventIcon: String? = null,
        eventColor: String? = null,
        recurrenceInterval: Int? = null,
        recurrenceDays: String? = null
    ): String? {
        var localTaskCreated = false
        var createdTaskId: String? = null
        val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly", "rollover") }

        if (com.theblankstate.preamble.sync.GoogleTasksManager.syncVoiceTasks.value
            && com.theblankstate.preamble.sync.GoogleTasksManager.isLinked.value) {
            
            val googleTaskId = com.theblankstate.preamble.sync.GoogleTasksManager.createGoogleTask(
                this@VoiceTaskService, title, date
            )
            if (googleTaskId != null) {
                val taskDate = date ?: java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                val now = System.currentTimeMillis()
                
                val localTask = com.theblankstate.preamble.data.Task(
                    id = "gtask_$googleTaskId",
                    title = title,
                    createdDate = taskDate,
                    deadlineTime = time,
                    createdTimestamp = now,
                    updatedTimestamp = now,
                    priority = priority,
                    source = "google_tasks",
                    tags = tags,
                    description = description,
                    recurrenceType = validRecurrence,
                    recurrenceInterval = if (validRecurrence != null && validRecurrence != "rollover") recurrenceInterval else null,
                    recurrenceDays = if (validRecurrence != null && validRecurrence != "rollover") recurrenceDays else null,
                    isHabit = isHabit,
                    isEvent = isEvent,
                    eventIcon = eventIcon,
                    eventColor = eventColor
                )
                app.repository.insertTask(localTask)
                if (tags != null) {
                    app.repository.saveTagOverride("gtask_$googleTaskId", tags)
                }
                localTaskCreated = true
                createdTaskId = localTask.id
                // Schedule alarm if task has a deadline time
                scheduleAlarmForTask(localTask)
            }
        }

        if (!localTaskCreated) {
            val taskDate = date ?: java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            val now = System.currentTimeMillis()
            val localTask = com.theblankstate.preamble.data.Task(
                title = title,
                createdDate = taskDate,
                deadlineTime = time,
                createdTimestamp = now,
                updatedTimestamp = now,
                priority = priority,
                tags = tags,
                description = description,
                recurrenceType = validRecurrence,
                recurrenceInterval = if (validRecurrence != null && validRecurrence != "rollover") recurrenceInterval else null,
                recurrenceDays = if (validRecurrence != null && validRecurrence != "rollover") recurrenceDays else null,
                isHabit = isHabit,
                isEvent = isEvent,
                eventIcon = eventIcon,
                eventColor = eventColor
            )
            app.repository.insertTask(localTask)
            if (tags != null) {
                app.repository.saveTagOverride(localTask.id, tags)
            }
            createdTaskId = localTask.id
            // Schedule alarm if task has a deadline time
            scheduleAlarmForTask(localTask)
        }
        if (createdTaskId != null) {
            AnalyticsManager.trackTaskCreated(
                category = tags ?: "uncategorized",
                isPriority = priority > 0,
                hasDeadline = time != null,
                isRecurring = validRecurrence != null
            )
        }
        return createdTaskId
    }

    /** Convert AI time string "HH:mm" + optional date to epoch millis for today (or given date). */
    private fun timeStringToEpochMs(timeStr: String, dateStr: String? = null): Long? {
        return try {
            val today = dateStr ?: java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse("$today $timeStr")?.time
        } catch (_: Exception) { null }
    }

    /** Auto-set default reminder and schedule all reminders for a task */
    private suspend fun scheduleAlarmForTask(task: com.theblankstate.preamble.data.Task) {
        val app = applicationContext as PreambleApplication

        // Auto-set default reminder if no reminders exist
        val taskWithReminder = if (task.remindersJson == null) {
            val defaultReminder = if (task.deadlineTime != null) {
                // Tasks with deadline: 10-min-before reminder
                com.theblankstate.preamble.data.Reminder.DEFAULT
            } else {
                // All-day tasks (no deadline): 9 AM morning reminder on task date
                com.theblankstate.preamble.data.Reminder.defaultAllDay(task.createdDate)
            }
            if (defaultReminder != null) {
                val updated = task.copy(remindersJson = com.theblankstate.preamble.data.Reminder.toJson(listOf(defaultReminder)))
                app.repository.updateTask(updated)
                updated
            } else task
        } else task

        // Schedule all reminders (works for both "before" and "exact" types)
        TaskAlarmManager.scheduleReminders(applicationContext, taskWithReminder)
        Log.d("PreambleAlarm", "Scheduled reminders for '${task.title}'")
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val VOICE_CHANNEL_ID = "preamble_voice_input"
        private const val VOICE_NOTIFICATION_ID = 1002
        const val EXTRA_TEXT_COMMAND = "extra_text_command"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_IS_NOTIFICATION = "is_notification"
    }
}
