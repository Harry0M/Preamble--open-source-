package com.theblankstate.preamble.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.theblankstate.preamble.collab.AssigneeRef
import com.theblankstate.preamble.collab.CollaborativeDocument
import com.theblankstate.preamble.collab.CollaborativeDocumentResult
import com.theblankstate.preamble.collab.CollaborativeMemberOps
import com.theblankstate.preamble.collab.MemberStatusTransitions
import com.theblankstate.preamble.collab.InviteValidation
import com.theblankstate.preamble.collab.InviteValidator
import com.theblankstate.preamble.collab.PreambleId
import com.theblankstate.preamble.collab.TaskProjection
import com.theblankstate.preamble.data.CollabAssigneeStatus
import com.theblankstate.preamble.data.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class WorkspaceInvite(
    val id: String = "",
    val senderUid: String = "",
    val targetUid: String = "",
    val senderName: String = "",
    val senderPreambleId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class Friend(
    val uid: String = "",
    val name: String = "",
    val preambleId: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val productivityPoints: Int = 0
)

/**
 * Firestore gateway for friendships and collaborative tasks.
 *
 * A collaborative task has exactly one canonical document at
 * /collaborativeTasks/{taskId}. Room remains the local source of truth for the UI,
 * but Firestore is no longer populated with independently writable per-user copies.
 */
class WorkspaceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(FIRESTORE_DATABASE_ID),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    suspend fun resolvePreambleId(targetPreambleId: String): Map<String, Any>? {
        val normalizedId = targetPreambleId.trim().uppercase()
        if (normalizedId.isBlank()) return null
        return try {
            db.collection(PREAMBLE_IDS).document(normalizedId).get().await()
                .takeIf(DocumentSnapshot::exists)?.data
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to resolve Preamble ID", exception)
            null
        }
    }

    /**
     * Send a Friend_Request, enforcing every Requirement 1 gate before any
     * Firestore write occurs.
     *
     * The pure [InviteValidator] is the single source of truth for the
     * empty (1.2), self (1.5), already-friends (1.6), and already-pending (1.7)
     * checks; this gateway only collects the inputs (normalizing through
     * [PreambleId], reading the friend set and the pending-invite set, and
     * performing the directory lookup for NotFound, 1.4). The Friend_Request is
     * constructed by [InviteValidator.buildInvite] so it always carries the
     * sender's uid, display name, and normalized Preamble_ID (1.8). Exactly one
     * Friend_Request is written under the target user's account (1.3).
     */
    suspend fun sendInvite(
        targetPreambleId: String,
        senderName: String,
        senderPreambleId: String
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        val normalizedId = PreambleId.normalize(targetPreambleId)

        // Requirement 1.2: reject an empty/whitespace-only id without performing
        // a directory lookup or creating a Friend_Request.
        if (PreambleId.isBlank(normalizedId)) error("A Preamble ID is required")

        // Collect the friend set so the already-friends gate (1.6) is decided
        // by pure logic over normalized Preamble_IDs.
        val friends = getFriendsOnce()
        val friendPreambleIds = friends.mapNotNull { it.preambleId.takeIf(String::isNotBlank) }.toSet()

        // Pure validation for empty (1.2), self (1.5), and already-friends (1.6).
        // Already-pending (1.7) requires the resolved target uid, so it is
        // checked below once the directory lookup succeeds.
        when (
            InviteValidator.validate(
                submittedPreambleId = normalizedId,
                ownPreambleId = senderPreambleId,
                friendPreambleIds = friendPreambleIds,
                pendingPreambleIds = emptySet()
            )
        ) {
            InviteValidation.EmptyId -> error("A Preamble ID is required")
            InviteValidation.SelfInvite -> error("You cannot invite yourself")
            InviteValidation.AlreadyFriends -> error("You are already friends")
            // Unreachable here (pending is checked below) but kept exhaustive.
            InviteValidation.AlreadyPending -> error("A request to that user is already pending")
            // Decided by the directory lookup that follows.
            InviteValidation.NotFound -> Unit
            InviteValidation.Ok -> Unit
        }

        // Requirement 1.4: directory lookup runs only after the non-empty gate.
        val targetData = resolvePreambleId(normalizedId)
            ?: error("No user exists with that Preamble ID")
        val targetUid = targetData["uid"] as? String
            ?: error("The target user's directory entry is invalid")

        // Backstop the self (1.5) and already-friends (1.6) gates by uid, in case
        // the directory Preamble_ID differs from the submitter's stored value.
        require(targetUid != uid) { "You cannot invite yourself" }
        val existingFriend = db.collection(USERS).document(uid)
            .collection(FRIENDS).document(targetUid).get().await()
        require(!existingFriend.exists()) { "You are already friends" }

        // Requirement 1.7: the invite is keyed by the sender's uid under the
        // target's account, so a duplicate pending request is a deterministic
        // existence check.
        val existingInvite = db.collection(USERS).document(targetUid)
            .collection(INVITES).document(uid).get().await()
        require(!existingInvite.exists()) { "A request to that user is already pending" }

        // All gates passed: construct and write exactly one Friend_Request (1.3, 1.8).
        val invite = InviteValidator.buildInvite(
            senderUid = uid,
            targetUid = targetUid,
            senderName = senderName,
            senderPreambleId = senderPreambleId
        )
        db.collection(USERS).document(targetUid)
            .collection(INVITES).document(uid)
            .set(invite)
            .await()
    }.onFailure { Log.e(TAG, "Failed to send invite", it) }

    suspend fun acceptInvite(
        invite: WorkspaceInvite,
        myName: String,
        myPreambleId: String
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(invite.senderUid.isNotBlank() && invite.senderUid != uid) { "Invalid invite" }

        val now = System.currentTimeMillis()
        val batch = db.batch()
        val myFriendRef = db.collection(USERS).document(uid)
            .collection(FRIENDS).document(invite.senderUid)
        val theirFriendRef = db.collection(USERS).document(invite.senderUid)
            .collection(FRIENDS).document(uid)
        val inviteRef = db.collection(USERS).document(uid)
            .collection(INVITES).document(invite.id.ifBlank { invite.senderUid })

        batch.set(
            myFriendRef,
            Friend(
                uid = invite.senderUid,
                name = invite.senderName,
                preambleId = invite.senderPreambleId,
                addedAt = now
            )
        )
        batch.set(
            theirFriendRef,
            Friend(
                uid = uid,
                name = myName.trim().ifBlank { "Preamble user" },
                preambleId = myPreambleId.trim().uppercase(),
                addedAt = now
            )
        )
        batch.delete(inviteRef)
        batch.commit().await()
    }.onFailure { Log.e(TAG, "Failed to accept invite", it) }

    suspend fun declineInvite(inviteId: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        db.collection(USERS).document(uid)
            .collection(INVITES).document(inviteId)
            .delete()
            .await()
    }.onFailure { Log.e(TAG, "Failed to decline invite", it) }

    suspend fun removeFriend(friendUid: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(friendUid.isNotBlank() && friendUid != uid) { "Invalid friend" }

        val batch = db.batch()
        batch.delete(
            db.collection(USERS).document(uid)
                .collection(FRIENDS).document(friendUid)
        )
        batch.delete(
            db.collection(USERS).document(friendUid)
                .collection(FRIENDS).document(uid)
        )
        batch.commit().await()
    }.onFailure { Log.e(TAG, "Failed to remove friend", it) }

    suspend fun getFriendsOnce(): List<Friend> {
        val uid = requireCurrentUid()
        return db.collection(USERS).document(uid).collection(FRIENDS).get().await()
            .documents.mapNotNull { it.toObject(Friend::class.java) }
    }

    fun getPendingInvitesFlow(): Flow<List<WorkspaceInvite>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(USERS).document(uid).collection(INVITES)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Invite listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val invites = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject(WorkspaceInvite::class.java)?.copy(id = document.id)
                }
                trySend(invites)
            }

        awaitClose(listener::remove)
    }

    fun getFriendsFlow(): Flow<List<Friend>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(USERS).document(uid).collection(FRIENDS)
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Friend listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val friends = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject(Friend::class.java)
                }
                trySend(friends)
            }

        awaitClose(listener::remove)
    }

    /**
     * Observe every active collaborative task involving the signed-in user.
     *
     * The Task_Sync_Engine listens on `collaborativeTasks where memberUidMap.{uid} == true`,
     * projects each canonical document into the current user's [Task] via
     * [TaskProjection.documentToTask], and drops tasks whose own member status is terminal
     * (`declined`/`left`/`removed`). Firestore delivers snapshots in real time, so under
     * normal connectivity each canonical change reaches this flow well within the 5 s
     * propagation window (Requirements 7.1, 7.5). A listener error closes the flow so the
     * collecting ViewModel can `catch` it, retain its last-loaded list, and surface a
     * message without crashing.
     */
    fun getCollaborativeTasksFlow(): Flow<List<Task>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(COLLABORATIVE_TASKS)
            .whereEqualTo(FieldPath.of(MEMBER_UID_MAP, uid), true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Collaborative task listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }

                val tasks = snapshot?.documents.orEmpty().mapNotNull { document ->
                    documentToTask(document, uid)
                }.filter { task ->
                    task.assignmentStatus !in TERMINAL_MEMBER_STATUSES
                }
                trySend(tasks.sortedByDescending(Task::updatedTimestamp))
            }

        awaitClose(listener::remove)
    }

    fun getIncomingAssignmentsFlow(): Flow<List<Task>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val collector = launch {
            getCollaborativeTasksFlow().collect { tasks ->
                trySend(tasks.filter { it.collabAdminUid != uid })
            }
        }
        awaitClose(collector::cancel)
    }

    fun getOutgoingAssignmentsFlow(): Flow<List<Task>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val collector = launch {
            getCollaborativeTasksFlow().collect { tasks ->
                trySend(tasks.filter { it.collabAdminUid == uid })
            }
        }
        awaitClose(collector::cancel)
    }

    suspend fun assignTask(friend: Friend, task: Task, senderName: String): Result<Unit> =
        assignTaskToMultiple(listOf(friend), task, senderName)

    /**
     * Create the canonical collaborative task after the caller has already saved the
     * admin's own local Room copy (Requirements 6.2, 6.7).
     *
     * The admin's local copy is the source of truth for the UI and is created first by
     * the caller; this method only performs the asynchronous canonical-document write
     * (Requirement 6.3). It never touches the local copy, so a failure here leaves the
     * admin's saved copy intact (Requirement 6.8). The returned [Result] lets the caller
     * surface a "collaborative assignment could not be completed" message without rolling
     * back that local copy.
     */
    suspend fun assignTaskToMultiple(
        assignees: List<Friend>,
        task: Task,
        adminName: String
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        // Build + set the single canonical document (Requirement 6.3). createCollaborativeDocument
        // enforces all invariants and rejects the no-friend (6.4) and oversized (6.5, 6.6) cases.
        val document = createCollaborativeDocument(
            adminUid = uid,
            adminName = adminName,
            assignees = assignees,
            task = task
        )
        db.collection(COLLABORATIVE_TASKS).document(task.id)
            .set(document)
            .await()
    }.onFailure { Log.e(TAG, "Failed to create collaborative task", it) }

    /**
     * Write the finalized AI-derived task attributes to the canonical document as a
     * *subsequent* canonical update once asynchronous AI parsing completes
     * (Requirements 7.3, 7.4, 9.4).
     *
     * This is admin-only and refreshes only the shared `task` payload (plus `updatedAt`),
     * leaving every member's `memberStates` entry, the membership lists, and the
     * `memberUidMap` byte-for-byte unchanged (Requirement 8.6). That way attributes which
     * finalize *after* the canonical document was first created with the admin's
     * as-entered values are reconciled without resetting any member's acceptance or
     * completion (Requirement 7.4).
     *
     * When the canonical document does not yet exist (the AI attributes finalized before
     * the document was ever created), the document is created from [assignees] with the
     * finalized payload instead (Requirement 7.3).
     */
    suspend fun writeFinalizedCollaborativeAttributes(
        task: Task,
        assignees: List<Friend>
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(task.collabAdminUid == uid) { "Only the task admin can finalize task details" }
        val reference = db.collection(COLLABORATIVE_TASKS).document(task.id)
        val snapshot = reference.get().await()
        if (snapshot.exists()) {
            // Subsequent canonical update: refresh only the shared payload (7.4); the
            // per-member states are intentionally not touched (8.6).
            reference
                .update(
                    mapOf(
                        "task" to taskPayload(task),
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        } else {
            // The attributes finalized before the canonical document existed: create it now
            // carrying the finalized payload (7.3). createCollaborativeDocument enforces all
            // invariants and rejects the no-friend (6.4) / oversized (6.5, 6.6) cases.
            reference
                .set(
                    createCollaborativeDocument(
                        adminUid = uid,
                        adminName = task.collabAdminName ?: "Preamble user",
                        assignees = assignees,
                        task = task
                    )
                )
                .await()
        }
    }.onFailure { Log.e(TAG, "Failed to write finalized collaborative attributes", it) }

    /** Admin-only metadata update. Member completion is stored separately. */
    suspend fun updateCollaborativeTask(task: Task): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(task.collabAdminUid == uid) { "Only the task admin can edit task details" }
        val reference = db.collection(COLLABORATIVE_TASKS).document(task.id)
        try {
            val existing = reference.get().await()
            val remoteMemberUids = existing.stringList("memberUids")
            val localMemberUids = collaborativeMemberUids(
                adminUid = uid,
                assigneeUids = task.collabAssignees.map(CollabAssigneeStatus::uid)
            )
            val memberUids = remoteMemberUids.ifEmpty { localMemberUids }
            reference
                .update(
                    mapOf(
                        "task" to taskPayload(task),
                        MEMBER_UID_MAP to memberUidMap(memberUids),
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (exception: Exception) {
            val assignees = task.collabAssignees
                .filter { it.uid.isNotBlank() && it.uid != uid }
                .map { Friend(uid = it.uid, name = it.name) }
                .distinctBy(Friend::uid)
            if (assignees.isEmpty()) throw exception

            Log.w(TAG, "Collaborative task update failed; attempting canonical create fallback for ${task.id}", exception)
            reference
                .set(
                    createCollaborativeDocument(
                        adminUid = uid,
                        adminName = task.collabAdminName ?: "Preamble user",
                        assignees = assignees,
                        task = task
                    )
                )
                .await()
        }
    }.onFailure { Log.e(TAG, "Failed to update collaborative task", it) }

    /**
     * Update only the signed-in member's acceptance/completion record.
     *
     * The requested [newStatus] is mapped to a guarded transition from
     * [MemberStatusTransitions] and applied inside a transaction so the guard is
     * evaluated against the canonical document's current state:
     *  - `accepted`  -> accept   (only from `pending`)            (10.1, 10.3)
     *  - `declined`  -> decline  (only from `pending`)            (10.2, 10.3)
     *  - `completed` -> complete (only from `accepted`; sets the
     *                   completion flag + a UTC timestamp)        (10.4, 10.5)
     *
     * A rejected transition aborts the write and surfaces the guard's reason so the
     * caller can revert and message (10.7). Reverting completion (`accepted` while
     * currently `completed`, e.g. un-checking a finished task) is permitted and clears
     * the completion flag/timestamp. The write touches only `memberStates[uid]` so every
     * other member's state is left byte-for-byte identical (Requirement 8.6).
     */
    suspend fun updateCollabAssignmentStatus(
        taskId: String,
        adminUid: String,
        assigneeUid: String,
        newStatus: String,
        isCompleted: Boolean = false
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(assigneeUid == uid) { "You can only update your own assignment status" }
        require(newStatus in ALLOWED_MEMBER_STATUSES) { "Invalid assignment status" }
        val reference = db.collection(COLLABORATIVE_TASKS).document(taskId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw IllegalStateException("Shared task no longer exists")
            val current = snapshot.memberStateValue(uid)
            val now = System.currentTimeMillis()

            val transition = when (newStatus) {
                MemberStatusTransitions.Status.ACCEPTED ->
                    if (current.status == MemberStatusTransitions.Status.PENDING) {
                        MemberStatusTransitions.accept(current)
                    } else {
                        // Re-accept / revert a previously completed task: permitted, clears completion.
                        MemberStatusTransitions.TransitionResult.Applied(
                            current.copy(
                                status = MemberStatusTransitions.Status.ACCEPTED,
                                isCompleted = false,
                                completedTimestamp = null
                            )
                        )
                    }
                MemberStatusTransitions.Status.DECLINED -> MemberStatusTransitions.decline(current)
                MemberStatusTransitions.Status.COMPLETED -> MemberStatusTransitions.complete(current, now)
                else -> throw IllegalArgumentException("Unsupported assignment status: $newStatus")
            }

            val next = when (transition) {
                is MemberStatusTransitions.TransitionResult.Applied -> transition.state
                is MemberStatusTransitions.TransitionResult.Rejected ->
                    throw IllegalStateException(transition.reason)
            }

            transaction.update(
                reference,
                mapOf(
                    "memberStates.$uid.status" to next.status,
                    "memberStates.$uid.isCompleted" to next.isCompleted,
                    "memberStates.$uid.completedTimestamp" to next.completedTimestamp,
                    "updatedAt" to now
                )
            )
        }.await()
    }.onFailure { Log.e(TAG, "Failed to update member status", it) }

    /**
     * Shared subtask state may be edited by active members and is persisted to the
     * canonical document (Requirement 10.6). Whole-task completion still updates only the
     * caller's member record (Requirement 8.6) and is gated by the completion transition
     * guard (10.4, 10.5): marking complete requires the caller to be `accepted`. Clearing
     * completion (`parentCompleted == false`) reverts the caller to `accepted`; a pure
     * subtask edit that changes no completion state leaves every member record untouched.
     */
    suspend fun updateCollabTaskSubtasks(
        taskId: String,
        adminUid: String,
        subtasksJson: String,
        parentCompleted: Boolean = false
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        val reference = db.collection(COLLABORATIVE_TASKS).document(taskId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw IllegalStateException("Shared task no longer exists")
            val current = snapshot.memberStateValue(uid)
            val now = System.currentTimeMillis()

            val updates = mutableMapOf<String, Any?>(
                "task.subtasksJson" to subtasksJson,
                "task.updatedTimestamp" to now,
                "updatedAt" to now
            )

            if (parentCompleted) {
                // Completing the parent: enforce the accepted -> completed guard (10.4, 10.5).
                when (val transition = MemberStatusTransitions.complete(current, now)) {
                    is MemberStatusTransitions.TransitionResult.Applied -> {
                        updates["memberStates.$uid.status"] = transition.state.status
                        updates["memberStates.$uid.isCompleted"] = transition.state.isCompleted
                        updates["memberStates.$uid.completedTimestamp"] =
                            transition.state.completedTimestamp
                    }
                    is MemberStatusTransitions.TransitionResult.Rejected ->
                        throw IllegalStateException(transition.reason)
                }
            } else if (current.status == MemberStatusTransitions.Status.COMPLETED) {
                // Clearing completion reverts the caller to accepted; only the caller's entry changes.
                updates["memberStates.$uid.status"] = MemberStatusTransitions.Status.ACCEPTED
                updates["memberStates.$uid.isCompleted"] = false
                updates["memberStates.$uid.completedTimestamp"] = null
            }

            transaction.update(reference, updates)
        }.await()
    }.onFailure { Log.e(TAG, "Failed to update collaborative subtasks", it) }

    // Compatibility wrapper for the original single-assignee API.
    suspend fun updateAssignmentStatus(
        taskId: String,
        targetUid: String,
        newStatus: String,
        isCompleted: Boolean = false
    ): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("Not logged in"))
        return updateCollabAssignmentStatus(taskId, targetUid, uid, newStatus, isCompleted)
    }

    suspend fun removeCollaborator(taskId: String, memberUid: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(memberUid.isNotBlank() && memberUid != uid) { "Invalid collaborator" }
        val reference = db.collection(COLLABORATIVE_TASKS).document(taskId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw IllegalStateException("Shared task no longer exists")
            require(snapshot.getString("adminUid") == uid) { "Only the admin can remove members" }
            val memberUids = snapshot.stringList("memberUids")
            require(memberUid in memberUids) { "That user is not a member of this task" }
            val assigneeUids = snapshot.stringList("assigneeUids")
            val newMemberUids = memberUids.filterNot { it == memberUid }
            // Recompute the membership lists/map and flip only the removed member's status (11.2).
            transaction.update(
                reference,
                mapOf(
                    "memberUids" to newMemberUids,
                    "assigneeUids" to assigneeUids.filterNot { it == memberUid },
                    MEMBER_UID_MAP to memberUidMap(newMemberUids),
                    "memberStates.$memberUid.status" to "removed",
                    "updatedAt" to System.currentTimeMillis()
                )
            )
        }.await()
    }.onFailure { Log.e(TAG, "Failed to remove collaborator", it) }

    /**
     * Admin-only: add an existing friend to a collaborative task as a `pending` member
     * (Requirements 11.7, 11.8). Mirrors [removeCollaborator]'s transaction shape: it
     * recomputes `memberUids`/`assigneeUids`/`memberUidMap` and writes only the new
     * member's `memberStates` entry, leaving every existing member's state byte-for-byte
     * unchanged (Requirement 8.6). The add is rejected when it would push the assignee
     * count past the maximum (Requirement 11.9) or when the user is already a member.
     */
    suspend fun addMember(taskId: String, friend: Friend): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(friend.uid.isNotBlank() && friend.uid != uid) { "Invalid member" }
        val reference = db.collection(COLLABORATIVE_TASKS).document(taskId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw IllegalStateException("Shared task no longer exists")
            require(snapshot.getString("adminUid") == uid) { "Only the admin can add members" }
            val memberUids = snapshot.stringList("memberUids")
            require(friend.uid !in memberUids) { "That user is already a member of this task" }
            val assigneeUids = snapshot.stringList("assigneeUids")
            require(assigneeUids.size + 1 <= MAX_ASSIGNEES) {
                "A task can have at most $MAX_ASSIGNEES assignees"
            }
            val newMemberUids = memberUids + friend.uid
            val now = System.currentTimeMillis()
            transaction.update(
                reference,
                mapOf(
                    "memberUids" to newMemberUids,
                    "assigneeUids" to (assigneeUids + friend.uid),
                    MEMBER_UID_MAP to memberUidMap(newMemberUids),
                    "memberStates.${friend.uid}" to mapOf(
                        "uid" to friend.uid,
                        "name" to friend.name,
                        "role" to "member",
                        "status" to "pending",
                        "isCompleted" to false,
                        "completedTimestamp" to null,
                        "assignedTimestamp" to now
                    ),
                    "updatedAt" to now
                )
            )
        }.await()
    }.onFailure { Log.e(TAG, "Failed to add member", it) }

    /**
     * Non-admin self-removal (Requirements 12.2, 12.3, 12.4). Delegates to the pure
     * [CollaborativeMemberOps.leaveCollaborativeTask] transform inside a transaction so
     * the leaving member is removed from `memberUids`/`assigneeUids`/`memberUidMap` and
     * only that member's status is set to `left`; the admin's self-removal is rejected.
     */
    suspend fun leaveCollaborativeTask(taskId: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        val reference = db.collection(COLLABORATIVE_TASKS).document(taskId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val document = snapshot.data
                ?: throw IllegalStateException("Shared task no longer exists")
            val now = System.currentTimeMillis()
            when (val result = CollaborativeMemberOps.leaveCollaborativeTask(document, uid, now)) {
                is CollaborativeMemberOps.DocumentOpResult.Updated ->
                    transaction.set(reference, result.document)
                is CollaborativeMemberOps.DocumentOpResult.Rejected ->
                    throw IllegalStateException(result.reason)
            }
        }.await()
    }.onFailure { Log.e(TAG, "Failed to leave collaborative task", it) }

    /**
     * Admin ownership transfer (Requirements 11.4, 11.5). Delegates to the pure
     * [CollaborativeMemberOps.transferOwnership] transform inside a transaction: the
     * chosen member becomes the sole admin and the previous admin is retained as an
     * `accepted` member, with every canonical invariant preserved. Targets that are not
     * members (or the current admin) are rejected.
     *
     * When [leaveCurrentAdmin] is set (used by the friend-removal flow), the previous
     * admin is additionally removed via the [CollaborativeMemberOps.leaveCollaborativeTask]
     * transform after the transfer, composing the two pure transforms.
     */
    suspend fun transferOwnership(
        taskId: String,
        newAdminUid: String,
        leaveCurrentAdmin: Boolean = false
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        val reference = db.collection(COLLABORATIVE_TASKS).document(taskId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val document = snapshot.data
                ?: throw IllegalStateException("Shared task no longer exists")
            require(document["adminUid"] == uid) { "Only the admin can transfer ownership" }
            val now = System.currentTimeMillis()

            val transferred = when (
                val result = CollaborativeMemberOps.transferOwnership(document, newAdminUid, now)
            ) {
                is CollaborativeMemberOps.DocumentOpResult.Updated -> result.document
                is CollaborativeMemberOps.DocumentOpResult.Rejected ->
                    throw IllegalStateException(result.reason)
            }

            val finalDocument = if (leaveCurrentAdmin) {
                when (
                    val leftResult =
                        CollaborativeMemberOps.leaveCollaborativeTask(transferred, uid, now)
                ) {
                    is CollaborativeMemberOps.DocumentOpResult.Updated -> leftResult.document
                    is CollaborativeMemberOps.DocumentOpResult.Rejected ->
                        throw IllegalStateException(leftResult.reason)
                }
            } else {
                transferred
            }

            transaction.set(reference, finalDocument)
        }.await()
    }.onFailure { Log.e(TAG, "Failed to transfer ownership", it) }

    suspend fun deleteAssignment(taskId: String, targetUid: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("Not logged in"))
        return if (uid == targetUid) leaveCollaborativeTask(taskId) else deleteCanonicalTask(taskId)
    }

    suspend fun deleteCollabTaskForAll(
        taskId: String,
        assignees: List<CollabAssigneeStatus>
    ): Result<Unit> = deleteCanonicalTask(taskId)

    private suspend fun deleteCanonicalTask(taskId: String): Result<Unit> = runCatching<Unit> {
        requireCurrentUid()
        db.collection(COLLABORATIVE_TASKS).document(taskId).delete().await()
    }.onFailure { Log.e(TAG, "Failed to delete collaborative task", it) }

    private fun createCollaborativeDocument(
        adminUid: String,
        adminName: String,
        assignees: List<Friend>,
        task: Task
    ): Map<String, Any?> {
        val result = CollaborativeDocument.build(
            taskId = task.id,
            adminUid = adminUid,
            adminName = adminName,
            assignees = assignees.map { AssigneeRef(uid = it.uid, name = it.name) },
            taskPayload = taskPayload(task),
            now = System.currentTimeMillis(),
            adminCompleted = task.isCompleted,
            adminCompletedTimestamp = task.completedTimestamp
        )
        return when (result) {
            is CollaborativeDocumentResult.Created -> result.document
            CollaborativeDocumentResult.NotCollaborative ->
                throw IllegalArgumentException("Choose at least one friend")
            CollaborativeDocumentResult.TooManyAssignees ->
                throw IllegalArgumentException(
                    "A task can have at most ${CollaborativeDocument.MAX_ASSIGNEES} assignees"
                )
        }
    }

    // Pure payload/projection logic lives in TaskProjection so it can be exercised
    // by JVM/property tests without a live Firestore instance (Requirements 7.2, 7.7).
    private fun taskPayload(task: Task): Map<String, Any?> = TaskProjection.taskPayload(task)

    private fun documentToTask(document: DocumentSnapshot, currentUserUid: String): Task? {
        val data = document.data ?: return null
        return TaskProjection.documentToTask(data, document.id, currentUserUid)
            ?: run {
                Log.e(TAG, "Invalid collaborative task ${document.id}")
                null
            }
    }

    private fun DocumentSnapshot.stringList(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

    private fun collaborativeMemberUids(adminUid: String, assigneeUids: List<String>): List<String> =
        CollaborativeDocument.memberUids(adminUid, assigneeUids)

    private fun memberUidMap(memberUids: List<String>): Map<String, Boolean> =
        CollaborativeDocument.memberUidMap(memberUids)

    private fun DocumentSnapshot.memberStateValue(uid: String): MemberStatusTransitions.MemberStateValue {
        val states = get("memberStates") as? Map<*, *>
        val state = states?.get(uid) as? Map<*, *>
        val status = state?.get("status") as? String ?: MemberStatusTransitions.Status.PENDING
        val isCompleted = state?.get("isCompleted") as? Boolean ?: false
        val completedTimestamp = (state?.get("completedTimestamp") as? Number)?.toLong()
        return MemberStatusTransitions.MemberStateValue(status, isCompleted, completedTimestamp)
    }

    private fun requireCurrentUid(): String =
        currentUid ?: throw IllegalStateException("Sign in to use collaboration")

    private companion object {
        const val TAG = "WorkspaceRepository"
        const val FIRESTORE_DATABASE_ID = "preamble"
        const val USERS = "users"
        const val FRIENDS = "friends"
        const val INVITES = "invites"
        const val PREAMBLE_IDS = "preambleIds"
        const val COLLABORATIVE_TASKS = "collaborativeTasks"
        const val MEMBER_UID_MAP = "memberUidMap"
        const val COLLAB_SCHEMA_VERSION = CollaborativeDocument.SCHEMA_VERSION
        const val MAX_ASSIGNEES = CollaborativeDocument.MAX_ASSIGNEES

        val ALLOWED_MEMBER_STATUSES = setOf("pending", "accepted", "completed", "declined")
        val TERMINAL_MEMBER_STATUSES = TaskProjection.TERMINAL_MEMBER_STATUSES
    }
}
