package com.theblankstate.preamble.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.theblankstate.preamble.data.Task

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
    val addedAt: Long = System.currentTimeMillis(),
    val productivityPoints: Int = (100..999).random() // TODO: Fetch real points from backend
)

class WorkspaceRepository {
    private val db = FirebaseFirestore.getInstance("preamble")
    private val auth = FirebaseAuth.getInstance()
    
    private val gson = GsonBuilder()
        .setExclusionStrategies(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes): Boolean {
                return f.name.endsWith("\$delegate") || f.declaredClass == Lazy::class.java
            }
            override fun shouldSkipClass(clazz: Class<*>): Boolean {
                return clazz == Lazy::class.java
            }
        })
        .create()
    
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

    // Get real-time updates for incoming assignments (assigned to me)
    fun getIncomingAssignmentsFlow(): Flow<List<Task>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(uid)
            .collection("collaborativeTasks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WorkspaceRepo", "Error listening to collaborative tasks", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val tasks = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val json = gson.toJson(data)
                            gson.fromJson(json, Task::class.java)
                        } catch (e: Exception) {
                            Log.e("WorkspaceRepo", "Failed to deserialize collaborative task", e)
                            null
                        }
                    }.filter { it.assignedByUid != uid } // Filter incoming
                    trySend(tasks)
                }
            }

        awaitClose { listener.remove() }
    }

    // Get real-time updates for outgoing assignments (assigned by me)
    fun getOutgoingAssignmentsFlow(): Flow<List<Task>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(uid)
            .collection("collaborativeTasks")
            .whereEqualTo("assignedByUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WorkspaceRepo", "Error listening to outgoing collaborative tasks", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val tasks = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val json = gson.toJson(data)
                            gson.fromJson(json, Task::class.java)
                        } catch (e: Exception) {
                            Log.e("WorkspaceRepo", "Failed to deserialize outgoing task", e)
                            null
                        }
                    }
                    trySend(tasks)
                }
            }

        awaitClose { listener.remove() }
    }

    // Assign a task to a friend
    suspend fun assignTask(friend: Friend, task: Task, senderName: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        
        // Prepare task with assignment fields
        val assignedTask = task.copy(
            assignedByUid = uid,
            assignedByName = senderName,
            assignedToUid = friend.uid,
            assignedToName = friend.name,
            assignmentStatus = "pending"
        )

        val json = gson.toJson(assignedTask)
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        val taskMap: Map<String, Any?> = gson.fromJson(json, type)

        return try {
            val batch = db.batch()

            // Write to recipient's collaborativeTasks
            val recipientRef = db.collection("users").document(friend.uid)
                .collection("collaborativeTasks").document(task.id)
            batch.set(recipientRef, taskMap)

            // Write to sender's collaborativeTasks
            val senderRef = db.collection("users").document(uid)
                .collection("collaborativeTasks").document(task.id)
            batch.set(senderRef, taskMap)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error assigning task", e)
            Result.failure(e)
        }
    }

    // Update assignment status in both collections
    suspend fun updateAssignmentStatus(taskId: String, targetUid: String, newStatus: String, isCompleted: Boolean = false): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val batch = db.batch()
            val updates = mutableMapOf<String, Any>(
                "assignmentStatus" to newStatus,
                "isCompleted" to isCompleted,
                "updatedTimestamp" to System.currentTimeMillis()
            )
            if (isCompleted) {
                updates["completedTimestamp"] = System.currentTimeMillis()
                updates["completedDate"] = TaskRepository.todayString()
            }

            // Update recipient's collaborativeTasks
            val recipientRef = db.collection("users").document(targetUid)
                .collection("collaborativeTasks").document(taskId)
            batch.update(recipientRef, updates)

            // Update sender's collaborativeTasks
            val senderRef = db.collection("users").document(uid)
                .collection("collaborativeTasks").document(taskId)
            batch.update(senderRef, updates)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error updating assignment status", e)
            Result.failure(e)
        }
    }

    // Delete collaborative task assignment from both collections (Recall)
    suspend fun deleteAssignment(taskId: String, targetUid: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val batch = db.batch()

            val recipientRef = db.collection("users").document(targetUid)
                .collection("collaborativeTasks").document(taskId)
            batch.delete(recipientRef)

            val senderRef = db.collection("users").document(uid)
                .collection("collaborativeTasks").document(taskId)
            batch.delete(senderRef)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("WorkspaceRepo", "Error deleting assignment", e)
            Result.failure(e)
        }
    }
}
