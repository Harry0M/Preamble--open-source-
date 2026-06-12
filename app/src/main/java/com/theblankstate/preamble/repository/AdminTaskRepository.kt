package com.theblankstate.preamble.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.theblankstate.preamble.data.AdminTask
import com.theblankstate.preamble.analytics.AnalyticsManager
import com.theblankstate.preamble.notification.TaskNotificationService
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Fetches admin-pushed broadcast tasks from Firestore and manages
 * dismiss state locally via SharedPreferences.
 *
 * Also generates feature-discovery tasks based on user's current
 * feature adoption state (e.g. "Try custom theme" if not yet unlocked).
 */
class AdminTaskRepository(private val appContext: Context) {

    companion object {
        private const val TAG = "AdminTaskRepo"
        private const val FIRESTORE_DB_ID = "preamble"
        private const val COLLECTION = "broadcasts"
        private const val PREFS_NAME = "admin_task_prefs"
        private const val KEY_DISMISSED = "dismissed_ids"
        private const val KEY_INTERACTED = "interacted_ids"
    }

    private val firestore = FirebaseFirestore.getInstance(FIRESTORE_DB_ID)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _adminTasks = MutableStateFlow<List<AdminTask>>(emptyList())
    val adminTasks: StateFlow<List<AdminTask>> = _adminTasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Dismissed / interacted tracking ──

    private fun getDismissedIds(): Set<String> {
        return prefs.getStringSet(KEY_DISMISSED, emptySet()) ?: emptySet()
    }

    private fun getInteractedIds(): Set<String> {
        return prefs.getStringSet(KEY_INTERACTED, emptySet()) ?: emptySet()
    }

    fun dismissTask(taskId: String) {
        val current = getDismissedIds().toMutableSet()
        current.add(taskId)
        prefs.edit().putStringSet(KEY_DISMISSED, current).apply()

        // PostHog: Announcement dismiss track karo
        val task = _adminTasks.value.find { it.id == taskId }
        if (task != null) {
            AnalyticsManager.trackAnnouncementDismissed(taskId, task.type)
        }

        // Remove from live list
        _adminTasks.value = _adminTasks.value.filter { it.id != taskId }
    }

    fun markInteracted(taskId: String) {
        // PostHog: Announcement action click track karo
        val task = _adminTasks.value.find { it.id == taskId }
        if (task != null) {
            AnalyticsManager.trackAnnouncementClicked(taskId, task.type, task.actionLabel)
        }

        val current = getInteractedIds().toMutableSet()
        current.add(taskId)
        prefs.edit().putStringSet(KEY_INTERACTED, current).apply()
        // Also dismiss after interaction
        dismissTask(taskId)
    }

    // ── Fetch from Firestore ──

    suspend fun refresh() {
        if (_isLoading.value) return
        _isLoading.value = true
        try {
            val remoteTasks = fetchRemoteBroadcasts()
            val featureTasks = generateFeatureDiscoveryTasks()
            val dismissed = getDismissedIds()
            val now = System.currentTimeMillis()
            val uid = FirebaseAuth.getInstance().currentUser?.uid

            val combined = (remoteTasks + featureTasks)
                .filter { it.active }
                .filter { it.id !in dismissed }
                .filter { it.expiresAt == null || it.expiresAt > now }
                .filter { msg ->
                    // Client-side targeted broadcast filtering
                    msg.targetType == "all" || 
                    (msg.targetType == "single" && uid != null && msg.targetUids?.contains(uid) == true) ||
                    (msg.targetType == "group" && uid != null && msg.targetUids?.contains(uid) == true)
                }
                .filter { !shouldHideFeatureTask(it) }
                .sortedByDescending { it.priority }

            _adminTasks.value = combined
            Log.d(TAG, "Loaded ${combined.size} admin tasks (${remoteTasks.size} remote + ${featureTasks.size} feature)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh admin tasks", e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchRemoteBroadcasts(): List<AdminTask> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(COLLECTION)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    AdminTask.fromMap(doc.id, data)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse broadcast ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore fetch failed", e)
            emptyList()
        }
    }

    // ── Feature Discovery Tasks ──

    private fun generateFeatureDiscoveryTasks(): List<AdminTask> {
        val tasks = mutableListOf<AdminTask>()

        // 1. Custom theme
        if (!isThemeUnlocked()) {
            tasks.add(
                AdminTask(
                    id = "feature_${AdminTask.FEATURE_THEME_CUSTOM}",
                    title = "Personalize Your App",
                    description = "Make Preamble truly yours — unlock custom color themes and pick a color that matches your style.",
                    type = "feature",
                    actionLabel = "Try Now",
                    deepLink = "preamble://settings/theme",
                    featureKey = AdminTask.FEATURE_THEME_CUSTOM,
                    priority = 50,
                    createdAt = System.currentTimeMillis(),
                    tags = listOf(
                        com.theblankstate.preamble.data.AdminTaskTag("New Feature", "#6366F1")
                    )
                )
            )
        }

        // 2. Google Calendar link
        if (!isCalendarLinked()) {
            tasks.add(
                AdminTask(
                    id = "feature_${AdminTask.FEATURE_CALENDAR_LINK}",
                    title = "Link Google Calendar",
                    description = "See your calendar events alongside tasks — never miss a meeting or deadline again.",
                    type = "feature",
                    actionLabel = "Link Now",
                    deepLink = "preamble://settings/calendar",
                    featureKey = AdminTask.FEATURE_CALENDAR_LINK,
                    priority = 80,
                    createdAt = System.currentTimeMillis(),
                    tags = listOf(
                        com.theblankstate.preamble.data.AdminTaskTag("Integration", "#4285F4")
                    )
                )
            )
        }

        // 3. Voice task sync to Google Tasks
        if (isCalendarLinked() && !isVoiceSyncEnabled()) {
            tasks.add(
                AdminTask(
                    id = "feature_${AdminTask.FEATURE_VOICE_SYNC}",
                    title = "Sync Voice Tasks to Google",
                    description = "Voice tasks you create are automatically synced to Google Tasks — access them from any device.",
                    type = "feature",
                    actionLabel = "Enable",
                    deepLink = "preamble://settings/calendar",
                    featureKey = AdminTask.FEATURE_VOICE_SYNC,
                    priority = 40,
                    createdAt = System.currentTimeMillis(),
                    tags = listOf(
                        com.theblankstate.preamble.data.AdminTaskTag("Productivity", "#34A853")
                    )
                )
            )
        }

        // 4. Smart task breakdown
        tasks.add(
            AdminTask(
                id = "feature_${AdminTask.FEATURE_SMART_BREAKDOWN}",
                title = "Try Smart Task Breakdown",
                description = "Let AI break down complex tasks into actionable subtasks automatically. Just add a task and tap the AI button.",
                type = "feature",
                actionLabel = "Try It",
                deepLink = "preamble://home",
                featureKey = AdminTask.FEATURE_SMART_BREAKDOWN,
                priority = 60,
                createdAt = System.currentTimeMillis(),
                tags = listOf(
                    com.theblankstate.preamble.data.AdminTaskTag("AI", "#FF6F00")
                )
            )
        )

        // 5. Persistent notification
        if (!isPersistentNotificationEnabled()) {
            tasks.add(
                AdminTask(
                    id = "feature_${AdminTask.FEATURE_PERMANENT_NOTIFICATION}",
                    title = "Stay on Track",
                    description = "Enable the persistent notification to quickly add tasks and see today's progress without opening the app.",
                    type = "feature",
                    actionLabel = "Enable",
                    deepLink = "preamble://settings/notifications",
                    featureKey = AdminTask.FEATURE_PERMANENT_NOTIFICATION,
                    priority = 30,
                    createdAt = System.currentTimeMillis(),
                    tags = listOf(
                        com.theblankstate.preamble.data.AdminTaskTag("Tip", "#00BCD4")
                    )
                )
            )
        }

        return tasks
    }

    /**
     * Check if a feature-discovery task should be hidden because
     * the user already has that feature enabled/used.
     */
    private fun shouldHideFeatureTask(task: AdminTask): Boolean {
        val key = task.featureKey ?: return false
        return when (key) {
            AdminTask.FEATURE_THEME_CUSTOM -> isThemeUnlocked()
            AdminTask.FEATURE_CALENDAR_LINK -> isCalendarLinked()
            AdminTask.FEATURE_VOICE_SYNC -> isVoiceSyncEnabled() || !isCalendarLinked()
            AdminTask.FEATURE_PERMANENT_NOTIFICATION -> isPersistentNotificationEnabled()
            // Smart breakdown is always shown (one-time discovery, dismissed on interaction)
            else -> false
        }
    }

    // ── Feature state checks ──

    private fun isThemeUnlocked(): Boolean {
        return true // Theme customisation is always unlocked — no ads/premium
    }

    private fun isCalendarLinked(): Boolean {
        return GoogleCalendarManager.isLinked.value
    }

    private fun isVoiceSyncEnabled(): Boolean {
        // Check if Google Tasks is linked (voice sync requires it)
        return com.theblankstate.preamble.sync.GoogleTasksManager.isLinked.value
    }

    private fun isPersistentNotificationEnabled(): Boolean {
        return TaskNotificationService.isEnabled(appContext)
    }
}
