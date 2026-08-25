package com.theblankstate.preamble.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.data.DailyFocusStats
import com.theblankstate.preamble.data.FocusSession
import com.theblankstate.preamble.data.FocusSessionDao
import com.theblankstate.preamble.data.PerTaskTimerStats
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.TaskFocusSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerSessionRepository(
    private val context: Context,
    private val dao: FocusSessionDao = PreambleDatabase.getInstance(context).focusSessionDao()
) {
    private val auth get() = FirebaseAuth.getInstance()

    // Explicitly target Preamble's named Firestore database instance ("preamble")
    private val firestore: FirebaseFirestore
        get() = try {
            FirebaseFirestore.getInstance("preamble")
        } catch (_: Exception) {
            FirebaseFirestore.getInstance()
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeUid: String? = null
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("preamble_timer_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        const val TAG = "TimerCloudSync"
        private const val KEY_UNSYNCED_IDS = "unsynced_session_ids"
        private const val PREFS_LAST_TIMER_SYNC = "last_timer_sync_at"
        private const val MAX_OFFLINE_QUEUE_SIZE = 200
        private const val MAX_BATCH_SYNC_SIZE = 50
    }

    init {
        startSync()
    }

    /**
     * Start real-time Firestore sync & auth state listener
     */
    fun startSync() {
        if (authStateListener != null) return

        Log.i(TAG, "Initializing TimerCloudSync for database='preamble'")
        registerNetworkCallback()

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val userId = firebaseAuth.currentUser?.uid
            Log.d(TAG, "AuthStateListener triggered: uid=$userId signedIn=${AuthManager.isSignedIn()}")
            handleAuthChanged(userId)
        }

        try {
            auth.addAuthStateListener(authStateListener!!)
            handleAuthChanged(auth.currentUser?.uid)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add AuthStateListener", e)
        }
    }

    private fun handleAuthChanged(userId: String?) {
        if (userId == activeUid) return
        activeUid = userId

        if (userId != null && AuthManager.isSignedIn()) {
            scope.launch {
                syncAllLocalToCloud()
                fetchSessionsFromCloud()
                syncUnsyncedSessions()
            }
        }
    }

    /**
     * Fetch timer sessions changed since last sync (delta). Falls back to full fetch on first sync.
     * Called on app foreground and after saving a new session.
     */
    suspend fun fetchSessionsFromCloud() = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: run {
            Log.d(TAG, "fetchSessionsFromCloud skipped: No authenticated user")
            return@withContext
        }
        try {
            val lastSync = prefs.getLong(PREFS_LAST_TIMER_SYNC, 0L)
            val now = System.currentTimeMillis()

            val baseQuery = firestore.collection("users")
                .document(userId)
                .collection("timer_sessions")
                .orderBy("startedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)

            val query = if (lastSync > 0L) {
                Log.i("COST_OPT", "[DELTA_QUERY] timer_sessions: fetching only sessions since ${lastSync}ms ago (limit 100) — SAVES reading all historical sessions")
                baseQuery.whereGreaterThan("startedAt", lastSync).limit(100)
            } else {
                Log.i("COST_OPT", "[FULL_QUERY] timer_sessions: first sync, fetching recent 200 sessions (one-time cost)")
                baseQuery.limit(200)
            }

            val snapshot = query.get().await()
            var count = 0
            for (doc in snapshot.documents) {
                val session = parseDocumentToSession(doc)
                if (session != null) {
                    dao.insertSession(session)
                    count++
                }
            }
            prefs.edit().putLong(PREFS_LAST_TIMER_SYNC, now).apply()
            Log.i("COST_OPT", "[DELTA_RESULT] timer_sessions: read ${snapshot.documents.size} docs, stored $count sessions for uid=$userId")
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sessions fetch failed for uid=$userId: ${e.message}")
        }
    }

    /**
     * Push all local Room DB focus sessions to Firestore (Initial Bootstrap / Device Sync)
     */
    suspend fun syncAllLocalToCloud() = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext
        try {
            val localSessions = dao.getRecentSessions(limit = 200)
            if (localSessions.isEmpty()) return@withContext

            Log.d(TAG, "Pushing ${localSessions.size} local sessions to cloud for uid=$userId...")
            for (session in localSessions) {
                pushSessionToCloudInternal(userId, session)
            }
            Log.i(TAG, "All local sessions synced to cloud for uid=$userId")
        } catch (e: Exception) {
            Log.w(TAG, "syncAllLocalToCloud failed for uid=$userId", e)
        }
    }

    /**
     * Parse Firestore DocumentSnapshot to FocusSession
     */
    private fun parseDocumentToSession(doc: DocumentSnapshot): FocusSession? {
        return try {
            val id = doc.getString("sessionId") ?: doc.id
            val taskId = doc.getString("taskId")
            val taskTitle = doc.getString("taskName") ?: doc.getString("taskTitle")
            val startTimestamp = doc.getLong("startedAt") ?: doc.getLong("startTimestamp") ?: return null
            val endTimestamp = doc.getLong("endedAt") ?: doc.getLong("endTimestamp") ?: startTimestamp
            val durationSelected = doc.getLong("durationSelected")?.toInt()
                ?: doc.getLong("durationSelectedSeconds")?.toInt() ?: 1500
            val actualDurationCompleted = doc.getLong("actualDurationCompleted")?.toInt()
                ?: doc.getLong("actualDurationCompletedSeconds")?.toInt() ?: durationSelected
            val durationSeconds = actualDurationCompleted
            val completionStatus = doc.getString("completionStatus") ?: "COMPLETED"
            val createdTimestamp = doc.getLong("createdTimestamp") ?: startTimestamp
            val date = doc.getString("date") ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startTimestamp))

            FocusSession(
                id = id,
                taskId = taskId,
                taskTitle = taskTitle,
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                durationSeconds = durationSeconds,
                durationSelectedSeconds = durationSelected,
                actualDurationCompletedSeconds = actualDurationCompleted,
                completionStatus = completionStatus,
                createdTimestamp = createdTimestamp,
                date = date
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse session document ${doc.id}", e)
            null
        }
    }

    /**
     * Save completed timer session (Local-First + Instant Cloud Sync & Offline Queueing)
     */
    suspend fun saveSession(session: FocusSession) = withContext(Dispatchers.IO) {
        Log.i(TAG, "saveSession called: id=${session.id} taskId=${session.taskId} title='${session.taskTitle}' duration=${session.actualDurationCompletedSeconds}s")
        try {
            // 1. Local-first Room DB save (Instant 0ms latency)
            dao.insertSession(session)
            Log.d(TAG, "Saved session ${session.id} to local Room DB")

            // 2. Persist to Firestore if authenticated
            val userId = auth.currentUser?.uid
            if (AuthManager.isSignedIn() && userId != null) {
                pushSessionToCloudInternal(userId, session)
            } else {
                Log.w(TAG, "User not authenticated, queuing session ${session.id} offline")
                markSessionUnsynced(session.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving timer session locally", e)
        }
    }

    private suspend fun pushSessionToCloudInternal(userId: String, session: FocusSession) {
        try {
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("timer_sessions")
                .document(session.id)

            val data = mapOf(
                "sessionId" to session.id,
                "taskId" to session.taskId,
                "taskName" to session.taskTitle,
                "durationSelected" to session.durationSelectedSeconds,
                "actualDurationCompleted" to session.actualDurationCompletedSeconds,
                "startedAt" to session.startTimestamp,
                "endedAt" to session.endTimestamp,
                "completionStatus" to session.completionStatus,
                "createdTimestamp" to session.createdTimestamp,
                "date" to session.date,
                "updatedAt" to System.currentTimeMillis()
            )

            docRef.set(data).await()
            markSessionSynced(session.id)
            Log.i(TAG, "FIRESTORE SYNC SUCCESS: Saved session ${session.id} to cloud for uid=$userId")
        } catch (e: Exception) {
            Log.w(TAG, "FIRESTORE SYNC FAILED: Network/Permission error for session ${session.id}, queued offline", e)
            markSessionUnsynced(session.id)
        }
    }

    /**
     * Sync bounded offline queue sessions to Firestore when network is restored
     */
    suspend fun syncUnsyncedSessions() = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext
        val unsyncedIds = getUnsyncedSessionIds().toList().take(MAX_BATCH_SYNC_SIZE)

        if (unsyncedIds.isEmpty()) {
            Log.d(TAG, "syncUnsyncedSessions: Offline queue is empty")
            return@withContext
        }

        Log.i(TAG, "OFFLINE RETRY SYNC: Syncing ${unsyncedIds.size} pending sessions to cloud for uid=$userId...")

        val allRecentSessions = dao.getRecentSessions(limit = 100).associateBy { it.id }

        for (id in unsyncedIds) {
            val session = allRecentSessions[id]
            if (session != null) {
                try {
                    pushSessionToCloudInternal(userId, session)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed retry sync for session ${session.id}, halting batch", e)
                    break // Stop batch if network still down
                }
            } else {
                markSessionSynced(id) // Remove stale ID
            }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "NETWORK RESTORED: Triggering offline sync & cloud fetch")
                scope.launch {
                    syncUnsyncedSessions()
                    fetchSessionsFromCloud()
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
            Log.d(TAG, "Network callback registered for TimerCloudSync")
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to register network callback", exception)
        }
    }

    private fun getUnsyncedSessionIds(): Set<String> {
        return prefs.getStringSet(KEY_UNSYNCED_IDS, emptySet()) ?: emptySet()
    }

    private fun markSessionUnsynced(id: String) {
        val set = getUnsyncedSessionIds().toMutableSet()
        set.add(id)

        if (set.size > MAX_OFFLINE_QUEUE_SIZE) {
            val trimmed = set.toList().takeLast(MAX_OFFLINE_QUEUE_SIZE).toSet()
            prefs.edit().putStringSet(KEY_UNSYNCED_IDS, trimmed).apply()
        } else {
            prefs.edit().putStringSet(KEY_UNSYNCED_IDS, set).apply()
        }
    }

    private fun markSessionSynced(id: String) {
        val set = getUnsyncedSessionIds().toMutableSet()
        if (set.remove(id)) {
            prefs.edit().putStringSet(KEY_UNSYNCED_IDS, set).apply()
        }
    }

    suspend fun getTodayFocusSeconds(): Int = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dao.getTotalFocusSecondsForDate(today)
    }

    suspend fun getTodaySessionCount(): Int = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dao.getSessionCountForDate(today)
    }

    suspend fun getTotalFocusSecondsAllTime(): Int = withContext(Dispatchers.IO) {
        dao.getTotalFocusSecondsAllTime()
    }

    suspend fun getTotalSessionCountAllTime(): Int = withContext(Dispatchers.IO) {
        dao.getTotalSessionCount()
    }

    suspend fun getTopFocusedTasks(limit: Int = 5): List<TaskFocusSummary> = withContext(Dispatchers.IO) {
        dao.getTopFocusedTasks(limit)
    }

    suspend fun getPerTaskStats(taskId: String, taskTitle: String? = null): PerTaskTimerStats? = withContext(Dispatchers.IO) {
        if (!taskTitle.isNullOrBlank()) {
            dao.getPerTaskStatsByTitleOrId(taskId, taskTitle)
        } else {
            dao.getPerTaskStats(taskId)
        }
    }

    suspend fun getDailyFocusHistoryForTask(taskId: String, taskTitle: String? = null): List<DailyFocusStats> = withContext(Dispatchers.IO) {
        dao.getDailyFocusHistoryForTask(taskId, taskTitle)
    }

    suspend fun getAllPerTaskStats(): List<PerTaskTimerStats> = withContext(Dispatchers.IO) {
        dao.getAllPerTaskStats()
    }

    suspend fun getRecentSessions(limit: Int = 20): List<FocusSession> = withContext(Dispatchers.IO) {
        dao.getRecentSessions(limit)
    }

    fun getRecentSessionsFlow(limit: Int = 20): Flow<List<FocusSession>> {
        return dao.getRecentSessionsFlow(limit)
    }

    suspend fun getSessionsForTask(taskId: String, taskTitle: String? = null): List<FocusSession> = withContext(Dispatchers.IO) {
        if (!taskTitle.isNullOrBlank()) {
            dao.getSessionsForTaskByTitleOrId(taskId, taskTitle)
        } else {
            dao.getSessionsForTask(taskId)
        }
    }

    suspend fun getWeeklyFocusData(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        val dates = mutableListOf<String>()
        for (i in 6 downTo 0) {
            val c = cal.clone() as java.util.Calendar
            c.add(java.util.Calendar.DAY_OF_YEAR, -i)
            dates.add(sdf.format(c.time))
        }
        val statsList = dao.getFocusStatsByDates(dates)
        val statsMap = statsList.associate { it.date to it.totalSeconds }
        dates.map { date ->
            val seconds = statsMap[date] ?: 0
            val dayName = try {
                val parsed = sdf.parse(date)
                SimpleDateFormat("EEE", Locale.US).format(parsed ?: Date())
            } catch (e: Exception) {
                ""
            }
            dayName to seconds
        }
    }
}
