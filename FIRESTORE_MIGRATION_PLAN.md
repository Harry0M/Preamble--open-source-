# Firestore Migration Plan

## Overview
Migrate from Firebase Realtime Database (RTDB) to Cloud Firestore for better scalability, compound queries, and future feature potential.

**Estimated Effort:** 2-3 days
**Risk Level:** Low (data model maps 1:1, offline-first architecture preserved)

---

## Phase 1: Dependencies & Configuration

### 1.1 Update build.gradle.kts
- Remove: `firebase-database` dependency
- Keep: `firebase-firestore` (already in libs.versions.toml v26.1.2)

### 1.2 Deploy Firestore Rules
- File already exists: `firebase-firestore-rules.rules`
- Deploy to Firebase Console or via CLI

---

## Phase 2: Create FirestoreTaskSyncManager

### 2.1 New File Structure
```
sync/
├── FirebaseTaskSyncManager.kt  (DELETE - old RTDB)
└── FirestoreTaskSyncManager.kt (NEW - Firestore)
```

### 2.2 Key Method Mapping (RTDB → Firestore)

| RTDB Method | Firestore Equivalent |
|-------------|---------------------|
| `database.getReference("users/$uid/tasks")` | `firestore.collection("users/$uid/tasks")` |
| `ref.setValue(task).await()` | `docRef.set(task).await()` |
| `ref.removeValue().await()` | `docRef.delete().await()` |
| `ref.addValueEventListener(listener)` | `collection.addSnapshotListener(listener)` |
| `ref.get().await()` | `collection.get().await()` |
| `database.setPersistenceEnabled(true)` | `firestore.firestoreSettings { isPersistenceEnabled = true }` |

### 2.3 Data Model Changes

**RemoteTask (unchanged - same fields):**
```kotlin
data class FirestoreTask(
    val id: String = "",
    val title: String = "",
    val isCompleted: Boolean = false,
    val createdDate: String = "",           // "yyyy-MM-dd"
    val createdTimestamp: Long = 0L,
    val completedTimestamp: Long? = null,
    val deadlineTime: String? = null,       // "HH:mm"
    val updatedTimestamp: Long = 0L,
    val source: String = "local",
    val priority: Int = 0,
    val description: String? = null,
    val recurrenceType: String? = null,
    val recurrenceInterval: Int? = null,
    val recurrenceDays: String? = null,
    val recurrenceEndDate: String? = null,
    val recurrenceParentId: String? = null,
    val parentTaskId: String? = null,
    val tags: String? = null,               // Comma-separated (can upgrade to List later)
    val googleCalendarId: String? = null,
    val googleRecurrenceInfo: String? = null
)
```

**TagOverride (unchanged):**
```kotlin
data class FirestoreTagOverride(
    val googleId: String = "",
    val tags: String = "",
    val updatedTimestamp: Long = 0L
)
```

### 2.4 Firestore Paths
```
users/
  └── {uid}/
        └── tasks/
        │     └── {taskId}  (document)
        └── tagOverrides/
              └── {googleId}  (document)
```

---

## Phase 3: Implementation Details

### 3.1 FirestoreTaskSyncManager.kt - Core Methods

```kotlin
class FirestoreTaskSyncManager(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dao: TaskDao,
    private val context: Context,
    private val appScope: CoroutineScope
) {
    // Collection references
    private fun tasksCollection(uid: String) =
        firestore.collection("users").document(uid).collection("tasks")

    private fun tagOverridesCollection(uid: String) =
        firestore.collection("users").document(uid).collection("tagOverrides")

    private fun taskDoc(uid: String, taskId: String) =
        tasksCollection(uid).document(taskId)

    // Push task to Firestore
    suspend fun pushTask(task: Task) {
        if (task.source != "local") return
        val uid = auth.currentUser?.uid ?: return
        rememberLocalWrite(task.id)
        pendingUpserts.add(task.id)
        try {
            taskDoc(uid, task.id).set(FirestoreTask.fromLocal(task)).await()
        } catch (e: Exception) {
            Log.e(TAG, "pushTask failed", e)
        } finally {
            pendingUpserts.remove(task.id)
        }
    }

    // Delete task from Firestore
    suspend fun deleteTask(taskId: String) {
        val uid = auth.currentUser?.uid ?: return
        rememberLocalWrite(taskId)
        pendingDeletes.add(taskId)
        try {
            taskDoc(uid, taskId).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "deleteTask failed", e)
        } finally {
            pendingDeletes.remove(taskId)
        }
    }

    // Real-time listener (replaces ValueEventListener)
    private fun attachRealtimeListener(uid: String) {
        tasksListenerRegistration = tasksCollection(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Tasks listener error", error)
                    return@addSnapshotListener
                }
                val remoteTasks = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FirestoreTask::class.java)?.toLocal(doc.id)
                } ?: emptyList()
                appScope.launch {
                    mergeRemoteIntoLocal(remoteTasks)
                }
            }
    }

    // Sync all local tasks to Firestore
    suspend fun syncAllLocalToRemote() {
        val uid = auth.currentUser?.uid ?: return
        val localTasks = dao.getAllTasks().filter { it.source == "local" }

        // Use batched writes for efficiency (max 500 per batch)
        localTasks.chunked(500).forEach { chunk ->
            firestore.runBatch { batch ->
                chunk.forEach { task ->
                    rememberLocalWrite(task.id)
                    batch.set(taskDoc(uid, task.id), FirestoreTask.fromLocal(task))
                }
            }.await()
        }
    }

    // Force bidirectional sync
    suspend fun forceSyncBidirectional() {
        val uid = auth.currentUser?.uid ?: return
        syncAllLocalToRemote()
        try {
            val snapshot = tasksCollection(uid).get().await()
            val remoteTasks = snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirestoreTask::class.java)?.toLocal(doc.id)
            }
            mergeRemoteIntoLocal(remoteTasks)
        } catch (e: Exception) {
            Log.e(TAG, "forceSyncBidirectional failed", e)
        }
    }
}
```

### 3.2 Tag Override Methods

```kotlin
suspend fun pushTagOverride(googleId: String, tags: String) {
    val uid = auth.currentUser?.uid ?: return
    try {
        tagOverridesCollection(uid).document(googleId).set(
            mapOf(
                "googleId" to googleId,
                "tags" to tags,
                "updatedTimestamp" to System.currentTimeMillis()
            )
        ).await()
    } catch (e: Exception) {
        Log.e(TAG, "pushTagOverride failed", e)
    }
}

suspend fun deleteTagOverride(googleId: String) {
    val uid = auth.currentUser?.uid ?: return
    try {
        tagOverridesCollection(uid).document(googleId).delete().await()
    } catch (e: Exception) {
        Log.e(TAG, "deleteTagOverride failed", e)
    }
}

private fun attachTagOverridesListener(uid: String) {
    tagOverridesListenerRegistration = tagOverridesCollection(uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val overrides = snapshot?.documents?.mapNotNull { doc ->
                val googleId = doc.getString("googleId") ?: doc.id
                val tags = doc.getString("tags") ?: return@mapNotNull null
                val ts = doc.getLong("updatedTimestamp") ?: System.currentTimeMillis()
                TaskTagOverride(googleId, tags, ts)
            } ?: emptyList()
            appScope.launch {
                mergeTagOverridesFromRemote(overrides)
            }
        }
}
```

### 3.3 Offline Persistence Setup

```kotlin
companion object {
    private var persistenceConfigured = false

    fun configureFirestore(firestore: FirebaseFirestore) {
        if (persistenceConfigured) return
        synchronized(this) {
            if (persistenceConfigured) return
            firestore.firestoreSettings = firestoreSettings {
                isPersistenceEnabled = true
                cacheSizeBytes = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
            }
            persistenceConfigured = true
        }
    }
}
```

### 3.4 Flush Pending Writes

```kotlin
suspend fun flushPendingWrites(timeoutMs: Long = 8000L): Boolean {
    return withTimeoutOrNull(timeoutMs) {
        try {
            firestore.waitForPendingWrites().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "flushPendingWrites failed", e)
            false
        }
    } ?: false
}
```

---

## Phase 4: Data Migration

### 4.1 One-Time Migration (For Existing Users)

Create a migration manager that runs once on app update:

```kotlin
class RtdbToFirestoreMigration(
    private val rtdb: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
    private val prefs: SharedPreferences
) {
    private val MIGRATION_KEY = "rtdb_to_firestore_migrated"

    suspend fun migrateIfNeeded(uid: String): Boolean {
        if (prefs.getBoolean(MIGRATION_KEY, false)) return true

        try {
            // 1. Read all tasks from RTDB
            val tasksSnapshot = rtdb.getReference("users/$uid/tasks").get().await()
            val tasks = tasksSnapshot.children.mapNotNull { child ->
                child.getValue(RemoteTask::class.java)?.let { it to child.key }
            }

            // 2. Read all tag overrides from RTDB
            val tagsSnapshot = rtdb.getReference("users/$uid/tagOverrides").get().await()
            val overrides = tagsSnapshot.children.mapNotNull { child ->
                child.key to mapOf(
                    "googleId" to child.key,
                    "tags" to child.child("tags").getValue(String::class.java),
                    "updatedTimestamp" to (child.child("updatedTimestamp").getValue(Long::class.java) ?: 0L)
                )
            }

            // 3. Batch write to Firestore
            val tasksRef = firestore.collection("users").document(uid).collection("tasks")
            val tagsRef = firestore.collection("users").document(uid).collection("tagOverrides")

            tasks.chunked(500).forEach { chunk ->
                firestore.runBatch { batch ->
                    chunk.forEach { (task, id) ->
                        if (id != null && id != "_flush_marker") {
                            batch.set(tasksRef.document(id), task)
                        }
                    }
                }.await()
            }

            overrides.chunked(500).forEach { chunk ->
                firestore.runBatch { batch ->
                    chunk.forEach { (id, data) ->
                        if (id != null) {
                            batch.set(tagsRef.document(id), data)
                        }
                    }
                }.await()
            }

            // 4. Mark migration complete
            prefs.edit().putBoolean(MIGRATION_KEY, true).apply()
            Log.i(TAG, "Migration complete: ${tasks.size} tasks, ${overrides.size} tag overrides")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            return false
        }
    }
}
```

### 4.2 Migration Trigger Point

In `MainActivity.kt` or `AuthManager.kt`:

```kotlin
// After successful sign-in
val migrationManager = RtdbToFirestoreMigration(rtdb, firestore, prefs)
lifecycleScope.launch {
    val uid = auth.currentUser?.uid ?: return@launch
    if (migrationManager.migrateIfNeeded(uid)) {
        // Start using FirestoreTaskSyncManager
        firestoreSyncManager.startListening()
    }
}
```

---

## Phase 5: Update DI / Initialization

### 5.1 PreambleDatabase.kt Changes

```kotlin
// Remove RTDB sync manager creation
// Add Firestore sync manager creation

val firestoreSyncManager: FirestoreTaskSyncManager by lazy {
    FirestoreTaskSyncManager.configureFirestore(FirebaseFirestore.getInstance())
    FirestoreTaskSyncManager(
        firestore = FirebaseFirestore.getInstance(),
        auth = FirebaseAuth.getInstance(),
        dao = taskDao(),
        context = context,
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
}
```

### 5.2 Repository Update

Update `TaskRepository` constructor:
```kotlin
class TaskRepository(
    private val dao: TaskDao,
    private val syncManager: FirestoreTaskSyncManager? = null  // Changed from FirebaseTaskSyncManager
)
```

---

## Phase 6: Testing Checklist

### 6.1 Sync Tests
- [ ] Create task → appears on other device
- [ ] Update task → syncs correctly
- [ ] Delete task → removed on other device
- [ ] Complete task → completion state syncs
- [ ] Offline create → syncs when online
- [ ] Offline edit → syncs with correct timestamp

### 6.2 Tag Override Tests
- [ ] Add tag to Google Calendar event → persists across devices
- [ ] Add tag to Google Task → persists across devices
- [ ] Remove tag → deletion syncs
- [ ] Tag on recurring event parent → inherited by instances

### 6.3 Edge Cases
- [ ] Sign out/sign in → data preserved
- [ ] App clear data → re-syncs from Firestore
- [ ] Conflict resolution (edit same task on 2 devices)
- [ ] Large batch (100+ tasks) → batched write succeeds

### 6.4 Migration Tests
- [ ] Existing RTDB user → data migrates to Firestore
- [ ] Migration only runs once
- [ ] Migration handles empty data gracefully

---

## Phase 7: Cleanup

### 7.1 Files to Delete
- `firebase-rtdb-rules.json` (already marked deleted in git status)
- `FIREBASE_SECURITY_SETUP.md` (already marked deleted in git status)
- `FirebaseTaskSyncManager.kt` (after Firestore manager is working)

### 7.2 Dependencies to Remove
Remove from `build.gradle.kts`:
```kotlin
implementation(libs.firebase.database)  // Remove this line
```

Remove from `libs.versions.toml`:
```toml
firebaseDatabase = "21.0.0"  // Remove this line
```

### 7.3 ProGuard Rules Update
In `proguard-rules.pro`, ensure:
```proguard
-keep class com.theblankstate.preamble.sync.FirestoreTask { *; }
-keep class com.theblankstate.preamble.sync.FirestoreTagOverride { *; }
```

---

## Phase 8: Future Features (Post-Migration)

### 8.1 Compound Queries (Immediate)
```kotlin
// Overdue incomplete tasks
tasksCollection(uid)
    .whereEqualTo("isCompleted", false)
    .whereLessThan("deadlineTime", today)
    .get()

// High priority tasks due today
tasksCollection(uid)
    .whereEqualTo("createdDate", today)
    .whereGreaterThanOrEqualTo("priority", 3)
    .get()
```

### 8.2 Array-Based Tags (Later)
Change `tags: String?` to `tags: List<String>?` for:
```kotlin
// Query by tag
tasksCollection(uid)
    .whereArrayContains("tags", "work")
    .get()

// Atomic tag operations
taskDoc.update("tags", FieldValue.arrayUnion("urgent"))
taskDoc.update("tags", FieldValue.arrayRemove("done"))
```

### 8.3 Shared Lists (Future)
```
sharedLists/
  └── {listId}/
        ├── name: "Family Groceries"
        ├── ownerUid: "user1"
        ├── members: ["user1", "user2"]
        └── tasks/ (subcollection)
```

### 8.4 User Analytics Document (Future)
```kotlin
// Store aggregated stats
users/{uid}/stats/productivity
  ├── tasksCreatedTotal: 1234
  ├── tasksCompletedTotal: 987
  ├── currentStreak: 5
  └── lastActiveDate: "2026-03-31"
```

---

## Implementation Order

1. **Day 1:**
   - Create `FirestoreTaskSyncManager.kt`
   - Update dependency (remove RTDB, keep Firestore)
   - Test basic push/pull operations

2. **Day 2:**
   - Implement real-time listeners
   - Implement tag override sync
   - Create migration manager
   - Test migration with existing data

3. **Day 3:**
   - Update Repository and DI
   - Full integration testing
   - Cleanup old files
   - Deploy Firestore rules

---

## Rollback Plan

If issues arise post-release:
1. Keep RTDB data intact (don't delete during migration)
2. Feature flag to switch between sync managers
3. Dual-write period (write to both, read from Firestore)

```kotlin
// Feature flag approach
val useFirestore = remoteConfig.getBoolean("use_firestore_sync")
val syncManager = if (useFirestore) firestoreSyncManager else rtdbSyncManager
```

---

## Google Calendar/Tasks ID Preservation

**No changes needed!** Your current architecture already handles this correctly:

| Field | Current Behavior | After Migration |
|-------|-----------------|-----------------|
| Task.id | `gcal_<eventId>` or `gtask_<taskId>` | Same |
| Task.googleCalendarId | Stored in Room only | Same (not synced) |
| TagOverride.googleId | Stripped prefix, synced to Firebase | Same path in Firestore |

The `tagOverrides/{googleId}` → Firestore `tagOverrides/{googleId}` mapping is 1:1.
