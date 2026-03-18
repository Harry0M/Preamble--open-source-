package com.theblankstate.preamble.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.TaskDao
import com.theblankstate.preamble.data.TaskTagOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class FirebaseTaskSyncManager(
    context: Context,
    private val dao: TaskDao
) {
    private val auth = FirebaseAuth.getInstance()
    private val database = getDatabaseInstance()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val pendingUpserts = ConcurrentHashMap.newKeySet<String>()
    private val pendingDeletes = ConcurrentHashMap.newKeySet<String>()
    private val recentLocalWrites = ConcurrentHashMap<String, Long>()

    private var activeUid: String? = null
    private var activeTasksListener: ValueEventListener? = null
    private var activeTasksRefPath: String? = null
    private var activeTagOverridesListener: ValueEventListener? = null
    private var activeTagOverridesRefPath: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var started = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        appScope.launch {
            handleAuthChanged(firebaseAuth.currentUser?.uid)
        }
    }

    fun start() {
        if (started) return
        started = true
        registerNetworkCallback()
        auth.addAuthStateListener(authStateListener)
        appScope.launch {
            handleAuthChanged(auth.currentUser?.uid)
        }
    }

    suspend fun pushTask(task: Task) {
        // Don't push calendar events or Google Tasks to Firebase
        if (task.source != "local") return
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "Skipping pushTask(${task.id}) because no authenticated user")
            return
        }
        rememberLocalWrite(task.id)
        pendingDeletes.remove(task.id)
        pendingUpserts.add(task.id)
        try {
            taskRef(uid, task.id).setValue(RemoteTask.fromLocal(task)).await()
        } catch (exception: Exception) {
            Log.e(TAG, "pushTask failed for uid=$uid taskId=${task.id}", exception)
        } finally {
            pendingUpserts.remove(task.id)
        }
    }

    suspend fun deleteTask(taskId: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "Skipping deleteTask($taskId) because no authenticated user")
            return
        }
        rememberLocalWrite(taskId)
        pendingUpserts.remove(taskId)
        pendingDeletes.add(taskId)
        try {
            taskRef(uid, taskId).removeValue().await()
        } catch (exception: Exception) {
            Log.e(TAG, "deleteTask failed for uid=$uid taskId=$taskId", exception)
        } finally {
            pendingDeletes.remove(taskId)
        }
    }

    // ── Tag Override Firebase Sync ──

    /**
     * Push a tag override to Firebase for cross-device sync.
     * Stored at users/{uid}/tagOverrides/{googleId}
     */
    suspend fun pushTagOverride(googleId: String, tags: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val data = mapOf(
                "tags" to tags,
                "updatedTimestamp" to System.currentTimeMillis()
            )
            database.getReference("users").child(uid).child("tagOverrides")
                .child(googleId).setValue(data).await()
            Log.d(TAG, "Pushed tag override: $googleId -> $tags")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push tag override for $googleId", e)
        }
    }

    /**
     * Delete a tag override from Firebase.
     */
    suspend fun deleteTagOverride(googleId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            database.getReference("users").child(uid).child("tagOverrides")
                .child(googleId).removeValue().await()
            Log.d(TAG, "Deleted tag override: $googleId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete tag override for $googleId", e)
        }
    }

    suspend fun syncAllLocalToRemote() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "Skipping syncAllLocalToRemote because no authenticated user")
            return
        }
        val localTasks = dao.getAllTasks().filter { it.source == "local" }
        localTasks.forEach { task ->
            rememberLocalWrite(task.id)
            pendingUpserts.add(task.id)
            try {
                taskRef(uid, task.id).setValue(RemoteTask.fromLocal(task)).await()
            } catch (exception: Exception) {
                Log.e(TAG, "syncAllLocalToRemote failed for uid=$uid taskId=${task.id}", exception)
            } finally {
                pendingUpserts.remove(task.id)
            }
        }
    }

    suspend fun forceSyncBidirectional() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "Skipping forceSyncBidirectional — no authenticated user")
            return
        }
        syncAllLocalToRemote()
        try {
            val snapshot = usersTasksRef(uid).get().await()
            val remoteTasks = snapshot.children.mapNotNull { child ->
                val key = child.key ?: return@mapNotNull null
                if (key == "_flush_marker") return@mapNotNull null
                val remote = child.getValue(RemoteTask::class.java) ?: return@mapNotNull null
                remote.toLocal(key)
            }
            mergeRemoteIntoLocal(remoteTasks)
        } catch (e: Exception) {
            Log.e(TAG, "forceSyncBidirectional read failed", e)
        }
    }

    suspend fun flushPendingWrites(timeoutMs: Long = 8000L): Boolean {
        val uid = auth.currentUser?.uid ?: return true
        return withTimeoutOrNull(timeoutMs) {
            try {
                val flushRef = usersTasksRef(uid).child("_flush_marker")
                flushRef.setValue(System.currentTimeMillis()).await()
                flushRef.removeValue().await()
                true
            } catch (exception: Exception) {
                Log.e(TAG, "flushPendingWrites failed for uid=$uid", exception)
                false
            }
        } ?: false
    }

    private suspend fun handleAuthChanged(uid: String?) {
        if (uid == activeUid) return
        detachRealtimeListener()
        detachTagOverridesListener()
        activeUid = uid
        if (uid == null) return
        attachRealtimeListener(uid)
        attachTagOverridesListener(uid)
        syncAllLocalToRemote()
    }

    private fun attachRealtimeListener(uid: String) {
        val refPath = "users/$uid/tasks"
        val ref = database.getReference(refPath)
        Log.d(TAG, "Attaching realtime listener at path=$refPath")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val remoteTasks = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    if (key == "_flush_marker") return@mapNotNull null
                    val remote = child.getValue(RemoteTask::class.java) ?: return@mapNotNull null
                    remote.toLocal(key)
                }

                appScope.launch {
                    mergeRemoteIntoLocal(remoteTasks)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Realtime listener cancelled at path=$refPath code=${error.code} message=${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        activeTasksListener = listener
        activeTasksRefPath = refPath
    }

    private fun detachRealtimeListener() {
        val listener = activeTasksListener ?: return
        val refPath = activeTasksRefPath ?: return
        Log.d(TAG, "Detaching realtime listener from path=$refPath")
        database.getReference(refPath).removeEventListener(listener)
        activeTasksListener = null
        activeTasksRefPath = null
    }

    // ── Tag Overrides Realtime Listener (cross-device sync) ──

    private fun attachTagOverridesListener(uid: String) {
        val refPath = "users/$uid/tagOverrides"
        val ref = database.getReference(refPath)
        Log.d(TAG, "Attaching tag overrides listener at path=$refPath")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val overrides = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val tags = child.child("tags").getValue(String::class.java) ?: return@mapNotNull null
                    val updatedTs = child.child("updatedTimestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    TaskTagOverride(googleId = key, tags = tags, updatedTimestamp = updatedTs)
                }
                appScope.launch {
                    mergeTagOverridesFromRemote(overrides)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Tag overrides listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        activeTagOverridesListener = listener
        activeTagOverridesRefPath = refPath
    }

    private fun detachTagOverridesListener() {
        val listener = activeTagOverridesListener ?: return
        val refPath = activeTagOverridesRefPath ?: return
        Log.d(TAG, "Detaching tag overrides listener from path=$refPath")
        database.getReference(refPath).removeEventListener(listener)
        activeTagOverridesListener = null
        activeTagOverridesRefPath = null
    }

    private suspend fun mergeTagOverridesFromRemote(remoteOverrides: List<TaskTagOverride>) {
        for (remote in remoteOverrides) {
            val local = dao.getTagOverride(remote.googleId)
            if (local == null || remote.updatedTimestamp >= local.updatedTimestamp) {
                dao.upsertTagOverride(remote)
            }
        }
    }

    private suspend fun mergeRemoteIntoLocal(remoteTasks: List<Task>) {
        // Only load local-origin tasks for merging (skip calendar/google-tasks)
        val localTasks = dao.getAllLocalTasks()
        val localById = localTasks.associateBy { it.id }
        val remoteById = remoteTasks.associateBy { it.id }

        val upserts = remoteTasks.filter { remote ->
            if (pendingUpserts.contains(remote.id) || pendingDeletes.contains(remote.id)) {
                false
            } else {
                val local = localById[remote.id]
                local == null || remote.updatedTimestamp >= local.updatedTimestamp
            }
        }

        if (upserts.isNotEmpty()) {
            dao.insertTasks(upserts)
        }

        val now = System.currentTimeMillis()
        val deletions = localTasks.filter { local ->
            remoteById[local.id] == null &&
                !pendingUpserts.contains(local.id) &&
                !pendingDeletes.contains(local.id) &&
                !isRecentLocalWrite(local.id, now)
        }

        deletions.forEach { dao.deleteTask(it) }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                appScope.launch {
                    syncAllLocalToRemote()
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to register network callback", exception)
        }
    }

    private fun rememberLocalWrite(taskId: String) {
        recentLocalWrites[taskId] = System.currentTimeMillis()
    }

    private fun isRecentLocalWrite(taskId: String, now: Long): Boolean {
        val last = recentLocalWrites[taskId] ?: return false
        return now - last < LOCAL_WRITE_GRACE_MS
    }

    private fun usersTasksRef(uid: String) = database.getReference("users").child(uid).child("tasks")

    private fun taskRef(uid: String, taskId: String) = usersTasksRef(uid).child(taskId)

    companion object {
        private const val TAG = "FirebaseTaskSync"
        private const val LOCAL_WRITE_GRACE_MS = 15_000L
        private const val DATABASE_URL = "https://preambl-fbea6-default-rtdb.firebaseio.com"

        @Volatile
        private var persistenceInitialized = false

        private fun getDatabaseInstance(): FirebaseDatabase {
            return FirebaseDatabase.getInstance(DATABASE_URL)
        }

        fun enableOfflinePersistence() {
            if (persistenceInitialized) return
            synchronized(this) {
                if (persistenceInitialized) return
                try {
                    getDatabaseInstance().setPersistenceEnabled(true)
                } catch (exception: Exception) {
                    Log.w(TAG, "Failed to enable Firebase offline persistence", exception)
                }
                persistenceInitialized = true
            }
        }
    }
}

data class RemoteTask(
    var id: String = "",
    var title: String = "",
    var isCompleted: Boolean = false,
    var createdDate: String = "",
    var createdTimestamp: Long = 0L,
    var completedTimestamp: Long? = null,
    var deadlineTime: String? = null,
    var updatedTimestamp: Long = 0L,
    var source: String = "local",
    var priority: Int = 0,
    var description: String? = null,
    var recurrenceType: String? = null,
    var recurrenceInterval: Int? = null,
    var recurrenceDays: String? = null,
    var recurrenceEndDate: String? = null,
    var recurrenceParentId: String? = null,
    var parentTaskId: String? = null,
    var tags: String? = null,
    var googleCalendarId: String? = null,
    var googleRecurrenceInfo: String? = null
) {
    fun toLocal(fallbackId: String): Task? {
        val resolvedId = id.ifBlank { fallbackId }
        if (resolvedId.isBlank() || title.isBlank() || createdDate.isBlank()) return null

        val safeCreated = if (createdTimestamp > 0L) createdTimestamp else System.currentTimeMillis()
        val safeUpdated = if (updatedTimestamp > 0L) updatedTimestamp else safeCreated

        return Task(
            id = resolvedId,
            title = title,
            isCompleted = isCompleted,
            createdDate = createdDate,
            createdTimestamp = safeCreated,
            completedTimestamp = completedTimestamp,
            deadlineTime = deadlineTime,
            updatedTimestamp = safeUpdated,
            source = source,
            priority = priority,
            description = description,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceDays = recurrenceDays,
            recurrenceEndDate = recurrenceEndDate,
            recurrenceParentId = recurrenceParentId,
            parentTaskId = parentTaskId,
            tags = tags,
            googleCalendarId = googleCalendarId,
            googleRecurrenceInfo = googleRecurrenceInfo
        )
    }

    companion object {
        fun fromLocal(task: Task): RemoteTask {
            return RemoteTask(
                id = task.id,
                title = task.title,
                isCompleted = task.isCompleted,
                createdDate = task.createdDate,
                createdTimestamp = task.createdTimestamp,
                completedTimestamp = task.completedTimestamp,
                deadlineTime = task.deadlineTime,
                updatedTimestamp = task.updatedTimestamp,
                source = task.source,
                priority = task.priority,
                description = task.description,
                recurrenceType = task.recurrenceType,
                recurrenceInterval = task.recurrenceInterval,
                recurrenceDays = task.recurrenceDays,
                recurrenceEndDate = task.recurrenceEndDate,
                recurrenceParentId = task.recurrenceParentId,
                parentTaskId = task.parentTaskId,
                tags = task.tags,
                googleCalendarId = task.googleCalendarId,
                googleRecurrenceInfo = task.googleRecurrenceInfo
            )
        }
    }
}
