package com.theblankstate.preamble.ai

import com.theblankstate.preamble.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive test suite testing all AI Chat and AI Task Parser functions,
 * prompt builders, intent recognition engines, rollover heuristics, subtask parsers,
 * and fuzzy matchers.
 */
class AiFullPipelineTest {

    // ═════════════════════════════════════════════════════════════════════════════
    // 1. CHAT PROMPT FACTORY & INTENT DETECTION
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun testChatPromptFactory_buildSystemPrompt_formatting() {
        val memory = "USER CONTEXT:\n- Name: Harry\n- Goal: Run a marathon"
        val taskContext = "TASK CONTEXT:\n- Gym @07:00 [P2]"

        val prompt = ChatPromptFactory.buildSystemPrompt(
            memoryBlock = memory,
            taskContextBlock = taskContext,
            conciseMode = true,
            taskToolsEnabled = true
        )

        assertTrue(prompt.contains("You are a helpful AI assistant."))
        assertTrue(prompt.contains("You have access to task management tools."))
        assertTrue(prompt.contains("USER CONTEXT:"))
        assertTrue(prompt.contains("Harry"))
        assertTrue(prompt.contains("TASK CONTEXT:"))
        assertTrue(prompt.contains("Gym @07:00"))
        assertTrue(prompt.contains("[SUGGEST:"))
    }

    @Test
    fun testChatPromptFactory_shouldUseTaskTools_exhaustiveMatrix() {
        // Direct task actions
        assertTrue(ChatPromptFactory.shouldUseTaskTools("add task buy milk"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("create task meeting at 3pm"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("set reminder for dentist tomorrow 10am"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("delete task doctor"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("mark done gym"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("complete task slides"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("gym ho gaya"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("kaam kar liya"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("kal shift kar do meeting"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("change the time to 5pm"))

        // Natural Hinglish task verbs + time/date
        assertTrue(ChatPromptFactory.shouldUseTaskTools("kal shaam ko doctor ke paas jana hai"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("aaj subah 7 baje gym jana hai"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("tomorrow morning buy groceries"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("tonight finish presentation"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("aaj raat ko medicine lana hai"))

        // Task queries / lists
        assertTrue(ChatPromptFactory.shouldUseTaskTools("what's on my plate today?"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("what should i focus on"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("list my tasks for tomorrow"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("aaj ke tasks dikhao"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("kal ke mere kya task the"))

        // Productivity & analytics range queries
        assertTrue(ChatPromptFactory.shouldUseTaskTools("how productive was I last week?"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("review my progress this month"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("pichle hafte kitne task complete hue"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("analyze my last 7 days"))

        // Pure conversational/knowledge (Should NOT enable task tools)
        assertFalse(ChatPromptFactory.shouldUseTaskTools("what is quantum computing?"))
        assertFalse(ChatPromptFactory.shouldUseTaskTools("who is the president of USA?"))
        assertFalse(ChatPromptFactory.shouldUseTaskTools("tell me a joke"))
        assertFalse(ChatPromptFactory.shouldUseTaskTools("explain how airplanes fly"))
        assertFalse(ChatPromptFactory.shouldUseTaskTools("kya hai black hole?"))
        assertFalse(ChatPromptFactory.shouldUseTaskTools("kaun hai Einstein?"))
    }

    @Test
    fun testChatPromptFactory_memoryExtractionSignals() {
        // Explicit memory intent
        assertTrue(ChatPromptFactory.hasExplicitMemoryIntent("remember that my sister's birthday is June 5"))
        assertTrue(ChatPromptFactory.hasExplicitMemoryIntent("save preference: I prefer dark theme"))
        assertTrue(ChatPromptFactory.hasExplicitMemoryIntent("my name is Rahul"))
        assertTrue(ChatPromptFactory.hasExplicitMemoryIntent("mera naam Amit hai yaad rakhna"))
        assertTrue(ChatPromptFactory.hasExplicitMemoryIntent("forget my old office address"))

        // General durable signals
        assertTrue(ChatPromptFactory.shouldAttemptMemoryExtraction("I work as a software engineer at Google"))
        assertTrue(ChatPromptFactory.shouldAttemptMemoryExtraction("I live in Bangalore near Indiranagar"))
        assertTrue(ChatPromptFactory.shouldAttemptMemoryExtraction("I love drinking black coffee without sugar"))
        assertTrue(ChatPromptFactory.shouldAttemptMemoryExtraction("my goal is to learn Kotlin Multiplatform"))

        // Non-durable / generic questions (Should NOT extract memory)
        assertFalse(ChatPromptFactory.shouldAttemptMemoryExtraction("what is the time now?"))
        assertFalse(ChatPromptFactory.shouldAttemptMemoryExtraction("tell me the weather"))
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // 2. AI TASK PARSER PROMPT FACTORY
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAiPromptFactory_buildSystemPrompt_intensities() {
        val promptLight = AiPromptFactory.buildSystemPrompt(subtaskIntensity = 1)
        assertTrue(promptLight.contains("RULE 10 — SMART TASK BREAKDOWN"))
        assertTrue(promptLight.contains("INTENSITY = LIGHT"))

        val promptBalanced = AiPromptFactory.buildSystemPrompt(subtaskIntensity = 2)
        assertTrue(promptBalanced.contains("INTENSITY = BALANCED"))

        val promptAggressive = AiPromptFactory.buildSystemPrompt(subtaskIntensity = 3)
        assertTrue(promptAggressive.contains("INTENSITY = AGGRESSIVE"))

        val promptOff = AiPromptFactory.buildSystemPrompt(subtaskIntensity = 0)
        assertFalse(promptOff.contains("INTENSITY ="))
    }

    @Test
    fun testAiPromptFactory_existingTasksContext_notificationEdit() {
        val tasks = (1..50).map { i ->
            Task(
                id = "task-$i",
                title = "Task number $i",
                createdDate = if (i <= 5) "2026-08-25" else "2026-08-20",
                priority = i % 4,
                isCompleted = false
            )
        }

        // Notification edit mode takes up to 40 tasks prioritized by today
        val promptNotif = AiPromptFactory.buildSystemPrompt(
            existingTasks = tasks,
            isNotificationEdit = true
        )
        assertTrue(promptNotif.contains("EXISTING TASKS (for modify/delete/complete):"))
        assertTrue(promptNotif.contains("Task number 1"))
        assertTrue(promptNotif.contains("Task number 40"))
        assertFalse(promptNotif.contains("Task number 45")) // Capped at 40

        // Regular mode takes up to 20 tasks
        val promptRegular = AiPromptFactory.buildSystemPrompt(
            existingTasks = tasks,
            isNotificationEdit = false
        )
        assertTrue(promptRegular.contains("Task number 20"))
        assertFalse(promptRegular.contains("Task number 25")) // Capped at 20
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // 3. TASK TOOLS: ROLLOVER, SUBTASKS, FUZZY MATCHING & VALIDATION
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun testTaskTools_rolloverDecisionTree() {
        val today = "2026-08-25"

        // 1. Future date -> false
        assertFalse(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Submit project",
                date = "2026-08-30",
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )

        // 2. Recurrence set -> false
        assertFalse(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Morning run",
                date = today,
                deadlineTime = null,
                recurrence = "daily",
                today = today
            )
        )

        // 3. Deadline time set -> false
        assertFalse(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Doctor appointment",
                date = today,
                deadlineTime = "17:00",
                recurrence = null,
                today = today
            )
        )

        // 4. Persistence intent (Overrides time keywords) -> true
        assertTrue(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Main tab tak scrap yard jaaunga jab tak silencer na mile",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )
        assertTrue(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Silencer milne tak dhundhte rehna",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )

        // 5. Time-bound event keywords -> false
        assertFalse(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Family ke saath dinner",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )
        assertFalse(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Movie show at PVR",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )

        // 6. Open-ended creative/work tasks -> true
        assertTrue(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Write blog post about Kotlin",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )
        assertTrue(
            TaskTools.decideRollover(
                rolloverArg = null,
                title = "Fix bug in login module",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )

        // 7. Explicit AI rollover argument overrides heuristic
        assertTrue(
            TaskTools.decideRollover(
                rolloverArg = "true",
                title = "Family dinner",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )
        assertFalse(
            TaskTools.decideRollover(
                rolloverArg = "false",
                title = "Fix bug in login",
                date = today,
                deadlineTime = null,
                recurrence = null,
                today = today
            )
        )
    }

    @Test
    fun testTaskTools_parseSubtasks_edgeCases() {
        // Empty or null
        assertEquals(emptyList<String>(), TaskTools.parseSubtasks(null))
        assertEquals(emptyList<String>(), TaskTools.parseSubtasks("   "))

        // Multi items with uneven spaces & duplicate removal
        val input = "  Buy vegetables , Buy milk,  Buy vegetables , Call plumber  "
        val expected = listOf("Buy vegetables", "Buy milk", "Call plumber")
        assertEquals(expected, TaskTools.parseSubtasks(input))
    }

    @Test
    fun testTaskTools_fuzzyMatching_multiLanguage() {
        val tasks = listOf(
            Task(id = "1", title = "Hospital Dr Mehta checkup", createdDate = "2026-08-25"),
            Task(id = "2", title = "Gym workout chest and triceps", createdDate = "2026-08-25"),
            Task(id = "3", title = "Trip packing tent and stove", createdDate = "2026-08-25"),
            Task(id = "4", title = "Optimistic temp task", createdDate = "2026-08-25", isSyncing = true)
        )

        // Exact & prefix matching
        assertEquals("1", TaskTools.findMatchingTask("hospital", tasks)?.id)
        assertEquals("1", TaskTools.findMatchingTask("dr mehta hospital", tasks)?.id)
        assertEquals("2", TaskTools.findMatchingTask("gym workout", tasks)?.id)
        assertEquals("3", TaskTools.findMatchingTask("packing for trip", tasks)?.id)

        // Must never match optimistic syncing tasks
        assertNull(TaskTools.findMatchingTask("Optimistic temp task", tasks))
    }

    @Test
    fun testTaskTools_schemaDeclarations() {
        val toolNames = TaskTools.tools.map { it.name }
        assertTrue(toolNames.contains("add_task"))
        assertTrue(toolNames.contains("modify_task"))
        assertTrue(toolNames.contains("delete_task"))
        assertTrue(toolNames.contains("complete_task"))
        assertTrue(toolNames.contains("list_tasks"))
        assertTrue(toolNames.contains("set_reminder"))
        assertTrue(toolNames.contains("duplicate_task"))
        assertTrue(toolNames.contains("get_tasks_range"))

        val addTask = TaskTools.tools.find { it.name == "add_task" }
        assertNotNull(addTask)
        val paramNames = addTask!!.parameters.map { it.name }
        assertTrue(paramNames.contains("title"))
        assertTrue(paramNames.contains("tags"))
        assertTrue(paramNames.contains("priority"))
        assertTrue(paramNames.contains("rollover"))
        assertTrue(paramNames.contains("subtasks"))
        assertTrue(paramNames.contains("is_habit"))
        assertTrue(paramNames.contains("is_event"))
    }
}
