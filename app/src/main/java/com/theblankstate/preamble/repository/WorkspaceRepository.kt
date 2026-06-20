package com.theblankstate.preamble.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class WorkspaceInvite(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderPreambleId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class Friend(
    val uid: String = "",
    val name: String = "",
    val preambleId: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

class WorkspaceRepository {
    private val db = FirebaseFirestore.getInstance("preamble")
    private val auth = FirebaseAuth.getInstance()
    
    private val currentUid: String?
        get() = auth.currentUser?.uid

    // Look up a user by their Preamble ID
    suspend fun resolvePreambleId(targetPreambleId: String): Map<String, Any>? {
        return try {
            val doc = db.collection("preambleIds").document(targetPreambleId.uppercase()).get().await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error resolving Preamble ID", e)
            null
        }
    }

    // Send an invite to another user by their Preamble ID
    suspend fun sendInvite(targetPreambleId: String, senderName: String, senderPreambleId: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        val targetData = resolvePreambleId(targetPreambleId) ?: return Result.failure(Exception("User not found"))
        val targetUid = targetData["uid"] as? String ?: return Result.failure(Exception("Invalid target user data"))
        
        if (targetUid == uid) return Result.failure(Exception("You cannot invite yourself"))

        val invite = WorkspaceInvite(
            id = uid, // Use sender's UID as invite ID so we don't spam invites
            senderUid = uid,
            senderName = senderName,
            senderPreambleId = senderPreambleId
        )

        return try {
            db.collection("users").document(targetUid)
                .collection("invites").document(uid)
                .set(invite)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error sending invite", e)
            Result.failure(e)
        }
    }

    // Accept an invite
    suspend fun acceptInvite(invite: WorkspaceInvite, myName: String, myPreambleId: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            val batch = db.batch()

            // Add them to my friends list
            val myFriendRef = db.collection("users").document(uid)
                .collection("friends").document(invite.senderUid)
            
            batch.set(myFriendRef, Friend(
                uid = invite.senderUid,
                name = invite.senderName,
                preambleId = invite.senderPreambleId
            ))
            
            // Add me to their friends list
            val theirFriendRef = db.collection("users").document(invite.senderUid)
                .collection("friends").document(uid)
                
            batch.set(theirFriendRef, Friend(
                uid = uid,
                name = myName,
                preambleId = myPreambleId
            ))
            
            // Delete the invite
            val inviteRef = db.collection("users").document(uid)
                .collection("invites").document(invite.id)
            
            batch.delete(inviteRef)
            
            // Commit batch
            batch.commit().await()
                
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error accepting invite", e)
            Result.failure(e)
        }
    }

    // Decline an invite
    suspend fun declineInvite(inviteId: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            db.collection("users").document(uid)
                .collection("invites").document(inviteId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error declining invite", e)
            Result.failure(e)
        }
    }

    // Remove a friend
    suspend fun removeFriend(friendUid: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val batch = db.batch()

            // Remove from my list
            val myFriendRef = db.collection("users").document(uid)
                .collection("friends").document(friendUid)
            batch.delete(myFriendRef)
                
            // Remove from their list
            val theirFriendRef = db.collection("users").document(friendUid)
                .collection("friends").document(uid)
            batch.delete(theirFriendRef)
            
            // Commit batch
            batch.commit().await()
                
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error removing friend", e)
            Result.failure(e)
        }
    }

    
    // Get real-time updates for invites
    fun getPendingInvitesFlow(): Flow<List<WorkspaceInvite>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = db.collection("users").document(uid)
            .collection("invites")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WorkspaceRepo", "Error listening to invites", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val invites = snapshot.documents.mapNotNull { it.toObject(WorkspaceInvite::class.java)?.copy(id = it.id) }
                    trySend(invites)
                }
            }
            
        awaitClose { listener.remove() }
    }

    // Get real-time updates for friends
    fun getFriendsFlow(): Flow<List<Friend>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = db.collection("users").document(uid)
            .collection("friends")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WorkspaceRepo", "Error listening to friends", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val friends = snapshot.documents.mapNotNull { it.toObject(Friend::class.java) }
                    trySend(friends)
                }
            }
            
        awaitClose { listener.remove() }
    }
}
