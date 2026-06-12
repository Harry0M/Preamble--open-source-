package com.theblankstate.preamble.data

import androidx.compose.ui.graphics.Color

/**
 * Tag for admin-pushed tasks. Admin defines custom tags with colors.
 */
data class AdminTaskTag(
    val name: String,
    val color: String // Hex color e.g. "#FF5722"
) {
    fun toComposeColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(color))
        } catch (_: Exception) {
            Color.Gray
        }
    }
}

/**
 * A broadcast task pushed by the admin to all users via Firestore.
 * Completely separate from the app's Task system — no Room storage,
 * no sync with user tasks, no subtasks, no deadlines.
 *
 * Types:
 * - announcement: General info (new version, changelog)
 * - promotion: Offers, premium upsell
 * - social: Follow on socials, review prompt
 * - feature: In-app feature discovery (try theming, link calendar, etc.)
 */
data class AdminTask(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val imageUrl: String? = null,
    val actionUrl: String? = null,         // External URL (opens browser)
    val deepLink: String? = null,          // In-app deep link (preamble://settings/theme)
    val actionLabel: String = "Open Now",  // Button text
    val type: String = "announcement",     // announcement, feature, promotion, social
    val tags: List<AdminTaskTag> = emptyList(),
    val directRedirect: Boolean = false,   // True = tap opens link directly, no detail sheet
    val priority: Int = 0,                 // Higher = shown first
    val createdAt: Long = 0L,
    val expiresAt: Long? = null,           // Optional expiry timestamp
    val active: Boolean = true,
    // Feature discovery fields
    val featureKey: String? = null,        // e.g. "theme_custom", "calendar_link"
    val dismissible: Boolean = true,       // Whether user can close/dismiss
    val targetType: String = "all",        // all, single, group
    val targetUids: List<String>? = null
) {
    companion object {
        // ── Feature keys (used by admin to target specific features) ──
        const val FEATURE_THEME_CUSTOM = "theme_custom"
        const val FEATURE_CALENDAR_LINK = "calendar_link"
        const val FEATURE_VOICE_SYNC = "voice_sync"
        const val FEATURE_SMART_BREAKDOWN = "smart_breakdown"
        const val FEATURE_PERMANENT_NOTIFICATION = "permanent_notification"

        /**
         * Parse a Firestore document map into an AdminTask.
         */
        fun fromMap(id: String, map: Map<String, Any?>): AdminTask {
            val tagsList = (map["tags"] as? List<*>)?.mapNotNull { tagMap ->
                val m = tagMap as? Map<*, *> ?: return@mapNotNull null
                AdminTaskTag(
                    name = m["name"] as? String ?: return@mapNotNull null,
                    color = m["color"] as? String ?: "#9E9E9E"
                )
            } ?: emptyList()

            return AdminTask(
                id = id,
                title = map["title"] as? String ?: "",
                description = map["description"] as? String,
                imageUrl = map["imageUrl"] as? String,
                actionUrl = map["actionUrl"] as? String,
                deepLink = map["deepLink"] as? String,
                actionLabel = map["actionLabel"] as? String ?: "Open Now",
                type = map["type"] as? String ?: "announcement",
                tags = tagsList,
                directRedirect = map["directRedirect"] as? Boolean ?: false,
                priority = (map["priority"] as? Number)?.toInt() ?: 0,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                expiresAt = (map["expiresAt"] as? Number)?.toLong(),
                active = map["active"] as? Boolean ?: true,
                featureKey = map["featureKey"] as? String,
                dismissible = map["dismissible"] as? Boolean ?: true,
                targetType = map["targetType"] as? String ?: "all",
                targetUids = (map["targetUids"] as? List<*>)?.mapNotNull { it as? String }
            )
        }
    }
}
