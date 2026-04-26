package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.theblankstate.preamble.data.AiMemoryDao
import com.theblankstate.preamble.data.AiMemoryEntity
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.UserProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Single source of truth for long-term AI memory.
 * Backed by Room (fast read, offline) and mirrored to Firestore users/{uid}/ai_memory/{id}
 * so memory survives re-install and syncs across devices.
 */
class AiMemoryRepository private constructor(
    private val dao: AiMemoryDao,
    private val appContext: Context,
) {
    private val firestore by lazy { FirebaseFirestore.getInstance("preamble") }

    /** Always returns a usable userId — real Firebase uid or stable local fallback. */
    private fun uid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: LOCAL_USER

    private fun isLoggedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null

    fun observe(): Flow<List<AiMemoryEntity>> {
        return dao.observe(uid())
    }

    suspend fun snapshot(limit: Int = 40): List<AiMemoryEntity> {
        return dao.snapshot(uid(), limit)
    }

    /**
     * Upsert a memory fact. Deduplicates by lowercase-key: newer value overwrites
     * when confidence >= existing confidence.
     */
    suspend fun save(
        key: String,
        value: String,
        category: String,
        confidence: Float,
        source: String,
    ): AiMemoryEntity? = withContext(Dispatchers.IO) {
        val u = uid()
        val cleanKey = key.trim().lowercase()
        val cleanValue = value.trim()
        if (cleanKey.isBlank() || cleanValue.isBlank()) return@withContext null

        val existing = dao.findByKey(u, cleanKey)
        val now = System.currentTimeMillis()
        val entity = if (existing != null) {
            if (confidence < existing.confidence && existing.value.equals(cleanValue, ignoreCase = true)) {
                // Re-confirm existing fact — bump lastUsedAt only
                existing.copy(lastUsedAt = now, syncPending = 1)
            } else {
                existing.copy(
                    value = cleanValue,
                    category = category,
                    confidence = maxOf(confidence, existing.confidence),
                    source = source,
                    lastUsedAt = now,
                    syncPending = 1,
                )
            }
        } else {
            AiMemoryEntity(
                id = UUID.randomUUID().toString(),
                userId = u,
                key = cleanKey,
                value = cleanValue,
                category = category,
                confidence = confidence,
                source = source,
                createdAt = now,
                lastUsedAt = now,
                syncPending = 1,
            )
        }
        dao.upsert(entity)
        if (isLoggedIn()) syncOne(entity)
        entity
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
        if (!isLoggedIn()) return@withContext
        val u = uid()
        runCatching {
            firestore.collection("users").document(u)
                .collection("ai_memory").document(id).delete()
        }.onFailure { Log.w(TAG, "Firestore delete failed", it) }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val u = uid()
        dao.clear(u)
        if (!isLoggedIn()) return@withContext
        runCatching {
            val batch = firestore.collection("users").document(u).collection("ai_memory")
            val docs = batch.get().await()
            docs.forEach { it.reference.delete() }
        }.onFailure { Log.w(TAG, "Firestore clearAll failed", it) }
    }

    suspend fun touch(ids: List<String>) {
        if (ids.isEmpty()) return
        dao.touch(ids, System.currentTimeMillis())
    }

    /**
     * Pull remote memory into local Room. Call after sign-in / app start.
     */
    suspend fun pullRemote(): Int = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext 0
        val u = uid()
        runCatching {
            val snapshot = firestore.collection("users").document(u)
                .collection("ai_memory").get().await()
            val remote = snapshot.documents.mapNotNull { doc ->
                val key = doc.getString("key") ?: return@mapNotNull null
                val value = doc.getString("value") ?: return@mapNotNull null
                AiMemoryEntity(
                    id = doc.id,
                    userId = u,
                    key = key,
                    value = value,
                    category = doc.getString("category") ?: "context",
                    confidence = (doc.getDouble("confidence") ?: 0.8).toFloat(),
                    source = doc.getString("source") ?: "chat",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    lastUsedAt = doc.getLong("lastUsedAt") ?: System.currentTimeMillis(),
                    syncPending = 0,
                )
            }
            if (remote.isNotEmpty()) dao.upsertAll(remote)
            remote.size
        }.onFailure { Log.w(TAG, "pullRemote failed", it) }.getOrDefault(0)
    }

    /**
     * Flush any locally-pending memories to Firestore.
     */
    suspend fun flushPending() = withContext(Dispatchers.IO) {
        val pending = dao.pendingSync()
        pending.forEach { syncOne(it) }
    }

    /**
     * Build compact, token-budgeted context block for injection into system prompt.
     * Sorted by recency, capped at ~400 tokens worth (roughly 1600 chars).
     */
    suspend fun buildPromptSnapshot(maxChars: Int = 1600): String? {
        val items = snapshot(limit = 40)
        if (items.isEmpty()) return null

        val profile = runCatching { UserProfileStore.load(appContext) }.getOrNull()
        val sb = StringBuilder()
        sb.appendLine("USER CONTEXT (long-term memory — use naturally, don't announce):")
        profile?.name?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  - Name: $it") }
        profile?.role?.label?.let { sb.appendLine("  - Role: $it") }
        profile?.effectiveGoals
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.label }
            ?.let { sb.appendLine("  - Primary goals: $it") }

        val grouped = items.groupBy { it.category }
        val order = listOf("identity", "relationship", "preference", "goal", "interest", "context")
        val usedIds = mutableListOf<String>()
        for (cat in order + (grouped.keys - order.toSet())) {
            val rows = grouped[cat] ?: continue
            if (rows.isEmpty()) continue
            sb.appendLine("  [${cat.uppercase()}]")
            for (m in rows) {
                val line = "    - ${m.key}: ${m.value}"
                if (sb.length + line.length > maxChars) break
                sb.appendLine(line)
                usedIds.add(m.id)
            }
            if (sb.length > maxChars) break
        }
        if (usedIds.isNotEmpty()) touch(usedIds)
        return sb.toString().trimEnd()
    }

    private suspend fun syncOne(entity: AiMemoryEntity) {
        if (!isLoggedIn()) return
        val u = uid()
        runCatching {
            val data = mapOf(
                "key" to entity.key,
                "value" to entity.value,
                "category" to entity.category,
                "confidence" to entity.confidence,
                "source" to entity.source,
                "createdAt" to entity.createdAt,
                "lastUsedAt" to entity.lastUsedAt,
            )
            firestore.collection("users").document(u)
                .collection("ai_memory").document(entity.id)
                .set(data, SetOptions.merge())
                .await()
            dao.markSynced(entity.id)
        }.onFailure { Log.w(TAG, "Firestore sync failed for ${entity.id}", it) }
    }

    /**
     * Call after user signs in. Migrates any memories saved under LOCAL_USER
     * to the real Firebase uid, then flushes everything to Firestore.
     */
    suspend fun migrateOnLogin() = withContext(Dispatchers.IO) {
        val realUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext
        val localMemories = dao.snapshot(LOCAL_USER, limit = 5000)
        if (localMemories.isNotEmpty()) {
            val migrated = localMemories.map { it.copy(userId = realUid, syncPending = 1) }
            dao.upsertAll(migrated)
            dao.clear(LOCAL_USER)
            Log.d(TAG, "Migrated ${migrated.size} local memories to uid=$realUid")
        }
        // Pull remote first, then flush pending
        pullRemote()
        flushPending()
    }

    companion object {
        private const val TAG = "AiMemoryRepo"
        private const val LOCAL_USER = "local_user"

        @Volatile private var INSTANCE: AiMemoryRepository? = null

        fun get(context: Context): AiMemoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiMemoryRepository(
                    PreambleDatabase.getInstance(context).aiMemoryDao(),
                    context.applicationContext
                ).also { INSTANCE = it }
            }
        }
    }
}
