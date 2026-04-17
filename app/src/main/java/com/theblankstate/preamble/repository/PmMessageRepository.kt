package com.theblankstate.preamble.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches Personal Mode override messages from Firestore's `pm_messages` collection.
 * Uses SharedPreferences as a local cache so messages work offline.
 *
 * Admin panel se messages create/edit/delete hote hain → App Firestore se fetch karta hai
 * → Local cache mein save karta hai → HomeScreen mein override ke roop mein use karta hai.
 *
 * Usage:
 *   val greeting = PmMessageRepository.getMessage(context, "greeting", "morning")
 *   // Returns admin override if exists, else null → app uses hardcoded fallback
 */
object PmMessageRepository {

    private const val TAG = "PmMessageRepo"
    private const val PREFS_NAME = "pm_messages_cache"
    private const val KEY_MESSAGES = "messages_json"
    private const val KEY_LAST_FETCH = "last_fetch_ms"
    private const val STALE_AFTER_MS = 30 * 60 * 1000L  // 30 min cache

    data class PmMessage(
        val id: String,
        val type: String,        // greeting, smart_progress, empty_state, last_task, streak_warn, easter_egg, late_night
        val condition: String,   // morning, afternoon, evening, progress_0, progress_25, etc.
        val headline: String,
        val subtitle: String?,
        val active: Boolean,
        val targetType: String,  // all, user
        val targetUids: List<String>?,
        val priority: Int
    )

    private var cachedMessages: List<PmMessage>? = null

    /**
     * Get a message override for a specific type + condition.
     * Returns null if no override — app should use hardcoded default.
     */
    fun getMessage(context: Context, type: String, condition: String): PmMessage? {
        val messages = getCachedMessages(context)
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        return messages
            .filter { it.active && it.type == type }
            .filter { it.condition == condition || it.condition == "default" }
            .filter { msg ->
                // Target check: all = everyone, user = specific UIDs only
                msg.targetType == "all" || (msg.targetType == "user" && uid != null && msg.targetUids?.contains(uid) == true)
            }
            .sortedByDescending { it.priority }  // Higher priority wins
            .firstOrNull()
    }

    /**
     * Get all active messages for a type (e.g., all smart_progress messages).
     */
    fun getMessages(context: Context, type: String): List<PmMessage> {
        val messages = getCachedMessages(context)
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        return messages
            .filter { it.active && it.type == type }
            .filter { msg ->
                msg.targetType == "all" || (msg.targetType == "user" && uid != null && msg.targetUids?.contains(uid) == true)
            }
            .sortedByDescending { it.priority }
    }

    /**
     * Fetch from Firestore and update cache. Call from a coroutine scope.
     */
    suspend fun fetchAndCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = FirebaseFirestore.getInstance("preamble")
            val snap = db.collection("pm_messages").get().await()

            val messages = snap.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    PmMessage(
                        id = doc.id,
                        type = data["type"] as? String ?: return@mapNotNull null,
                        condition = data["condition"] as? String ?: "default",
                        headline = data["headline"] as? String ?: return@mapNotNull null,
                        subtitle = data["subtitle"] as? String,
                        active = data["active"] as? Boolean ?: true,
                        targetType = data["targetType"] as? String ?: "all",
                        targetUids = (data["targetUids"] as? List<*>)?.mapNotNull { it as? String },
                        priority = (data["priority"] as? Long)?.toInt() ?: 0
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing pm_message ${doc.id}", e)
                    null
                }
            }

            // Save to cache
            val jsonArray = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("type", msg.type)
                    put("condition", msg.condition)
                    put("headline", msg.headline)
                    put("subtitle", msg.subtitle ?: "")
                    put("active", msg.active)
                    put("targetType", msg.targetType)
                    put("targetUids", JSONArray(msg.targetUids ?: emptyList<String>()))
                    put("priority", msg.priority)
                }
                jsonArray.put(obj)
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_MESSAGES, jsonArray.toString())
                .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                .apply()

            cachedMessages = messages
            Log.d(TAG, "Cached ${messages.size} PM messages from Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch PM messages — using cached", e)
        }
    }

    /**
     * Should we re-fetch? True if cache is stale (>30 min old).
     */
    fun isStale(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)
        return System.currentTimeMillis() - lastFetch > STALE_AFTER_MS
    }

    private fun getCachedMessages(context: Context): List<PmMessage> {
        cachedMessages?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()

        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<PmMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(PmMessage(
                    id = obj.getString("id"),
                    type = obj.getString("type"),
                    condition = obj.optString("condition", "default"),
                    headline = obj.getString("headline"),
                    subtitle = obj.optString("subtitle", "").ifEmpty { null },
                    active = obj.optBoolean("active", true),
                    targetType = obj.optString("targetType", "all"),
                    targetUids = run {
                        val uids = obj.optJSONArray("targetUids")
                        if (uids != null) (0 until uids.length()).map { uids.getString(it) } else null
                    },
                    priority = obj.optInt("priority", 0)
                ))
            }
            cachedMessages = list
            list
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing cached PM messages", e)
            emptyList()
        }
    }
}
