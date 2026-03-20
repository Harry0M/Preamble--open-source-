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
        createVoiceChannel()
        startForegroundService()
        
        val textCommand = intent?.getStringExtra(EXTRA_TEXT_COMMAND)
        val existingTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        
        if (!textCommand.isNullOrBlank()) {
            // Text command from notification or external source
            saveTask(textCommand, existingTaskId)
        } else {
            // Voice mode: listen via microphone
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

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, VOICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Listening...")
            .setContentText("Preamble is recording your voice task")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                VOICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
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
    private fun saveTask(title: String, existingTaskId: String? = null) {
        val app = applicationContext as PreambleApplication

        CoroutineScope(Dispatchers.IO).launch {
            val provider = getOrCreateProvider()
            if (provider != null) {
                try {
                    // Fetch existing tasks for AI context (modify/delete operations)
                    val allTasks = app.repository.tasksFlow.firstOrNull() ?: emptyList()
                    val systemMsg = com.theblankstate.preamble.ai.ChatMessage("system",
                        com.theblankstate.preamble.ai.AiPromptFactory.buildSystemPrompt(allTasks)
                    )
                    val userMsg = com.theblankstate.preamble.ai.ChatMessage("user", title)

                    val response = provider.chat(
                        listOf(systemMsg, userMsg),
                        com.theblankstate.preamble.ai.TaskTools.tools
                    )

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
                                    
                                    if (existingTaskId != null) {
                                        updateExistingTask(app, existingTaskId, taskTitle, date, time, tags, priority, recurrence)
                                    } else {
                                        saveInterpretedTask(app, taskTitle, date, time, tags, priority, recurrence)
                                    }
                                }
                                "modify_task" -> {
                                    val targetTitle = call.arguments["target_title"] ?: title
                                    val newTitle = call.arguments["new_title"]
                                    val newDate = call.arguments["new_date"]
                                    val newTime = call.arguments["new_time"]
                                    val newPriority = call.arguments["new_priority"]?.toIntOrNull()
                                    val newTags = call.arguments["new_tags"]
                                    modifyInterpretedTask(app, targetTitle, newTitle, newDate, newTime, newPriority, newTags)
                                    // If there was an optimistic placeholder, remove it since we modified an existing task
                                    if (existingTaskId != null) {
                                        val placeholder = app.repository.getTaskById(existingTaskId)
                                        if (placeholder != null) app.repository.deleteTask(placeholder)
                                    }
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
                        // No tool calls — keep the raw task as-is, just clear syncing
                        if (existingTaskId != null) {
                            clearSyncing(app, existingTaskId)
                        } else {
                            saveInterpretedTask(app, title)
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            stopSelf()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VoiceTaskService", "AI processing failed, scheduling background retry", e)
                    if (existingTaskId != null) {
                        scheduleAiRetry(existingTaskId, title)
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
        recurrence: String? = null
    ) {
        val existing = app.repository.getTaskById(taskId) ?: return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly") }
        val updated = existing.copy(
            title = refinedTitle,
            createdDate = date ?: today,
            deadlineTime = time,
            tags = tags,
            priority = priority,
            recurrenceType = validRecurrence ?: existing.recurrenceType,
            isSyncing = false,
            updatedTimestamp = System.currentTimeMillis()
        )
        app.repository.updateTask(updated)
        if (tags != null) {
            app.repository.saveTagOverride(taskId, tags)
        }
    }

    /** Just flip isSyncing=false on a raw placeholder task */
    private suspend fun clearSyncing(app: PreambleApplication, taskId: String) {
        val existing = app.repository.getTaskById(taskId) ?: return
        app.repository.updateTask(existing.copy(isSyncing = false, updatedTimestamp = System.currentTimeMillis()))
    }

    private suspend fun modifyInterpretedTask(
        app: PreambleApplication,
        targetTitle: String,
        newTitle: String?,
        newDate: String?,
        newTime: String?,
        newPriority: Int?,
        newTags: String?
    ) {
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
        } else {
            // Fallback: If AI fails to find the task, just create a new one with the details to be safe
            saveInterpretedTask(app, newTitle ?: targetTitle, newDate, newTime, newTags, newPriority ?: 0)
        }
    }

    private suspend fun saveInterpretedTask(
        app: PreambleApplication,
        title: String,
        date: String? = null,
        time: String? = null,
        tags: String? = null,
        priority: Int = 0,
        recurrence: String? = null
    ) {
        var localTaskCreated = false
        val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly") }

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
                    recurrenceType = validRecurrence
                )
                app.repository.insertTask(localTask)
                if (tags != null) {
                    app.repository.saveTagOverride("gtask_$googleTaskId", tags)
                }
                localTaskCreated = true
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
                recurrenceType = validRecurrence
            )
            app.repository.insertTask(localTask)
            if (tags != null) {
                app.repository.saveTagOverride(localTask.id, tags)
            }
        }
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
    }
}
