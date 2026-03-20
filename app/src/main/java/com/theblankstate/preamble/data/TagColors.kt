package com.theblankstate.preamble.data

import androidx.compose.ui.graphics.Color

data class TagDefinition(
    val name: String,
    val color: Color
)

object PredefinedTags {
    val tags = listOf(
        // Life & Daily
        TagDefinition("Personal", Color(0xFF9C27B0)),
        TagDefinition("Home", Color(0xFF795548)),
        TagDefinition("Family", Color(0xFFE91E63)),
        TagDefinition("Social", Color(0xFFFF5722)),
        // Work & Productivity
        TagDefinition("Work", Color(0xFF2196F3)),
        TagDefinition("Meeting", Color(0xFF3F51B5)),
        TagDefinition("Project", Color(0xFF1976D2)),
        TagDefinition("Study", Color(0xFF00BCD4)),
        // Health & Lifestyle
        TagDefinition("Health", Color(0xFF4CAF50)),
        TagDefinition("Fitness", Color(0xFF8BC34A)),
        TagDefinition("Food", Color(0xFFCDDC39)),
        // Finance & Errands
        TagDefinition("Finance", Color(0xFFFF9800)),
        TagDefinition("Shopping", Color(0xFFE040FB)),
        TagDefinition("Travel", Color(0xFF009688)),
        TagDefinition("Errand", Color(0xFF607D8B)),
        // Creative & Mental
        TagDefinition("Ideas", Color(0xFFFFEB3B)),
        TagDefinition("Creative", Color(0xFFFF7043)),
        // Priority markers
        TagDefinition("Urgent", Color(0xFFF44336)),
        TagDefinition("Important", Color(0xFFFF5252)),
        // System tags (not AI-assignable)
        TagDefinition("Google Calendar", Color(0xFF4285F4)),
        TagDefinition("Google Tasks", Color(0xFF34A853))
    )

    /** Tags the AI can assign — excludes system-only tags */
    val aiAssignableTags: List<TagDefinition>
        get() = tags.filter { it.name != "Google Calendar" && it.name != "Google Tasks" }

    /** Comma-separated names for AI prompt injection */
    val aiTagNames: String
        get() = aiAssignableTags.joinToString(", ") { it.name }

    fun colorForTag(name: String): Color {
        return tags.find { it.name.equals(name, ignoreCase = true) }?.color ?: Color.Gray
    }
}

