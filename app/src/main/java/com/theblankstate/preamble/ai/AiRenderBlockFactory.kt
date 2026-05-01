package com.theblankstate.preamble.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Builds a provider-agnostic UI contract for assistant messages.
 *
 * Gemini/Mistral can keep streaming plain text and tool calls today. This factory
 * converts those durable facts into typed render blocks that the UI can display
 * without guessing from prose. Future providers can write the same JSON contract
 * directly and bypass most of this derivation.
 */
object AiRenderBlockFactory {
    private val gson = Gson()

    fun shouldPreferLocalBlocks(content: String, remoteBlocksJson: String?): Boolean {
        if (!looksLikeCode(content)) return false
        return remoteBlocksJson
            ?.contains("\"type\":\"code\"", ignoreCase = true) != true
    }

    fun build(
        content: String,
        toolCallsJson: String? = null,
        toolResultsJson: String? = null,
    ): String? {
        val blocks = mutableListOf<Map<String, Any>>()
        val toolResults = parseArray(toolResultsJson)
        val toolCalls = parseArray(toolCallsJson)

        toolResults.forEach { result ->
            blockForToolResult(result)?.let { blocks += it }
        }

        toolCalls
            .filter { it.string("name") == "suggest_task" }
            .mapNotNull { suggestionBlock(it) }
            .forEach { blocks += it }

        val suppressText = toolResults.any { it.string("name") == "list_tasks" }
        if (content.isNotBlank() && !suppressText) {
            blocks += contentBlock(content.trim())
        }

        if (blocks.isEmpty()) return null
        return gson.toJson(mapOf("version" to 1, "blocks" to blocks))
    }

    private fun blockForToolResult(result: JsonObject): Map<String, Any>? {
        val name = result.string("name")
        val args = result.obj("args")
        val output = result.string("result")
        val title = args?.string("title")
            ?: args?.string("target_title")
            ?: quotedTitle(output)
            ?: output.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return null

        return when (name) {
            "add_task" -> mapOf(
                "type" to "task_created",
                "title" to title,
                "subtitle" to buildTaskSubtitle(args, "Added"),
                "status" to if (output.startsWith("Error:", true)) "error" else "success",
                "items" to metadataItems(args),
            )
            "set_reminder" -> mapOf(
                "type" to "reminder_set",
                "title" to title,
                "subtitle" to buildTaskSubtitle(args, "Reminder set"),
                "status" to if (output.startsWith("Error:", true)) "error" else "success",
                "items" to metadataItems(args),
            )
            "modify_task" -> mapOf(
                "type" to "task_updated",
                "title" to title,
                "subtitle" to "Task updated",
                "status" to if (output.startsWith("No task", true) || output.startsWith("Error:", true)) "warning" else "success",
                "items" to metadataItems(args),
            )
            "complete_task" -> mapOf(
                "type" to "task_completed",
                "title" to title,
                "subtitle" to output.ifBlank { "Marked complete" },
                "status" to if (output.startsWith("No task", true) || output.startsWith("Error:", true)) "warning" else "success",
            )
            "delete_task" -> mapOf(
                "type" to "task_deleted",
                "title" to title,
                "subtitle" to output.ifBlank { "Task deleted" },
                "status" to if (output.startsWith("No task", true) || output.startsWith("Error:", true)) "warning" else "success",
            )
            "list_tasks" -> taskListBlock(output)
            else -> mapOf(
                "type" to "tool_result",
                "title" to toolTitle(name),
                "subtitle" to output.take(160),
                "status" to if (output.startsWith("Error:", true)) "error" else "success",
            )
        }
    }

    private fun contentBlock(text: String): Map<String, Any> {
        if (looksLikeCode(text)) {
            return mapOf(
                "type" to "code",
                "title" to codeTitle(text),
                "markdown" to text,
            )
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val numbered = lines.mapNotNull { Regex("""^\d+[\.)]\s+(.+)$""").find(it)?.groupValues?.get(1)?.trim() }
        if (numbered.size >= 2) {
            return mapOf(
                "type" to "steps",
                "title" to "Steps",
                "items" to numbered.map { mapOf("title" to it) },
                "markdown" to text,
            )
        }

        return mapOf("type" to "answer", "markdown" to text)
    }

    private fun looksLikeCode(text: String): Boolean {
        if (Regex("""(?s)```[A-Za-z0-9_+\-.#]*\s*\n.*?```""").containsMatchIn(text)) return true
        val lines = text.lines().filter { it.isNotBlank() }
        val codeLines = lines.count { raw ->
            val line = raw.trim()
            Regex("""^(def|class|import|from|print\(|if __name__|for\s+.+\s+in\s+.+:|while\s+.+:|try:|except\b|return\b|async\s+def|const|let|var|function|export|interface|typealias|fun|val|public|private)\b""")
                .containsMatchIn(line) ||
                Regex("""^[A-Za-z_][A-Za-z0-9_]*\s*=\s*.+""").containsMatchIn(line) ||
                Regex("""^["'][^"']+["']\s*:\s*.+""").containsMatchIn(line) ||
                line in setOf("{", "}", "[", "]") ||
                line.startsWith("//")
        }
        return codeLines >= 2
    }

    private fun codeTitle(text: String): String {
        val fence = Regex("""```([A-Za-z0-9_+\-.#]+)""").find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
        return fence?.replaceFirstChar { it.uppercase() } ?: "Code"
    }

    private fun taskListBlock(output: String): Map<String, Any> {
        val date = Regex("""Tasks for ([0-9]{4}-[0-9]{2}-[0-9]{2})""").find(output)?.groupValues?.get(1)
        val groups = mutableListOf<Map<String, Any>>()
        var currentTitle = "Tasks"
        val currentItems = mutableListOf<Map<String, Any>>()

        fun flush() {
            if (currentItems.isNotEmpty()) {
                groups += mapOf("title" to currentTitle, "items" to currentItems.toList())
                currentItems.clear()
            }
        }

        output.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.equals("Pending:", true) -> {
                    flush()
                    currentTitle = "Pending"
                }
                line.equals("Done:", true) -> {
                    flush()
                    currentTitle = "Done"
                }
                Regex("""^\d+\.\s+[○✓]""").containsMatchIn(line) -> {
                    val done = line.contains("✓")
                    val cleaned = line
                        .replace(Regex("""^\d+\.\s+[○✓]\s*"""), "")
                        .trim()
                    currentItems += mapOf(
                        "title" to cleaned,
                        "status" to if (done) "done" else "pending",
                    )
                }
            }
        }
        flush()

        return mapOf(
            "type" to "task_list",
            "title" to (date?.let { "Tasks for $it" } ?: "Tasks"),
            "groups" to groups,
            "markdown" to output,
        )
    }

    private fun suggestionBlock(call: JsonObject): Map<String, Any>? {
        val args = call.obj("args") ?: return null
        val title = args.string("title").takeIf { it.isNotBlank() } ?: return null
        return mapOf(
            "type" to "suggested_task",
            "title" to title,
            "subtitle" to args.string("description"),
            "args" to args.entrySet().associate { it.key to it.value.asText() },
        )
    }

    private fun metadataItems(args: JsonObject?): List<Map<String, String>> {
        if (args == null) return emptyList()
        val fields = listOf(
            "date" to "Date",
            "deadline_time" to "Time",
            "time" to "Time",
            "priority" to "Priority",
            "tags" to "Tags",
            "recurrence" to "Repeats",
            "rollover" to "Rollover",
            "subtasks" to "Subtasks",
        )
        return fields.mapNotNull { (key, label) ->
            val value = args.string(key).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            mapOf("label" to label, "value" to value)
        }
    }

    private fun buildTaskSubtitle(args: JsonObject?, prefix: String): String {
        if (args == null) return prefix
        val pieces = listOfNotNull(
            args.string("date").takeIf { it.isNotBlank() }?.let { "for $it" },
            (args.string("deadline_time").takeIf { it.isNotBlank() }
                ?: args.string("time").takeIf { it.isNotBlank() })?.let { "at $it" },
            args.string("tags").takeIf { it.isNotBlank() }?.let { "tagged $it" },
        )
        return if (pieces.isEmpty()) prefix else "$prefix ${pieces.joinToString(" ")}"
    }

    private fun parseArray(json: String?): List<JsonObject> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            JsonParser.parseString(json).asJsonArray
                .mapNotNull { it.takeIf { el -> el.isJsonObject }?.asJsonObject }
        }.getOrDefault(emptyList())
    }

    private fun quotedTitle(text: String): String? =
        Regex("\"([^\"]+)\"").find(text)?.groupValues?.get(1)

    private fun toolTitle(name: String): String =
        name.replace("_", " ").replaceFirstChar { it.uppercase() }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(key: String): String =
        get(key)?.asText().orEmpty()

    private fun com.google.gson.JsonElement.asText(): String =
        when {
            isJsonNull -> ""
            isJsonPrimitive -> asString
            else -> toString()
        }
}
