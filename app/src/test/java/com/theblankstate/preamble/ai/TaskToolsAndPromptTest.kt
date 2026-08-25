package com.theblankstate.preamble.ai

import com.theblankstate.preamble.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskToolsAndPromptTest {

    @Test
    fun testShouldUseTaskTools_intentRecognition() {
        // Task queries (Hinglish and English)
        assertTrue(ChatPromptFactory.shouldUseTaskTools("kal ke mere kya task the"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("aaj ke tasks batao"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("what are my tasks today"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("show my tasks"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("what's on my plate"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("gym karna hai kal"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("doctor appointment at 5pm"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("delete hospital task"))
        assertTrue(ChatPromptFactory.shouldUseTaskTools("gym ho gaya"))

        // Pure conversational/knowledge queries (should NOT enable task tools)
        assertFalse(ChatPromptFactory.shouldUseTaskTools("what is the capital of France?"))
        assertFalse(ChatPromptFactory.shouldUseTaskTools("explain photosynthesis"))
        assertFalse(ChatPromptFactory.shouldUseTaskTools("kya hai ye machine learning?"))
    }

    @Test
    fun testDecideRollover_logic() {
        val today = "2026-08-25"

        // Open-ended today task -> rollover = true
        val openEnded = TaskTools.decideRollover(
            rolloverArg = null,
            title = "Gym karna",
            date = today,
            deadlineTime = null,
            recurrence = null,
            today = today
        )
        assertTrue(openEnded)

        // Future date task -> rollover = false
        val futureTask = TaskTools.decideRollover(
            rolloverArg = null,
            title = "Gym karna",
            date = "2026-08-26",
            deadlineTime = null,
            recurrence = null,
            today = today
        )
        assertFalse(futureTask)

        // Time bound event -> rollover = false
        val timeBound = TaskTools.decideRollover(
            rolloverArg = null,
            title = "Dinner with team",
            date = today,
            deadlineTime = "20:00",
            recurrence = null,
            today = today
        )
        assertFalse(timeBound)
    }

    @Test
    fun testFindMatchingTask_fuzzyMatching() {
        val tasks = listOf(
            Task(id = "1", title = "Hospital jaana Dr Sharma", createdDate = "2026-08-25"),
            Task(id = "2", title = "Gym workout chest day", createdDate = "2026-08-25"),
            Task(id = "3", title = "Bazaar se doodh aur fal laana", createdDate = "2026-08-25")
        )

        // Exact & partial matches
        val match1 = TaskTools.findMatchingTask("hospital", tasks)
        assertNotNull(match1)
        assertEquals("1", match1?.id)

        val match2 = TaskTools.findMatchingTask("gym", tasks)
        assertNotNull(match2)
        assertEquals("2", match2?.id)

        val match3 = TaskTools.findMatchingTask("doodh laana", tasks)
        assertNotNull(match3)
        assertEquals("3", match3?.id)
    }

    @Test
    fun testParseSubtasks() {
        val raw = "Milk, Bread, Eggs , Butter"
        val parsed = TaskTools.parseSubtasks(raw)
        assertEquals(listOf("Milk", "Bread", "Eggs", "Butter"), parsed)
    }
}
