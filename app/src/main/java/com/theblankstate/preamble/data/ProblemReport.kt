package com.theblankstate.preamble.data

data class ProblemReportAttachment(
    val name: String,
    val contentType: String,
    val sizeBytes: Long,
    val storagePath: String,
)

data class ProblemReport(
    val id: String,
    val uid: String,
    val userEmail: String?,
    val userName: String?,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long?,
    val adminNote: String?,
    val attachments: List<ProblemReportAttachment>,
) {
    val isResolved: Boolean
        get() = status == STATUS_RESOLVED

    val statusLabel: String
        get() = when (status) {
            STATUS_OPEN -> "Open"
            STATUS_IN_PROGRESS -> "In review"
            STATUS_RESOLVED -> "Resolved"
            else -> status.replaceFirstChar { it.uppercase() }
        }

    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_RESOLVED = "resolved"

        fun fromMap(id: String, data: Map<String, Any?>): ProblemReport {
            val attachments = (data["attachments"] as? List<*>)
                ?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    ProblemReportAttachment(
                        name = map["name"] as? String ?: "Attachment",
                        contentType = map["contentType"] as? String ?: "application/octet-stream",
                        sizeBytes = (map["sizeBytes"] as? Number)?.toLong() ?: 0L,
                        storagePath = map["storagePath"] as? String ?: "",
                    )
                }
                .orEmpty()

            return ProblemReport(
                id = id,
                uid = data["uid"] as? String ?: "",
                userEmail = data["userEmail"] as? String,
                userName = data["userName"] as? String,
                title = data["title"] as? String ?: "Problem report",
                description = data["description"] as? String ?: "",
                status = data["status"] as? String ?: STATUS_OPEN,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
                resolvedAt = (data["resolvedAt"] as? Number)?.toLong(),
                adminNote = data["adminNote"] as? String,
                attachments = attachments,
            )
        }
    }
}
