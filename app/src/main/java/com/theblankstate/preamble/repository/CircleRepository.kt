package com.theblankstate.preamble.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.theblankstate.preamble.circles.CircleDocument
import com.theblankstate.preamble.circles.CircleDocumentResult
import com.theblankstate.preamble.circles.CircleMemberOps
import com.theblankstate.preamble.circles.CircleMemberRef
import com.theblankstate.preamble.circles.CircleTask
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Projection of a single Circle_Member's record from the canonical document's
 * `memberStates` map (Requirement 8.4).
 */
data class CircleMember(
    val uid: String = "",
    val name: String = "",
    val role: String = "",
    val status: String = ""
)

/**
 * UI projection of a canonical `/circles/{circleId}` document (Requirement 2.1).
 *
 * Mirrors how [WorkspaceRepository] projects `/collaborativeTasks` documents into
 * [com.theblankstate.preamble.data.Task]: the loosely-typed Firestore map is parsed
 * into a typed, immutable value the ViewModel/UI can render directly. [memberCount]
 * is derived from [memberUids] so the Circles list can show the member count.
 */
data class Circle(
    val id: String = "",
    val name: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    val members: List<CircleMember> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val memberCount: Int get() = memberUids.size
}

/** Projection of the Completer attribution on a Circle_Task (Requirements 11.2, 11.4). */
data class CircleCompleter(
    val uid: String = "",
    val name: String = ""
)

/**
 * UI projection of a top-level `/circleTasks/{taskId}` document (Requirement 10.1):
 * the shared title, the Shared_Completion state, the Completer attribution, and the
 * Circle_Author (`authorUid`). The denormalized `memberUidMap` is owned by the Cloud
 * Function and is not surfaced to the UI.
 */
data class CircleTaskModel(
    val id: String = "",
    val circleId: String = "",
    val authorUid: String = "",
    val title: String = "",
    val isCompleted: Boolean = false,
    val completedBy: CircleCompleter? = null,
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Firestore gateway for Shared Circles and their shared task lists.
 *
 * Deliberately parallels [WorkspaceRepository]: it talks to the named `"preamble"`
 * database, resolves the signed-in uid, exposes real-time snapshot-listener
 * [callbackFlow]s (membership-gated via `memberUidMap.{uid} == true`), and wraps
 * every write in [runCatching] returning a [Result]. All business rules live in the
 * pure `com.theblankstate.preamble.circles` package
 * ([CircleDocument] / [CircleMemberOps] / [CircleTask]); this gateway only collects
 * inputs, applies those pure transforms, and serializes the resulting
 * `Map<String, Any?>` payloads.
 */
class CircleRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(FIRESTORE_DATABASE_ID),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    // -------------------------------------------------------------------------------------------
    // Read flows (Requirements 2.1, 2.3, 10.1, 10.2, 10.4, 15.1)
    // -------------------------------------------------------------------------------------------

    /**
     * Observe every Circle the signed-in user belongs to.
     *
     * Listens on `/circles where memberUidMap.{uid} == true` and projects each canonical
     * document into a [Circle] (Requirements 2.1, 2.3). Firestore delivers snapshots in real
     * time, so under normal connectivity each membership/name change reaches this flow well
     * within the 5 s window. A listener error closes the flow so the collecting ViewModel can
     * `catch` it, retain its last-loaded list, and surface a message without crashing
     * (Requirement 15.1).
     */
    fun getCirclesFlow(): Flow<List<Circle>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(CIRCLES)
            .whereEqualTo(FieldPath.of(MEMBER_UID_MAP, uid), true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Circle listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val circles = snapshot?.documents.orEmpty().mapNotNull { documentToCircle(it) }
                trySend(circles.sortedByDescending(Circle::updatedAt))
            }

        awaitClose(listener::remove)
    }

    /**
     * Observe the shared task list of a single Circle, scoped to the open Circle_Detail_Screen
     * (Decision D5).
     *
     * Listens on `/circleTasks where circleId == id and memberUidMap.{uid} == true` and projects
     * each document into a [CircleTaskModel] (Requirements 10.1, 10.2). The denormalized
     * `memberUidMap` (kept current by the Cloud Function) lets the membership check run without a
     * parent-document lookup. A listener error closes the flow so the collecting ViewModel can
     * `catch` it, retain its last successfully synced copy, and surface a message
     * (Requirements 10.4, 15.1).
     */
    fun getCircleTasksFlow(circleId: String): Flow<List<CircleTaskModel>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(CIRCLE_TASKS)
            .whereEqualTo("circleId", circleId)
            .whereEqualTo(FieldPath.of(MEMBER_UID_MAP, uid), true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Circle task listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val tasks = snapshot?.documents.orEmpty().mapNotNull { documentToCircleTask(it) }
                trySend(tasks.sortedByDescending(CircleTaskModel::createdAt))
            }

        awaitClose(listener::remove)
    }

    // -------------------------------------------------------------------------------------------
    // Circle lifecycle writes (Requirements 1.3, 3.4, 4.2, 5.2, 6.2, 7.2)
    // -------------------------------------------------------------------------------------------

    /**
     * Create exactly one canonical Circle document recording the creator as the sole admin and
     * only active member (Requirement 1.3). [CircleDocument.build] enforces every invariant and
     * rejects an empty normalized name (Requirement 1.2). Returns the new Circle's id.
     */
    suspend fun createCircle(name: String): Result<String> = runCatching {
        val uid = requireCurrentUid()
        val reference = db.collection(CIRCLES).document()
        val now = System.currentTimeMillis()
        val document = when (
            val result = CircleDocument.build(
                circleId = reference.id,
                adminUid = uid,
                adminName = currentDisplayName(),
                name = name,
                now = now
            )
        ) {
            is CircleDocumentResult.Created -> result.document
            CircleDocumentResult.EmptyName -> error("A Circle name is required")
            CircleDocumentResult.TooManyMembers ->
                error(CircleMemberOps.REASON_CIRCLE_FULL)
        }
        reference.set(document).await()
        reference.id
    }.onFailure { Log.e(TAG, "Failed to create circle", it) }

    /**
     * Admin renames a Circle, setting only `name` + `updatedAt` via [CircleMemberOps.rename]
     * (Requirements 3.3, 3.4). The transform is applied inside a transaction so it runs against
     * the canonical document's current state; a rejected rename surfaces its reason.
     */
    suspend fun renameCircle(circleId: String, newName: String): Result<Unit> = runCatching<Unit> {
        requireCurrentUid()
        val reference = db.collection(CIRCLES).document(circleId)
        db.runTransaction { transaction ->
            val document = transaction.get(reference).data
                ?: throw IllegalStateException("Circle no longer exists")
            val now = System.currentTimeMillis()
            when (val result = CircleMemberOps.rename(document, newName, now)) {
                is CircleMemberOps.DocumentOpResult.Updated ->
                    transaction.set(reference, result.document)
                is CircleMemberOps.DocumentOpResult.Rejected ->
                    throw IllegalStateException(result.reason)
            }
        }.await()
    }.onFailure { Log.e(TAG, "Failed to rename circle", it) }

    /**
     * Admin adds an existing friend to a Circle via [CircleMemberOps.addMember], which appends
     * only the new member's slice and rejects duplicates / over-capacity (Requirements 4.2, 4.4,
     * 4.6, 8.6). The friendship gate is enforced before this call by the ViewModel (Decision D6).
     */
    suspend fun addMember(circleId: String, friend: Friend): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(friend.uid.isNotBlank() && friend.uid != uid) { "Invalid member" }
        val reference = db.collection(CIRCLES).document(circleId)
        db.runTransaction { transaction ->
            val document = transaction.get(reference).data
                ?: throw IllegalStateException("Circle no longer exists")
            val now = System.currentTimeMillis()
            val newMember = CircleMemberRef(uid = friend.uid, name = friend.name)
            when (val result = CircleMemberOps.addMember(document, newMember, now)) {
                is CircleMemberOps.DocumentOpResult.Updated ->
                    transaction.set(reference, result.document)
                is CircleMemberOps.DocumentOpResult.Rejected ->
                    throw IllegalStateException(result.reason)
            }
        }.await()
    }.onFailure { Log.e(TAG, "Failed to add member", it) }

    /**
     * Admin removes a non-admin member via [CircleMemberOps.removeMember], which removes only
     * that uid from `memberUids`/`memberUidMap` and sets only that member's status to `removed`;
     * removing the admin or a non-member is rejected (Requirements 5.2, 5.3, 8.6).
     */
    suspend fun removeMember(circleId: String, memberUid: String): Result<Unit> = runCatching<Unit> {
        requireCurrentUid()
        require(memberUid.isNotBlank()) { "Invalid member" }
        val reference = db.collection(CIRCLES).document(circleId)
        db.runTransaction { transaction ->
            val document = transaction.get(reference).data
                ?: throw IllegalStateException("Circle no longer exists")
            val now = System.currentTimeMillis()
            when (val result = CircleMemberOps.removeMember(document, memberUid, now)) {
                is CircleMemberOps.DocumentOpResult.Updated ->
                    transaction.set(reference, result.document)
                is CircleMemberOps.DocumentOpResult.Rejected ->
                    throw IllegalStateException(result.reason)
            }
        }.await()
    }.onFailure { Log.e(TAG, "Failed to remove member", it) }

    /**
     * Non-admin self-removal (Requirement 6.2). The pure [CircleMemberOps.leaveCircle] transform
     * validates the leave (rejecting an admin self-removal, Requirement 6.3) and computes the new
     * `memberUids`/`memberUidMap`; the write itself is the **own-slice** field-level update that
     * the `removesSelfFromCircleOnly()` rule accepts — touching only `memberUids`, `memberUidMap`,
     * the leaver's own `memberStates` status, and `updatedAt`, and never a full overwrite
     * (Requirement 8.6).
     */
    suspend fun leaveCircle(circleId: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        val reference = db.collection(CIRCLES).document(circleId)
        db.runTransaction { transaction ->
            val document = transaction.get(reference).data
                ?: throw IllegalStateException("Circle no longer exists")
            val now = System.currentTimeMillis()
            when (val result = CircleMemberOps.leaveCircle(document, uid, now)) {
                is CircleMemberOps.DocumentOpResult.Updated -> {
                    // Own-slice field-level update only (matches removesSelfFromCircleOnly).
                    transaction.update(
                        reference,
                        mapOf(
                            "memberUids" to result.document["memberUids"],
                            MEMBER_UID_MAP to result.document["memberUidMap"],
                            "memberStates.$uid.status" to "left",
                            "updatedAt" to now
                        )
                    )
                }
                is CircleMemberOps.DocumentOpResult.Rejected ->
                    throw IllegalStateException(result.reason)
            }
        }.await()
    }.onFailure { Log.e(TAG, "Failed to leave circle", it) }

    /**
     * Admin deletes a Circle, deleting only the canonical `/circles/{id}` document
     * (Requirement 7.2). The `onCirclesMembershipChanged` Cloud Function cascades the deletion to
     * every `/circleTasks` with this `circleId` (Decision D3), so the client never issues a large
     * batch or a `list` over another member's tasks.
     */
    suspend fun deleteCircle(circleId: String): Result<Unit> = runCatching<Unit> {
        requireCurrentUid()
        db.collection(CIRCLES).document(circleId).delete().await()
    }.onFailure { Log.e(TAG, "Failed to delete circle", it) }

    // -------------------------------------------------------------------------------------------
    // Circle_Task writes (Requirements 9.2, 11.2, 12.1, 12.3)
    // -------------------------------------------------------------------------------------------

    /**
     * Add a Circle_Task to a Circle's shared list (Requirement 9.2). Reads the parent Circle's
     * current `memberUidMap` to seed the denormalized copy on the task (Decision D2; the trigger
     * keeps it current thereafter), then builds the not-completed document via [CircleTask.build]
     * (which rejects an empty title, Requirement 9.4). Returns the new task's id.
     */
    suspend fun addCircleTask(circleId: String, title: String): Result<String> = runCatching {
        val uid = requireCurrentUid()
        val circleSnapshot = db.collection(CIRCLES).document(circleId).get().await()
        if (!circleSnapshot.exists()) error("Circle no longer exists")
        val memberUidMap = circleSnapshot.memberUidMapValue()
        // Read adminUid from the parent circle snapshot (already fetched above) so it can be
        // denormalized onto the task document, eliminating the get() call in isCircleAdminOf().
        val circleAdminUid = (circleSnapshot.data?.get("adminUid") as? String).orEmpty()

        val taskReference = db.collection(CIRCLE_TASKS).document()
        val now = System.currentTimeMillis()
        val document = when (
            val result = CircleTask.build(
                taskId = taskReference.id,
                circleId = circleId,
                adminUid = circleAdminUid,
                authorUid = uid,
                title = title,
                memberUidMap = memberUidMap,
                now = now
            )
        ) {
            is CircleTask.BuildResult.Created -> result.document
            CircleTask.BuildResult.EmptyTitle -> error("A task title is required")
        }
        taskReference.set(document).await()
        taskReference.id
    }.onFailure { Log.e(TAG, "Failed to add circle task", it) }

    /**
     * Shared completion (Decision D4, Requirements 11.2, 11.4). The pure [CircleTask.setCompletion]
     * transform records/clears the Completer for the whole Circle; the write is the completion-only
     * field-level update that the `updatesOnlyCompletionFields()` rule accepts (touching only
     * `isCompleted`, `completedBy`, `completedAt`, and `updatedAt`), so any member may flip it.
     */
    suspend fun setCircleTaskCompletion(taskId: String, completed: Boolean): Result<Unit> =
        runCatching<Unit> {
            val uid = requireCurrentUid()
            val actorName = currentDisplayName()
            val reference = db.collection(CIRCLE_TASKS).document(taskId)
            db.runTransaction { transaction ->
                val document = transaction.get(reference).data
                    ?: throw IllegalStateException("Shared task no longer exists")
                val now = System.currentTimeMillis()
                val updated = CircleTask.setCompletion(document, completed, uid, actorName, now)
                transaction.update(
                    reference,
                    mapOf(
                        "isCompleted" to updated["isCompleted"],
                        "completedBy" to updated["completedBy"],
                        "completedAt" to updated["completedAt"],
                        "updatedAt" to updated["updatedAt"]
                    )
                )
            }.await()
        }.onFailure { Log.e(TAG, "Failed to set circle task completion", it) }

    /**
     * Edit a Circle_Task's title (Requirement 12.1). Author/admin authorization is enforced by the
     * `authorOrAdminEdit()` rule (and gated in the ViewModel via [CircleTask.classifyEditDelete]);
     * this write touches only `title` + `updatedAt`, leaving `circleId`/`authorUid`/`memberUidMap`
     * unchanged as the rule requires.
     */
    suspend fun editCircleTaskTitle(taskId: String, newTitle: String): Result<Unit> =
        runCatching<Unit> {
            requireCurrentUid()
            val trimmedTitle = newTitle.trim()
            require(trimmedTitle.isNotEmpty()) { "A task title is required" }
            db.collection(CIRCLE_TASKS).document(taskId)
                .update(
                    mapOf(
                        "title" to trimmedTitle,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        }.onFailure { Log.e(TAG, "Failed to edit circle task title", it) }

    /**
     * Delete a Circle_Task (Requirement 12.3). Author/admin authorization is enforced by the
     * `/circleTasks` delete rule (and gated in the ViewModel via [CircleTask.classifyEditDelete]).
     */
    suspend fun deleteCircleTask(taskId: String): Result<Unit> = runCatching<Unit> {
        requireCurrentUid()
        db.collection(CIRCLE_TASKS).document(taskId).delete().await()
    }.onFailure { Log.e(TAG, "Failed to delete circle task", it) }

    // -------------------------------------------------------------------------------------------
    // Projection + helpers
    // -------------------------------------------------------------------------------------------

    private fun documentToCircle(document: DocumentSnapshot): Circle? {
        val data = document.data ?: return null
        val adminUid = data["adminUid"] as? String ?: return null
        val name = data["name"] as? String ?: return null
        val memberUids = (data["memberUids"] as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
        val memberStates = data["memberStates"] as? Map<*, *>
        val members = memberUids.mapNotNull { uid ->
            val entry = memberStates?.get(uid) as? Map<*, *> ?: return@mapNotNull null
            CircleMember(
                uid = uid,
                name = entry["name"] as? String ?: "",
                role = entry["role"] as? String ?: "",
                status = entry["status"] as? String ?: ""
            )
        }
        return Circle(
            id = document.id,
            name = name,
            adminUid = adminUid,
            memberUids = memberUids,
            members = members,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun documentToCircleTask(document: DocumentSnapshot): CircleTaskModel? {
        val data = document.data ?: return null
        val circleId = data["circleId"] as? String ?: return null
        val authorUid = data["authorUid"] as? String ?: return null
        val title = data["title"] as? String ?: return null
        val completedByRaw = data["completedBy"] as? Map<*, *>
        val completedBy = completedByRaw?.let {
            CircleCompleter(
                uid = it["uid"] as? String ?: "",
                name = it["name"] as? String ?: ""
            )
        }
        return CircleTaskModel(
            id = document.id,
            circleId = circleId,
            authorUid = authorUid,
            title = title,
            isCompleted = data["isCompleted"] as? Boolean ?: false,
            completedBy = completedBy,
            completedAt = (data["completedAt"] as? Number)?.toLong(),
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    /** Read the parent Circle's denormalized membership map to seed a new Circle_Task. */
    private fun DocumentSnapshot.memberUidMapValue(): Map<String, Boolean> {
        val raw = get(MEMBER_UID_MAP) as? Map<*, *> ?: return emptyMap()
        val result = LinkedHashMap<String, Boolean>(raw.size)
        for ((key, value) in raw) {
            if (key is String && value == true) result[key] = true
        }
        return result
    }

    private fun currentDisplayName(): String =
        auth.currentUser?.displayName?.trim()?.takeIf(String::isNotBlank) ?: DEFAULT_NAME

    private fun requireCurrentUid(): String =
        currentUid ?: throw IllegalStateException("Sign in to use Circles")

    private companion object {
        const val TAG = "CircleRepository"
        const val FIRESTORE_DATABASE_ID = "preamble"
        const val CIRCLES = "circles"
        const val CIRCLE_TASKS = "circleTasks"
        const val MEMBER_UID_MAP = "memberUidMap"
        const val DEFAULT_NAME = "Preamble user"
    }
}
