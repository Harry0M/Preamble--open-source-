package com.theblankstate.preamble.circles

/**
 * Pure, side-effect-free logic for a single Circle_Task document stored top-level at
 * `/circleTasks/{taskId}` (Decision D2: the task carries a denormalized `memberUidMap`
 * copied from its parent Circle).
 *
 * This object deliberately parallels the rest of the `circles` package
 * ([CircleDocument], [CircleMemberOps]) and owns three concerns described in design.md:
 *
 *  - [build]             : constructs a not-completed Circle_Task from a trimmed,
 *    non-empty title (Requirements 9.2, 9.4).
 *  - [setCompletion]     : the SHARED (global) completion transform (Decision D4):
 *    completing records the Completer for the whole Circle, un-completing clears it
 *    (Requirements 11.2, 11.4).
 *  - [classifyEditDelete]: title-edit / delete authorization — author OR circle admin
 *    (Requirements 12.1–12.4).
 *
 * Like the rest of this package, the file is free of Android, Firestore, and
 * serialization dependencies so these transforms are fast/JVM-testable. The Firestore
 * gateway (`CircleRepository`) serializes the resulting `Map<String, Any?>` payload.
 *
 * The Circle_Task document shape:
 *
 * ```
 * {
 *   "circleId":     String,
 *   "adminUid":     String,                 // denormalized from parent /circles/{circleId}.adminUid
 *   "authorUid":    String,
 *   "title":        String,                 // trimmed, non-empty, <= MAX_TITLE_LEN
 *   "isCompleted":  Boolean,                // shared completion flag (Decision D4)
 *   "completedBy":  Map<String,Any?>?,      // { uid, name } when completed, else null
 *   "completedAt":  Long?,                  // UTC when completed, else null
 *   "memberUidMap": Map<String, Boolean>,   // denormalized copy of the parent circle's map
 *   "createdAt":    Long,                    // UTC
 *   "updatedAt":    Long                     // UTC
 * }
 * ```
 */
object CircleTask {

    /** Maximum length of a Circle_Task title (parallels the deployed rule schema). */
    const val MAX_TITLE_LEN = 500

    private const val DEFAULT_NAME = "Preamble user"

    /** Outcome of attempting to build a Circle_Task document. */
    sealed interface BuildResult {
        /** A valid Circle_Task document was produced. */
        data class Created(val document: Map<String, Any?>) : BuildResult

        /** The trimmed title is blank, so no Circle_Task is created (Requirement 9.4). */
        data object EmptyTitle : BuildResult
    }

    /** Authorization outcome for title-edit and delete (Requirements 12.1–12.4). */
    enum class EditDeleteDecision { ALLOW, DENY }

    /**
     * Builds a `/circleTasks/{taskId}` document recording [circleId], [authorUid], the
     * trimmed [title], a not-completed Shared_Completion state (`isCompleted=false`,
     * `completedBy=null`, `completedAt=null`), the denormalized [memberUidMap] copied
     * from the parent Circle, and `createdAt`/`updatedAt` (Requirement 9.2).
     *
     * @return [BuildResult.EmptyTitle] when the trimmed title is blank (Requirement 9.4),
     * otherwise [BuildResult.Created].
     */
    fun build(
        taskId: String,
        circleId: String,
        adminUid: String,
        authorUid: String,
        title: String,
        memberUidMap: Map<String, Boolean>,
        now: Long
    ): BuildResult {
        require(circleId.isNotBlank()) { "circleId is required" }
        require(adminUid.isNotBlank()) { "adminUid is required" }
        require(authorUid.isNotBlank()) { "authorUid is required" }

        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return BuildResult.EmptyTitle

        val document = mapOf(
            "taskId" to taskId,
            "circleId" to circleId,
            // Denormalized from parent /circles/{circleId}.adminUid — eliminates get() in
            // security rules, so isCircleAdminOf() can check resource.data.adminUid directly.
            "adminUid" to adminUid,
            "authorUid" to authorUid,
            "title" to trimmedTitle,
            "isCompleted" to false,
            "completedBy" to null,
            "completedAt" to null,
            // Denormalized copy of the parent circle's membership map (Decision D2).
            "memberUidMap" to LinkedHashMap(memberUidMap),
            "createdAt" to now,
            "updatedAt" to now
        )
        return BuildResult.Created(document)
    }

    /**
     * Shared-completion transform (Decision D4, Requirements 11.2, 11.4).
     *
     * When [completed] is `true`: sets `isCompleted=true`, records the acting member as
     * the Completer (`completedBy={uid,name}`), and sets `completedAt=now` for the WHOLE
     * Circle.
     *
     * When [completed] is `false`: sets `isCompleted=false` and clears the Completer
     * (`completedBy=null`, `completedAt=null`).
     *
     * In both cases `circleId`, `authorUid`, `title`, and the denormalized `memberUidMap`
     * are left unchanged; only the completion fields and `updatedAt` are touched.
     */
    fun setCompletion(
        document: Map<String, Any?>,
        completed: Boolean,
        actorUid: String,
        actorName: String,
        now: Long
    ): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>(document)
        if (completed) {
            result["isCompleted"] = true
            result["completedBy"] = mapOf(
                "uid" to actorUid,
                "name" to actorName.trim().ifBlank { DEFAULT_NAME }
            )
            result["completedAt"] = now
        } else {
            result["isCompleted"] = false
            result["completedBy"] = null
            result["completedAt"] = null
        }
        result["updatedAt"] = now
        return result
    }

    /**
     * Title-edit / delete authorization (Requirements 12.1–12.4): returns
     * [EditDeleteDecision.ALLOW] iff [requesterUid] is the Circle_Author of the task or
     * the Circle_Admin of the Circle, otherwise [EditDeleteDecision.DENY].
     */
    fun classifyEditDelete(
        taskAuthorUid: String,
        circleAdminUid: String,
        requesterUid: String
    ): EditDeleteDecision =
        if (requesterUid == taskAuthorUid || requesterUid == circleAdminUid) {
            EditDeleteDecision.ALLOW
        } else {
            EditDeleteDecision.DENY
        }
}
