package com.theblankstate.preamble.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private val firestore = FirebaseFirestore.getInstance(FIRESTORE_DATABASE_ID)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val pendingUpserts = ConcurrentHashMap.newKeySet<String>()
    private val pendingDeletes = ConcurrentHashMap.newKeySet<String>()
    private val recentLocalWrites = ConcurrentHashMap<String, Long>()

    private var activeUid: String? = null
    private var activeTasksListener: ListenerRegistration? = null
    private var activeTagOverridesListener: ListenerRegistration? = null
    private var parityCheckedUid: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var started = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        appScope.launch {
            handleAuthChanged(firebaseAuth.currentUser?.uid)
        }
    }

    fun start() {
        if (started) {
            Log.d(TAG, "start() called but already started")
            return
        }
        started = true
        Log.i(TAG, "FirebaseTaskSyncManager started, database=$FIRESTORE_DATABASE_ID")
        registerNetworkCallback()
        auth.addAuthStateListener(authStateListener)
        appScope.launch {
            handleAuthChanged(auth.currentUser?.uid)
        }
    }

    suspend fun runParityCheckNow(): MirrorParitySummary? {
        val uid = auth.currentUser?.uid ?: return null
        return verifyMirrorParity(uid)
    }

    suspend fun pushTask(task: Task) {
        Log.d(TAG, "pushTask called: id=${task.id} title=${task.title} source=${task.source}")
        // Don't push calendar events or Google Tasks to Firebase
        if (task.source != "local") {
            Log.d(TAG, "Skipping pushTask(${task.id}) - source is ${task.source}, not local")
            return
        }
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "Skipping pushTask(${task.id}) because no authenticated user")
            return
        }
        Log.d(TAG, "Pushing task to Firestore: uid=$uid taskId=${task.id}")
        rememberLocalWrite(task.id)
        pendingDeletes.remove(task.id)
        pendingUpserts.add(task.id)
        val remoteTask = RemoteTask.fromLocal(task)
        try {
            taskDoc(uid, task.id).set(remoteTask).await()
            Log.i(TAG, "Firestore pushTask SUCCESS: uid=$uid taskId=${task.id}")
        } catch (exception: Exception) {
            Log.e(TAG, "Firestore pushTask FAILED: uid=$uid taskId=${task.id}", exception)
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
            taskDoc(uid, taskId).delete().await()
        } catch (exception: Exception) {
            Log.e(TAG, "Firestore deleteTask failed for uid=$uid taskId=$taskId", exception)
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
        val updatedTimestamp = System.currentTimeMillis()
        pushTagOverrideToDatastores(
            uid = uid,
            override = TaskTagOverride(
                googleId = googleId,
                tags = tags,
                updatedTimestamp = updatedTimestamp
            )
        )
        Log.d(TAG, "Pushed tag override: $googleId -> $tags")
    }

    /**
     * Delete a tag override from Firebase.
     */
    suspend fun deleteTagOverride(googleId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            tagOverrideDoc(uid, googleId).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Firestore tag override for $googleId", e)
        }
        Log.d(TAG, "Deleted tag override: $googleId")
    }

    suspend fun syncAllLocalToRemote() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "Skipping syncAllLocalToRemote because no authenticated user")
            return
        }
        val localTasks = dao.getAllTasks().filter { it.source == "local" }
        Log.d(TAG, "syncAllLocalToRemote: pushing ${localTasks.size} local tasks for uid=$uid")
        localTasks.forEach { task ->
            rememberLocalWrite(task.id)
            pendingUpserts.add(task.id)
            val remoteTask = RemoteTask.fromLocal(task)
            try {
                taskDoc(uid, task.id).set(remoteTask).await()
                Log.d(TAG, "syncAllLocalToRemote: pushed task ${task.id}")
            } catch (exception: Exception) {
                Log.e(TAG, "Firestore syncAllLocalToRemote failed for uid=$uid taskId=${task.id}", exception)
            } finally {
                pendingUpserts.remove(task.id)
            }
        }

        val localOverrides = dao.getAllTagOverrides()
        localOverrides.forEach { override ->
            pushTagOverrideToDatastores(uid, override)
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
            val remoteTasks = usersTasksCollection(uid).get().await().documents.mapNotNull { doc ->
                val remote = doc.toObject(RemoteTask::class.java) ?: return@mapNotNull null
                remote.toLocal(decodeFirestoreDocId(doc.id))
            }
            mergeRemoteIntoLocal(remoteTasks)
        } catch (e: Exception) {
            Log.e(TAG, "forceSyncBidirectional read failed", e)
        }
    }

    suspend fun flushPendingWrites(timeoutMs: Long = 8000L): Boolean {
        auth.currentUser?.uid ?: return true
        return withTimeoutOrNull(timeoutMs) {
            try {
                firestore.waitForPendingWrites().await()
                true
            } catch (exception: Exception) {
                Log.e(TAG, "Firestore flushPendingWrites failed", exception)
                false
            }
        } ?: false
    }

    private suspend fun handleAuthChanged(uid: String?) {
        Log.d(TAG, "handleAuthChanged: uid=$uid activeUid=$activeUid")
        if (uid == activeUid) return
        detachRealtimeListener()
        detachTagOverridesListener()
        activeUid = uid
        parityCheckedUid = null
        if (uid == null) {
            Log.d(TAG, "User signed out, skipping sync setup")
            return
        }
        Log.d(TAG, "User signed in: uid=$uid, setting up listeners")
        attachRealtimeListener(uid)
        attachTagOverridesListener(uid)
        syncAllLocalToRemote()
        if (parityCheckedUid != uid) {
            verifyMirrorParity(uid)
            parityCheckedUid = uid
        }
    }

    private fun attachRealtimeListener(uid: String) {
        Log.d(TAG, "Attaching Firestore listener at path=users/$uid/tasks")
        activeTasksListener = usersTasksCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Firestore tasks listener error for uid=$uid: ${error.message}", error)
                return@addSnapshotListener
            }

            val docCount = snapshot?.documents?.size ?: 0
            Log.d(TAG, "Firestore snapshot received: $docCount documents")

            val remoteTasks = snapshot?.documents?.mapNotNull { doc ->
                val remote = doc.toObject(RemoteTask::class.java) ?: return@mapNotNull null
                remote.toLocal(decodeFirestoreDocId(doc.id))
            } ?: emptyList()

            appScope.launch {
                mergeRemoteIntoLocal(remoteTasks)
            }
        }
    }

    private fun detachRealtimeListener() {
        val listener = activeTasksListener ?: return
        Log.d(TAG, "Detaching Firestore listener from users/$activeUid/tasks")
        listener.remove()
        activeTasksListener = null
    }

    // ── Tag Overrides Realtime Listener (cross-device sync) ──

    private fun attachTagOverridesListener(uid: String) {
        Log.d(TAG, "Attaching Firestore tag overrides listener at path=users/$uid/tagOverrides")
        activeTagOverridesListener = usersTagOverridesCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Firestore tag overrides listener cancelled for uid=$uid", error)
                return@addSnapshotListener
            }

            val overrides = snapshot?.documents?.mapNotNull { doc ->
                val googleId = doc.getString("googleId") ?: decodeFirestoreDocId(doc.id)
                val tags = doc.getString("tags") ?: return@mapNotNull null
                val updatedTs = doc.getLong("updatedTimestamp") ?: System.currentTimeMillis()
                TaskTagOverride(googleId = googleId, tags = tags, updatedTimestamp = updatedTs)
            } ?: emptyList()

            appScope.launch {
                mergeTagOverridesFromRemote(overrides)
            }
        }
    }

    private fun detachTagOverridesListener() {
        val listener = activeTagOverridesListener ?: return
        Log.d(TAG, "Detaching Firestore tag overrides listener from users/$activeUid/tagOverrides")
        listener.remove()
        activeTagOverridesListener = null
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
        Log.d(TAG, "mergeRemoteIntoLocal: received ${remoteTasks.size} tasks from Firestore")
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

    private suspend fun verifyMirrorParity(uid: String): MirrorParitySummary? {
        try {
            // Compare local Room and Firestore task IDs.
            val localTaskIds = dao.getAllLocalTasks().map { it.id }.toSet()

            val firestoreTaskIds = usersTasksCollection(uid).get().await().documents.mapNotNull { doc ->
                val id = doc.getString("id")
                when {
                    !id.isNullOrBlank() -> id
                    else -> decodeFirestoreDocId(doc.id)
                }
            }.toSet()

            // Compare local Room and Firestore tag override IDs.
            val localTagOverrideIds = dao.getAllTagOverrides().map { it.googleId }.toSet()

            val firestoreTagOverrideIds = usersTagOverridesCollection(uid).get().await().documents.mapNotNull { doc ->
                val googleId = doc.getString("googleId")
                when {
                    !googleId.isNullOrBlank() -> googleId
                    else -> decodeFirestoreDocId(doc.id)
                }
            }.toSet()

            val tasksDiff = logParitySetDiff(
                label = "tasks",
                uid = uid,
                baselineIds = localTaskIds,
                firestoreIds = firestoreTaskIds
            )
            val tagOverridesDiff = logParitySetDiff(
                label = "tagOverrides",
                uid = uid,
                baselineIds = localTagOverrideIds,
                firestoreIds = firestoreTagOverrideIds
            )
            return MirrorParitySummary(
                tasksMatch = tasksDiff.matches,
                tagOverridesMatch = tagOverridesDiff.matches,
                tasksBaselineCount = tasksDiff.baselineCount,
                tasksFirestoreCount = tasksDiff.firestoreCount,
                tagOverridesBaselineCount = tagOverridesDiff.baselineCount,
                tagOverridesFirestoreCount = tagOverridesDiff.firestoreCount
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Parity check failed for uid=$uid", exception)
            return null
        }
    }

    private fun logParitySetDiff(
        label: String,
        uid: String,
        baselineIds: Set<String>,
        firestoreIds: Set<String>
    ): ParitySetDiff {
        val missingInFirestore = (baselineIds - firestoreIds).take(PARITY_SAMPLE_LIMIT)
        val missingInBaseline = (firestoreIds - baselineIds).take(PARITY_SAMPLE_LIMIT)
        val matches = missingInFirestore.isEmpty() && missingInBaseline.isEmpty()

        if (matches) {
            Log.i(
                TAG,
                "Parity OK for $label uid=$uid count=${baselineIds.size}"
            )
            return ParitySetDiff(matches = true, baselineCount = baselineIds.size, firestoreCount = firestoreIds.size)
        }

        Log.w(
            TAG,
            "Parity mismatch for $label uid=$uid baseline=${baselineIds.size} firestore=${firestoreIds.size} " +
                "missingInFirestore=${missingInFirestore.size} missingInBaseline=${missingInBaseline.size} " +
                "sampleMissingInFirestore=$missingInFirestore sampleMissingInBaseline=$missingInBaseline"
        )
        return ParitySetDiff(matches = false, baselineCount = baselineIds.size, firestoreCount = firestoreIds.size)
    }

    private suspend fun pushTagOverrideToDatastores(uid: String, override: TaskTagOverride) {
        val data = mapOf(
            "googleId" to override.googleId,
            "tags" to override.tags,
            "updatedTimestamp" to override.updatedTimestamp
        )

        try {
            tagOverrideDoc(uid, override.googleId)
                .set(data)
                .await()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to write tag override to Firestore for ${override.googleId}", exception)
        }
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

    private fun usersTasksCollection(uid: String) =
        firestore.collection("users").document(uid).collection("tasks")

    private fun taskDoc(uid: String, taskId: String) =
        usersTasksCollection(uid).document(firestoreDocId(taskId))

    private fun usersTagOverridesCollection(uid: String) =
        firestore.collection("users").document(uid).collection("tagOverrides")

    private fun tagOverrideDoc(uid: String, googleId: String) =
        usersTagOverridesCollection(uid).document(firestoreDocId(googleId))

    private fun firestoreDocId(rawId: String): String = rawId.replace("/", "%2F")

    private fun decodeFirestoreDocId(rawId: String): String = rawId.replace("%2F", "/")

    companion object {
        private const val TAG = "FirebaseTaskSync"
        private const val LOCAL_WRITE_GRACE_MS = 15_000L
        private const val PARITY_SAMPLE_LIMIT = 10
        private const val FIRESTORE_DATABASE_ID = "preamble"

        @Volatile
        private var persistenceInitialized = false

        fun enableOfflinePersistence() {
            if (persistenceInitialized) return
            synchronized(this) {
                if (persistenceInitialized) return
                // Firestore Android SDK enables local persistence by default.
                FirebaseFirestore.getInstance(FIRESTORE_DATABASE_ID)
                persistenceInitialized = true
            }
        }
    }
}

data class MirrorParitySummary(
    val tasksMatch: Boolean,
    val tagOverridesMatch: Boolean,
    val tasksBaselineCount: Int,
    val tasksFirestoreCount: Int,
    val tagOverridesBaselineCount: Int,
    val tagOverridesFirestoreCount: Int
) {
    val isMatch: Boolean
        get() = tasksMatch && tagOverridesMatch
}

private data class ParitySetDiff(
    val matches: Boolean,
    val baselineCount: Int,
    val firestoreCount: Int
)

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
