package com.theblankstate.preamble.data

import androidx.compose.ui.graphics.Color

data class TagDefinition(
    val name: String,
    val color: Color
)

object PredefinedTags {
    val tags = listOf(
        TagDefinition("Work", Color(0xFF2196F3)),
        TagDefinition("Personal", Color(0xFF9C27B0)),
        TagDefinition("Health", Color(0xFF4CAF50)),
        TagDefinition("Finance", Color(0xFFFF9800)),
        TagDefinition("Shopping", Color(0xFFE91E63)),
        TagDefinition("Study", Color(0xFF00BCD4)),
        TagDefinition("Home", Color(0xFF795548)),
        TagDefinition("Social", Color(0xFFFF5722)),
        TagDefinition("Urgent", Color(0xFFF44336)),
        TagDefinition("Ideas", Color(0xFFFFEB3B)),
        TagDefinition("Google Calendar", Color(0xFF4285F4)),
        TagDefinition("Google Tasks", Color(0xFF34A853))
    )

    fun colorForTag(name: String): Color {
        return tags.find { it.name.equals(name, ignoreCase = true) }?.color ?: Color.Gray
    }
}
