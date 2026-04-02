package com.theblankstate.preamble.data

import com.theblankstate.preamble.data.Link
import com.theblankstate.preamble.data.Media
import com.theblankstate.preamble.data.Subtask

object TaskInputValidator {
    const val TITLE_MAX_LENGTH = 500
    const val DESCRIPTION_MAX_LENGTH = 5000
    const val TAG_MAX_LENGTH = 15
    const val MAX_TAGS_COUNT = 5
    const val TAGS_MAX_LENGTH = 100  // 5 tags × 15 chars + commas
    const val MAX_LINKS_COUNT = 10
    const val MAX_SUBTASKS_COUNT = 20
    const val MEDIA_MAX_SIZE_MB = 50

    fun normalizeTitle(raw: String): String = raw.trim()

    fun normalizeDescription(raw: String?): String? = raw?.trim()?.ifBlank { null }

    fun normalizeTags(raw: String?): String? = raw?.trim()?.ifBlank { null }

    /** Normalize a single tag: trim, lowercase, max 15 chars */
    fun normalizeTag(raw: String): String = raw.trim().take(TAG_MAX_LENGTH)

    fun isValidTitle(title: String): Boolean {
        return title.isNotBlank() && title.length <= TITLE_MAX_LENGTH
    }

    fun isValidDescription(description: String?): Boolean {
        return description == null || description.length <= DESCRIPTION_MAX_LENGTH
    }

    fun isValidTags(tags: String?): Boolean {
        if (tags == null) return true
        if (tags.length > TAGS_MAX_LENGTH) return false
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return tagList.size <= MAX_TAGS_COUNT && tagList.all { it.length <= TAG_MAX_LENGTH }
    }

    /** Check if a single tag is valid */
    fun isValidTag(tag: String): Boolean {
        return tag.isNotBlank() && tag.length <= TAG_MAX_LENGTH
    }

    fun isValidMedia(media: Media?): Boolean {
        if (media == null) return true
        return media.url.isNotBlank() &&
               media.type.matches(Regex("image/(jpeg|png|webp)|video/(mp4|quicktime|webm)")) &&
               (media.size == null || media.size <= MEDIA_MAX_SIZE_MB * 1024 * 1024)
    }

    fun isValidLinks(links: List<Link>): Boolean {
        return links.size <= MAX_LINKS_COUNT &&
               links.all { it.url.isNotBlank() && (it.title == null || it.title.length <= 200) }
    }

    fun isValidSubtasks(subtasks: List<Subtask>): Boolean {
        return subtasks.size <= MAX_SUBTASKS_COUNT &&
               subtasks.all { it.title.isNotBlank() && it.title.length <= TITLE_MAX_LENGTH }
    }
}
