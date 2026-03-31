package com.theblankstate.preamble.data

object TaskInputValidator {
    const val TITLE_MAX_LENGTH = 500
    const val DESCRIPTION_MAX_LENGTH = 5000
    const val TAG_MAX_LENGTH = 15
    const val MAX_TAGS_COUNT = 5
    const val TAGS_MAX_LENGTH = 100  // 5 tags × 15 chars + commas

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
}
