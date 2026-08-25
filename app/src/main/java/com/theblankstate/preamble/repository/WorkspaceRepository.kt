package com.theblankstate.preamble.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.theblankstate.preamble.collab.AssigneeRef
import com.theblankstate.preamble.collab.CollaborativeDocument
import com.theblankstate.preamble.collab.CollaborativeDocumentResult
import com.theblankstate.preamble.collab.CollaborativeMemberOps
import com.theblankstate.preamble.collab.Leaderboard
import com.theblankstate.preamble.collab.MemberStatusTransitions
import com.theblankstate.preamble.collab.OutgoingInvite
import com.theblankstate.preamble.collab.InviteValidation
import com.theblankstate.preamble.collab.InviteValidator
import com.theblankstate.preamble.collab.PreambleId
import com.theblankstate.preamble.collab.Reactions
import com.theblankstate.preamble.collab.SocialSearch
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
    override val preambleId: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val productivityPoints: Int = 0,
    // Member photo sourced from the counterpart's public directory entry at
    // invite-accept time (Req 26). Nullable + defaulted so it is additive and
    // backward-compatible with existing Friend documents that predate this field.
    val photoUrl: String? = null
) : SocialSearch.Searchable {
    // Social_Search matches against the Preamble_ID and the display name (Req 9.2, 9.3);
    // displayName aliases the friend's name so a Friend row is searchable consistently with
    // the leaderboard rows without duplicating data.
    override val displayName: String get() = name
}

/**
 * Firestore gateway for friendships and collaborative tasks.
 *
 * A collaborative task has exactly one canonical document at
 * /collaborativeTasks/{taskId}. Room remains the local source of truth for the UI,
 * but Firestore is no longer populated with independently writable per-user copies.
 */
class WorkspaceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(FIRESTORE_DATABASE_ID),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
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

        // The successful write is a single batch that creates BOTH the recipient's
        // incoming Friend_Request and the sender mirror at
        // /users/{senderUid}/outgoingInvites/{targetUid} (social-hub-redesign Req 4.1).
        // Because every validation gate above runs before this commit, a rejected or
        // failed send creates neither document (Req 5.4).
        val incomingRef = db.collection(USERS).document(targetUid)
            .collection(INVITES).document(uid)
        val mirrorRef = db.collection(USERS).document(uid)
            .collection(OUTGOING_INVITES).document(targetUid)
        val mirror = OutgoingInvite(
            targetUid = targetUid,
            targetPreambleId = normalizedId,
            timestamp = System.currentTimeMillis()
        )

        commitWithAuthRetry {
            db.batch().apply {
                set(incomingRef, invite)
                set(mirrorRef, mirror)
            }
        }
    }.onFailure { Log.e(TAG, "Failed to send invite", it) }

    suspend fun acceptInvite(
        invite: WorkspaceInvite,
        myName: String,
        myPreambleId: String
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(invite.senderUid.isNotBlank() && invite.senderUid != uid) { "Invalid invite" }

        // Read each counterpart's photoUrl from the public directory so the reciprocal
        // Friend records carry the member photo (Req 26). The invite does not carry a
        // photoUrl, so the sender's is looked up via their Preamble_ID directory entry
        // (/preambleIds/{ID}, written with photoUrl by syncToFirestore). The current
        // user's own photoUrl comes from their own directory entry. Both lookups are
        // best-effort: a missing entry or absent photoUrl simply leaves photoUrl null.
        val senderPhotoUrl = resolvePreambleId(invite.senderPreambleId)?.get("photoUrl") as? String
        val myPhotoUrl = resolvePreambleId(myPreambleId)?.get("photoUrl") as? String

        val now = System.currentTimeMillis()
        val batch = db.batch()
        val myFriendRef = db.collection(USERS).document(uid)
            .collection(FRIENDS).document(invite.senderUid)
        val theirFriendRef = db.collection(USERS).document(invite.senderUid)
            .collection(FRIENDS).document(uid)
        val inviteRef = db.collection(USERS).document(uid)
            .collection(INVITES).document(invite.id.ifBlank { invite.senderUid })
        val outgoingInviteRef = db.collection(USERS).document(invite.senderUid)
            .collection(OUTGOING_INVITES).document(uid)

        batch.set(
            myFriendRef,
            Friend(
                uid = invite.senderUid,
                name = invite.senderName,
                preambleId = invite.senderPreambleId,
                addedAt = now,
                photoUrl = senderPhotoUrl
            )
        )
        batch.set(
            theirFriendRef,
            Friend(
                uid = uid,
                name = myName.trim().ifBlank { "Preamble user" },
                preambleId = myPreambleId.trim().uppercase(),
                addedAt = now,
                photoUrl = myPhotoUrl
            )
        )
        batch.delete(inviteRef)
        batch.delete(outgoingInviteRef)
        batch.commit().await()
    }.onFailure { Log.e(TAG, "Failed to accept invite", it) }

    suspend fun declineInvite(inviteId: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        db.collection(USERS).document(uid)
            .collection(INVITES).document(inviteId)
            .delete()
            .await()
    }.onFailure { Log.e(TAG, "Failed to decline invite", it) }

    /**
     * Withdraw an Outgoing_Invite the signed-in user has sent (social-hub-redesign
     * Req 4.1). Deletes both the incoming Friend_Request stored under the recipient
     * (`/users/{targetUid}/invites/{senderUid}`) and the sender mirror
     * (`/users/{senderUid}/outgoingInvites/{targetUid}`) in a single batch so the
     * invite disappears from both sides together.
     */
    suspend fun withdrawInvite(targetUid: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(targetUid.isNotBlank() && targetUid != uid) { "Invalid invite" }

        val batch = db.batch()
        batch.delete(
            db.collection(USERS).document(targetUid)
                .collection(INVITES).document(uid)
        )
        batch.delete(
            db.collection(USERS).document(uid)
                .collection(OUTGOING_INVITES).document(targetUid)
        )
        batch.commit().await()
    }.onFailure { Log.e(TAG, "Failed to withdraw invite", it) }

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
        // Clean up any remaining invites or outgoing invites between them in both directions
        batch.delete(
            db.collection(USERS).document(uid)
                .collection(OUTGOING_INVITES).document(friendUid)
        )
        // Since OUTGOING_INVITES might not exist, Firestore delete handles it gracefully
        batch.delete(
            db.collection(USERS).document(friendUid)
                .collection(OUTGOING_INVITES).document(uid)
        )
        batch.delete(
            db.collection(USERS).document(uid)
                .collection(INVITES).document(friendUid)
        )
        batch.delete(
            db.collection(USERS).document(friendUid)
                .collection(INVITES).document(uid)
        )
        batch.commit().await()
    }.onFailure { Log.e(TAG, "Failed to remove friend", it) }

    suspend fun getFriendsOnce(): List<Friend> {
        val uid = requireCurrentUid()
        return db.collection(USERS).document(uid).collection(FRIENDS).get().await()
            .documents.mapNotNull { it.toObject(Friend::class.java) }
    }

    /**
     * One-shot friends fetch for FCM-triggered refreshes.
     * Returns the fresh list; callers should update their StateFlow from this.
     */
    suspend fun refreshFriendsOnce(): List<Friend> = getFriendsOnce()

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

    /**
     * Observe the signed-in user's mirrored Outgoing_Invites at
     * `/users/{senderUid}/outgoingInvites` (social-hub-redesign Req 4.1).
     *
     * Follows the same snapshot-listener pattern as [getPendingInvitesFlow]: a
     * listener error closes the flow so the collecting ViewModel can `catch` it,
     * retain its last-loaded list, and surface a non-fatal message without
     * crashing the surface.
     */
    fun getOutgoingInvitesFlow(): Flow<List<OutgoingInvite>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(USERS).document(uid).collection(OUTGOING_INVITES)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Outgoing invite listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val outgoing = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject(OutgoingInvite::class.java)
                }
                trySend(outgoing)
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
     * Fetch collaborative tasks once (from server). Results flow from this call.
     * Call this on screen open and when FCM sync message arrives.
     * Much cheaper than always-on listener — only billed when explicitly called.
     */
    suspend fun fetchCollaborativeTasks(): List<Task> {
        val uid = currentUid ?: return emptyList()
        return try {
            db.collection(COLLABORATIVE_TASKS)
                .whereEqualTo(FieldPath.of(MEMBER_UID_MAP, uid), true)
                .get().await()
                .documents.mapNotNull { document ->
                    documentToTask(document, uid)
                }.filter { task ->
                    task.assignmentStatus !in TERMINAL_MEMBER_STATUSES
                }.sortedByDescending(Task::updatedTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCollaborativeTasks failed", e)
            emptyList()
        }
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
        adminName: String,
        adminPhotoUrl: String? = null
    ): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        // Build + set the single canonical document (Requirement 6.3). createCollaborativeDocument
        // enforces all invariants and rejects the no-friend (6.4) and oversized (6.5, 6.6) cases.
        // Each member's photoUrl (Req 26) is carried into the canonical doc: the admin's own from
        // [adminPhotoUrl] (UserProfile) and each assignee's from the Friend record.
        val document = createCollaborativeDocument(
            adminUid = uid,
            adminName = adminName,
            assignees = assignees,
            task = task,
            adminPhotoUrl = adminPhotoUrl
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
        assignees: List<Friend>,
        adminPhotoUrl: String? = null
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
                        task = task,
                        adminPhotoUrl = adminPhotoUrl
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
                .map { Friend(uid = it.uid, name = it.name, photoUrl = it.photoUrl) }
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

        // Award Productivity_Points only for the collaborative accepted -> completed
        // transition (Req 7.1, 7.5). The completion already committed above; this is a
        // best-effort follow-up — awardCompletionPoints swallows and logs its own failure,
        // so it never rolls back the completion (Req 7.2 idempotency guards double counting).
        if (newStatus == MemberStatusTransitions.Status.COMPLETED) {
            awardCompletionPoints(taskId)
        }
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

    // ---------------------------------------------------------------------------------------
    // Social-engagement: reactions, leaderboard points, and nudges.
    // ---------------------------------------------------------------------------------------

    /**
     * Add, change, or remove only the signed-in member's reaction on a Collaborative_Task.
     *
     * Mirrors [updateCollabAssignmentStatus]: a client-side guard rejects an out-of-set emoji
     * before any write (Req 1.3), and a transaction reads the canonical document, delegates the
     * toggle/change/remove decision to the pure [Reactions] logic, and writes only the caller's
     * own slice `reactions.{uid}` plus `updatedAt` (Req 2.1, 2.5; matching the deployed
     * `updatesOwnReactionOnly()` rule). Passing [emoji] == null is the explicit remove control
     * (Req 2.4) and uses [FieldValue.delete] on the caller's key.
     *
     * @param emoji a member of [Reactions.EMOJI_SET] to add/change, or null to remove.
     */
    suspend fun updateMyReaction(taskId: String, emoji: String?): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        // Client-side guard (Req 1.3): reject an emoji outside the fixed Reaction_Emoji_Set.
        require(emoji == null || Reactions.isValidEmoji(emoji)) { Reactions.REASON_INVALID_EMOJI }
        val reference = db.collection(COLLABORATIVE_TASKS).document(taskId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw IllegalStateException("Shared task no longer exists")
            val now = System.currentTimeMillis()
            val currentReactions = snapshot.reactionsValue()

            // Decide the value written to the caller's own reactions key: the new entry map for an
            // add/change, FieldValue.delete() for a removal, or null to skip the write entirely
            // (explicit remove when the reactor has no reaction — a no-op, Req 2.4).
            val ownEntry: Any? = if (emoji == null) {
                if (currentReactions.containsKey(uid)) FieldValue.delete() else null
            } else {
                when (val result = Reactions.apply(currentReactions, uid, emoji, null, now)) {
                    is Reactions.ReactionResult.Rejected ->
                        throw IllegalStateException(result.reason)
                    is Reactions.ReactionResult.Updated -> when (result.effect) {
                        Reactions.Effect.REMOVED -> FieldValue.delete()
                        Reactions.Effect.ADDED, Reactions.Effect.CHANGED -> result.reactions[uid]
                    }
                }
            }

            if (ownEntry != null) {
                transaction.update(
                    reference,
                    mapOf(
                        "reactions.$uid" to ownEntry,
                        "updatedAt" to now
                    )
                )
            }
        }.await()
    }.onFailure { Log.e(TAG, "Failed to update reaction", it) }

    /**
     * Award the Completion_Award to the signed-in user for completing [taskId], at most once.
     *
     * A transaction on `/leaderboard/{uid}` reads the current [Leaderboard.ScoreDoc] (an empty
     * first-award doc when the document is absent), applies the pure [Leaderboard.award]
     * (idempotent on `awardedTasks`, monotonic, bucketed by the current Weekly_Window — Req 7.1,
     * 7.2, 7.3, 7.4), and commits nothing when the task was already awarded. The first-ever award
     * creates the document at exactly `totalPoints == 10` to satisfy the `/leaderboard` create
     * rule; subsequent awards update with the +10 monotonic delta.
     */
    suspend fun awardCompletionPoints(taskId: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        val reference = db.collection(LEADERBOARD).document(uid)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val now = System.currentTimeMillis()
            val current = snapshot.scoreDoc(uid)
            val awarded = Leaderboard.award(current, taskId, now)

            // Already awarded (Req 7.2): the doc is unchanged, so commit nothing.
            if (awarded != current) {
                val data = mapOf(
                    "uid" to uid,
                    "totalPoints" to awarded.totalPoints,
                    "weeklyPoints" to awarded.weeklyPoints,
                    "awardedTasks" to awarded.awardedTasks.toList(),
                    "updatedAt" to now
                )
                if (snapshot.exists()) {
                    transaction.update(reference, data)
                } else {
                    // First award: create at exactly the Completion_Award (matches the create rule).
                    transaction.set(reference, data)
                }
            }
        }.await()
    }.onFailure { Log.e(TAG, "Failed to award completion points", it) }

    /**
     * The set of the signed-in user's friend uids (excluding self), used to scope the
     * Friends_Leaderboard. Reuses [getFriendsOnce].
     */
    suspend fun getFriendUidsOnce(): Set<String> {
        val uid = currentUid
        return getFriendsOnce()
            .map(Friend::uid)
            .filter { it.isNotBlank() && it != uid }
            .toSet()
    }

    /**
     * Observe the leaderboard score documents for the signed-in user and that user's friends.
     *
     * The signed-in user's `/leaderboard/{uid}` doc is observed with a snapshot listener, and a
     * per-friend snapshot listener is attached for each friend uid (each `get`/listen is evaluated
     * against the `/leaderboard` read rule, which permits a friend read only when the reciprocal
     * friendship exists — so the bounded per-doc reads avoid the query-time rule rejection a
     * `whereIn` collection query would hit). The friend set is sourced from [getFriendsFlow], and
     * listeners are added/removed as friends change. A per-listener error is logged and the last
     * loaded scores are retained (no re-emit), so the collecting ViewModel keeps its prior ranking.
     */
    fun getLeaderboardScoresFlow(): Flow<Map<String, Leaderboard.ScoreDoc>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        val lock = Any()
        val scores = LinkedHashMap<String, Leaderboard.ScoreDoc>()
        val registrations = HashMap<String, ListenerRegistration>()

        fun publish() {
            synchronized(lock) { trySend(LinkedHashMap(scores)) }
        }

        fun listenTo(targetUid: String) {
            synchronized(lock) {
                if (registrations.containsKey(targetUid)) return
                val registration = db.collection(LEADERBOARD).document(targetUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            // Retain last-loaded scores; do not close or re-emit (Req 9.1, 9.4).
                            Log.e(TAG, "Leaderboard listener failed for $targetUid", error)
                            return@addSnapshotListener
                        }
                        synchronized(lock) {
                            if (snapshot != null && snapshot.exists()) {
                                scores[targetUid] = snapshot.scoreDoc(targetUid)
                            } else {
                                scores.remove(targetUid)
                            }
                        }
                        publish()
                    }
                registrations[targetUid] = registration
            }
        }

        fun stopListening(targetUid: String) {
            synchronized(lock) {
                registrations.remove(targetUid)?.remove()
                scores.remove(targetUid)
            }
        }

        // Always observe the signed-in user's own score.
        listenTo(uid)

        // Track the friend set and reconcile listeners as it changes.
        val friendCollector = launch {
            getFriendsFlow().collect { friends ->
                val friendUids = friends.map(Friend::uid).filter { it.isNotBlank() }.toSet()
                val keep = friendUids + uid
                val stale = synchronized(lock) { registrations.keys.filter { it !in keep } }
                stale.forEach(::stopListening)
                friendUids.forEach(::listenTo)
            }
        }

        awaitClose {
            friendCollector.cancel()
            synchronized(lock) {
                registrations.values.forEach(ListenerRegistration::remove)
                registrations.clear()
            }
        }
    }

    /**
     * Send a Nudge to a pending member by invoking the server-side `sendNudge` callable
     * (default region `us-central1`), which authoritatively enforces membership, the pending
     * target, the not-self guard, and the 60-minute rate limit before delivering the push
     * (Req 11, 12). A [FirebaseFunctionsException] (e.g. `failed-precondition`) is mapped to a
     * failed [Result] carrying the server's reason so the ViewModel can revert the optimistic
     * nudged state and surface the message (Req 10.5, 11.2, 12.2).
     */
    suspend fun sendNudge(taskId: String, targetUid: String): Result<Unit> = runCatching<Unit> {
        val uid = requireCurrentUid()
        require(taskId.isNotBlank()) { "Invalid task" }
        require(targetUid.isNotBlank() && targetUid != uid) { "You cannot nudge yourself" }
        val payload = hashMapOf<String, Any?>(
            "taskId" to taskId,
            "targetUid" to targetUid
        )
        try {
            functions.getHttpsCallable("sendNudge").call(payload).await()
        } catch (exception: FirebaseFunctionsException) {
            // Carry the callable's reason message (failed-precondition, etc.) up to the caller.
            throw IllegalStateException(
                exception.message ?: "The nudge could not be sent",
                exception
            )
        }
    }.onFailure { Log.e(TAG, "Failed to send nudge", it) }

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
        task: Task,
        adminPhotoUrl: String? = null
    ): Map<String, Any?> {
        val result = CollaborativeDocument.build(
            taskId = task.id,
            adminUid = adminUid,
            adminName = adminName,
            assignees = assignees.map { AssigneeRef(uid = it.uid, name = it.name, photoUrl = it.photoUrl) },
            taskPayload = taskPayload(task),
            now = System.currentTimeMillis(),
            adminCompleted = task.isCompleted,
            adminCompletedTimestamp = task.completedTimestamp,
            adminPhotoUrl = adminPhotoUrl
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

    /** Project the canonical document's loosely-typed `reactions` map into a string-keyed map. */
    private fun DocumentSnapshot.reactionsValue(): Map<String, Any?> {
        val reactions = get("reactions") as? Map<*, *> ?: return emptyMap()
        val result = LinkedHashMap<String, Any?>(reactions.size)
        for ((key, value) in reactions) {
            if (key is String) result[key] = value
        }
        return result
    }

    /** Parse a `/leaderboard/{uid}` document into a [Leaderboard.ScoreDoc] (empty when absent). */
    private fun DocumentSnapshot.scoreDoc(uid: String): Leaderboard.ScoreDoc {
        if (!exists()) return Leaderboard.ScoreDoc(uid = uid)
        val totalPoints = (get("totalPoints") as? Number)?.toInt() ?: 0
        val weeklyRaw = get("weeklyPoints") as? Map<*, *>
        val weeklyPoints = LinkedHashMap<String, Int>()
        weeklyRaw?.forEach { (key, value) ->
            if (key is String && value is Number) weeklyPoints[key] = value.toInt()
        }
        val awardedTasks = (get("awardedTasks") as? List<*>)
            ?.filterIsInstance<String>()
            ?.toSet()
            .orEmpty()
        return Leaderboard.ScoreDoc(
            uid = uid,
            totalPoints = totalPoints,
            weeklyPoints = weeklyPoints,
            awardedTasks = awardedTasks
        )
    }

    private fun requireCurrentUid(): String =
        currentUid ?: throw IllegalStateException("Sign in to use collaboration")

    /**
     * Commits a batch built by [buildBatch], retrying exactly once — after forcing a
     * fresh ID token — if the first attempt is rejected with PERMISSION_DENIED.
     *
     * Firestore's authenticated gRPC stream can briefly lag behind FirebaseAuth right
     * after sign-in, a token refresh, or the app resuming from background: a write sent
     * during that window can be rejected by security rules even though the user is
     * legitimately authorized, and normally succeeds moments later. Forcing a fresh ID
     * token and rebuilding+recommitting the batch (a committed [WriteBatch] cannot be
     * reused) resolves that race. Any other failure — including a genuine second
     * PERMISSION_DENIED after the token refresh — is rethrown unchanged.
     */
    private suspend fun commitWithAuthRetry(buildBatch: () -> WriteBatch) {
        try {
            buildBatch().commit().await()
        } catch (exception: FirebaseFirestoreException) {
            if (exception.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw exception
            auth.currentUser?.getIdToken(true)?.await()
            buildBatch().commit().await()
        }
    }

    private companion object {
        const val TAG = "WorkspaceRepository"
        const val FIRESTORE_DATABASE_ID = "preamble"
        const val USERS = "users"
        const val FRIENDS = "friends"
        const val INVITES = "invites"
        const val OUTGOING_INVITES = "outgoingInvites"
        const val PREAMBLE_IDS = "preambleIds"
        const val COLLABORATIVE_TASKS = "collaborativeTasks"
        const val LEADERBOARD = "leaderboard"
        const val MEMBER_UID_MAP = "memberUidMap"
        const val COLLAB_SCHEMA_VERSION = CollaborativeDocument.SCHEMA_VERSION
        const val MAX_ASSIGNEES = CollaborativeDocument.MAX_ASSIGNEES

        val ALLOWED_MEMBER_STATUSES = setOf("pending", "accepted", "completed", "declined")
        val TERMINAL_MEMBER_STATUSES = TaskProjection.TERMINAL_MEMBER_STATUSES
    }
}
