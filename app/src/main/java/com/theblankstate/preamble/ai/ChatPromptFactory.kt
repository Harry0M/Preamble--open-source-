package com.theblankstate.preamble.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight prompt factory for the chat screen only.
 *
 * Task parsing continues to use AiPromptFactory. This keeps normal chat natural
 * and token-cheap while preserving the richer parser behavior elsewhere.
 */
object ChatPromptFactory {

    fun buildSystemPrompt(
        memoryBlock: String? = null,
        taskContextBlock: String? = null,
        conciseMode: Boolean = true,
        taskToolsEnabled: Boolean = false,
    ): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        return buildString {
            appendLine("You are a helpful AI assistant. Today is $today, current time is $currentTime.")

            if (taskToolsEnabled) {
                appendLine("You have access to task management tools. Use them only when the user explicitly asks to add, list, modify, delete, complete, or schedule tasks and reminders.")
                appendLine("When adding tasks: keep titles concise, dates as YYYY-MM-DD, times as HH:mm.")
            }

            appendLine("If a concrete task would genuinely help (goal/habit/event mentioned), append up to 2 suggestions AFTER your reply—exact format: [SUGGEST:{\"title\":\"…\",\"tags\":\"Work|Personal|Health|Fitness|Finance|Study|Shopping|Social|Home|Travel\",\"priority\":\"1|2|3\",\"description\":\"…\"}] — omit otherwise.")

            if (!memoryBlock.isNullOrBlank()) {
                appendLine()
                appendLine(memoryBlock.trim().take(900))
            }
            if (!taskContextBlock.isNullOrBlank()) {
                appendLine()
                appendLine(taskContextBlock.trim().take(900))
            }
        }.trimEnd()
    }

    fun shouldUseTaskTools(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return false

        val conversationalPatterns = listOf(
            "what is", "what's", "who is", "how many", "tell me",
            "explain", "describe", "define", "meaning of",
            "kya hai", "kaun hai", "kitne",
        )
        val taskDomainWords = listOf(
            "task", "tasks", "todo", "to-do", "reminder", "reminders",
            "schedule", "deadline", "due", "focus", "plate", "done",
        )
        if (conversationalPatterns.any { it in lower } && taskDomainWords.none { it in lower }) {
            return false
        }

        val directSignals = listOf(
            "add task", "create task", "new task", "make a task",
            "add reminder", "set reminder", "remind me",
            "delete task", "remove task", "cancel task",
            "mark done", "mark complete", "complete task",
            "move my", "shift my", "reschedule", "change the time",
            "list my tasks", "show my tasks", "today's tasks", "todays tasks",
            "my todo", "my to-do", "what's on my plate", "what is on my plate",
            "what should i focus", "plan my day", "got done", "completed this week",
            "task banana", "task banao", "kaam add", "yaad dilana",
            "hata do", "delete karo", "complete kar", "done mark",
            "shift karo", "badal do", "kal shift", "aaj ke tasks",
        )
        if (directSignals.any { it in lower }) return true
        if (Regex("\\b(tasks?|todos?|to-dos?|reminders?)\\b").containsMatchIn(lower)) return true

        val dateOrTime =
            Regex("\\b(today|tomorrow|tonight|morning|afternoon|evening|night|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b").containsMatchIn(lower) ||
                Regex("\\b\\d{1,2}(:\\d{2})?\\s?(am|pm)\\b").containsMatchIn(lower) ||
                Regex("\\b(aaj|kal|parso|subah|dopahar|shaam|raat)\\b").containsMatchIn(lower)

        val taskVerb =
            Regex("\\b(buy|call|email|finish|submit|pay|pack|clean|visit|meet|gym|doctor|hospital|appointment|meeting)\\b").containsMatchIn(lower) ||
                Regex("\\b(karna hai|jana hai|jaana hai|laana hai|lana hai|banana hai|bhejna hai)\\b").containsMatchIn(lower)

        return dateOrTime && taskVerb
    }

    fun shouldAttemptMemoryExtraction(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.length < 12) return false

        if (Regex("\\b(forget|remove|delete|don't remember|do not remember|stop remembering)\\b").containsMatchIn(lower) ||
            Regex("\\b(yaad mat rakh|bhool ja|bhul ja|delete kar|hata do)\\b").containsMatchIn(lower)
        ) {
            return true
        }

        val knowledgeQuestion =
            Regex("^(what|who|when|where|why|how|explain|define|describe|tell me)\\b").containsMatchIn(lower) ||
                Regex("^(kya|kaun|kab|kahan|kyu|kyon|kaise)\\b").containsMatchIn(lower)
        if (knowledgeQuestion && !Regex("\\b(i am|i'm|my|me|mera|mujhe|main|mein)\\b").containsMatchIn(lower)) {
            return false
        }

        val durableSignals = listOf(
            "my name is", "i am", "i'm", "i work", "i live", "i study",
            "i like", "i love", "i hate", "i prefer", "my goal", "my dream",
            "my plan is to", "i want to become", "i'm trying to", "i am trying to",
            "i usually", "i always", "i mostly", "i often", "i never",
            "my timezone", "my city", "my job", "my college", "my school",
            "my wife", "my husband", "my sister", "my brother", "my friend",
            "my father", "my mother", "my son", "my daughter",
            "mera naam", "meri naam", "main ", "mein ", "mujhe pasand", "meri goal",
            "mera goal", "main chahta", "main chahti", "main padh", "main kaam",
        )
        return durableSignals.any { it in lower }
    }
}
