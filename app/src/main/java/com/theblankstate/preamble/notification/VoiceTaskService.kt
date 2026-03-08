package com.theblankstate.preamble.notification

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceTaskService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_NOT_STICKY
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
                    saveTask(spokenText)
                } else {
                    Toast.makeText(this@VoiceTaskService, "No speech detected", Toast.LENGTH_SHORT).show()
                }
                stopSelf()
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

    private fun saveTask(title: String) {
        val app = applicationContext as PreambleApplication
        val apiKey = com.theblankstate.preamble.BuildConfig.AI_API_KEY

        CoroutineScope(Dispatchers.IO).launch {
            if (apiKey.isNotBlank()) {
                try {
                    val providerName = com.theblankstate.preamble.BuildConfig.AI_PROVIDER
                    val provider: com.theblankstate.preamble.ai.AiProvider = when (
                        com.theblankstate.preamble.ai.AiProviderType.valueOf(providerName)
                    ) {
                        com.theblankstate.preamble.ai.AiProviderType.MISTRAL ->
                            com.theblankstate.preamble.ai.MistralProvider(apiKey)
                        com.theblankstate.preamble.ai.AiProviderType.OPENAI ->
                            com.theblankstate.preamble.ai.OpenAiProvider(apiKey)
                        com.theblankstate.preamble.ai.AiProviderType.GEMINI ->
                            com.theblankstate.preamble.ai.GeminiProvider(apiKey)
                        com.theblankstate.preamble.ai.AiProviderType.CLAUDE ->
                            com.theblankstate.preamble.ai.ClaudeProvider(apiKey)
                    }

                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(java.util.Date())
                    val systemMsg = com.theblankstate.preamble.ai.ChatMessage("system",
                        "You are Preamble AI. Today is $today. Interpret the voice command and use tools. " +
                        "IMPORTANT HINGLISH/HINDI RULES: " +
                        "1. 'aaj' = today ($today) " +
                        "2. 'kal' or 'cal' = tomorrow " +
                        "3. 'parso', 'parson', or 'perso' = day after tomorrow."
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
                                    app.repository.addTask(taskTitle, date, time)
                                }
                                else -> app.repository.addTask(title)
                            }
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@VoiceTaskService, "AI processed: $title", Toast.LENGTH_SHORT).show()
                            TaskNotificationManager.updateNotification(this@VoiceTaskService)
                        }
                    } else {
                        app.repository.addTask(title)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@VoiceTaskService, "Task saved: $title", Toast.LENGTH_SHORT).show()
                            TaskNotificationManager.updateNotification(this@VoiceTaskService)
                        }
                    }
                } catch (e: Exception) {
                    app.repository.addTask(title)
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(this@VoiceTaskService, "Task saved: $title", Toast.LENGTH_SHORT).show()
                        TaskNotificationManager.updateNotification(this@VoiceTaskService)
                    }
                }
            } else {
                app.repository.addTask(title)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@VoiceTaskService, "Task saved: $title", Toast.LENGTH_SHORT).show()
                    TaskNotificationManager.updateNotification(this@VoiceTaskService)
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
