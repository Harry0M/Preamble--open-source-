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

    private val prefs by lazy {
        appContext.getSharedPreferences("preamble_ai_memory_sync", Context.MODE_PRIVATE)
    }
    private val AI_MEMORY_LAST_SYNC_AT = "ai_memory_last_sync_at"
    private val AI_MEMORY_TTL_MS = 6 * 60 * 60 * 1000L  // 6 hours

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
        val cleanKey = cleanMemoryKey(key)
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
        pruneDuplicateKey(u, cleanKey, keepId = entity.id)
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

    suspend fun deleteByKey(key: String): Boolean = withContext(Dispatchers.IO) {
        val cleanKey = cleanMemoryKey(key)
        if (cleanKey.isBlank()) return@withContext false
        val rows = dao.snapshot(uid(), limit = 5000)
            .filter { cleanMemoryKey(it.key) == cleanKey }
        if (rows.isEmpty()) return@withContext false
        rows.forEach { deleteMemoryRow(it.id) }
        true
    }

    suspend fun cleanupDuplicateKeys(): Int = withContext(Dispatchers.IO) {
        pruneDuplicateKeys(uid())
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
        val now = System.currentTimeMillis()
        val lastSync = prefs.getLong(AI_MEMORY_LAST_SYNC_AT, 0L)

        // TTL guard: skip if synced within last 6 hours
        if (lastSync > 0L && (now - lastSync) < AI_MEMORY_TTL_MS) {
            val ageMins = (now - lastSync) / 60_000
            Log.i("COST_OPT", "[CACHE_HIT] ai_memory TTL: skipping Firestore read (cache is ${ageMins}min old, TTL=${AI_MEMORY_TTL_MS / 60_000}min) — 0 docs read")
            return@withContext 0
        }

        runCatching {
            val collection = firestore.collection("users").document(u).collection("ai_memory")
            val query = if (lastSync > 0L) {
                Log.i("COST_OPT", "[DELTA_QUERY] ai_memory: fetching only entries changed since last sync (${(now - lastSync) / 60_000}min ago)")
                collection.whereGreaterThan("lastUsedAt", lastSync)
            } else {
                Log.i("COST_OPT", "[FULL_QUERY] ai_memory: first sync, reading full collection")
                collection
            }
            val snapshot = query.get().await()
            val remote = snapshot.documents.mapNotNull { doc ->
                val key = cleanMemoryKey(doc.getString("key") ?: return@mapNotNull null)
                if (key.isBlank()) return@mapNotNull null
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
            Log.i("COST_OPT", "[DELTA_RESULT] ai_memory: read ${remote.size} docs")
            if (remote.isNotEmpty()) dao.upsertAll(remote)
            pruneDuplicateKeys(u)
            prefs.edit().putLong(AI_MEMORY_LAST_SYNC_AT, now).apply()
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
     * Ranked by importance, confidence, recency and query relevance, then capped
     * at ~400 tokens worth (roughly 1600 chars).
     */
    suspend fun buildPromptSnapshot(maxChars: Int = 1600, query: String? = null): String? {
        pruneDuplicateKeys(uid())
        val items = snapshot(limit = 40).rankForPrompt(query)
        if (items.isEmpty()) return null

        val profile = runCatching { UserProfileStore.load(appContext) }.getOrNull()
        val sb = StringBuilder()
        sb.appendLine("USER CONTEXT (long-term memory — use naturally, don't announce):")
        sb.appendLine("  Use only when relevant; latest user message overrides memory if there is conflict.")
        val hasSavedName = items.any { cleanMemoryKey(it.key) == "name" && it.value.isNotBlank() }
        if (!hasSavedName) {
            profile?.name?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  - Name: $it") }
        }
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

    private suspend fun pruneDuplicateKey(userId: String, key: String, keepId: String? = null): Int {
        return pruneDuplicateKeys(userId, onlyKey = key, keepId = keepId)
    }

    private suspend fun pruneDuplicateKeys(
        userId: String,
        onlyKey: String? = null,
        keepId: String? = null,
    ): Int {
        val rows = dao.snapshot(userId, limit = 5000)
            .filter { onlyKey == null || cleanMemoryKey(it.key) == onlyKey }
        val grouped = rows.groupBy { cleanMemoryKey(it.key) }.filterKeys { it.isNotBlank() }
        var removed = 0

        for ((key, group) in grouped) {
            if (group.size <= 1) {
                val single = group.firstOrNull()
                if (single != null && single.key != key) {
                    val normalized = single.copy(key = key, syncPending = 1)
                    dao.upsert(normalized)
                    if (isLoggedIn()) syncOne(normalized)
                }
                continue
            }

            val keep = group.firstOrNull { it.id == keepId } ?: chooseCanonical(group)
            val normalizedKeep = if (keep.key != key) keep.copy(key = key, syncPending = 1) else keep
            if (normalizedKeep != keep) {
                dao.upsert(normalizedKeep)
                if (isLoggedIn()) syncOne(normalizedKeep)
            }

            for (row in group) {
                if (row.id == keep.id) continue
                deleteMemoryRow(row.id)
                removed++
            }
        }

        if (removed > 0) Log.d(TAG, "Pruned $removed duplicate memories")
        return removed
    }

    private fun chooseCanonical(rows: List<AiMemoryEntity>): AiMemoryEntity {
        return rows.maxWith(
            compareBy<AiMemoryEntity> { it.lastUsedAt }
                .thenBy { it.createdAt }
                .thenBy { it.confidence }
        )
    }

    private suspend fun deleteMemoryRow(id: String) {
        dao.delete(id)
        if (!isLoggedIn()) return
        val u = uid()
        runCatching {
            firestore.collection("users").document(u)
                .collection("ai_memory").document(id).delete()
                .await()
        }.onFailure { Log.w(TAG, "Firestore duplicate delete failed for $id", it) }
    }

    private fun List<AiMemoryEntity>.rankForPrompt(query: String?): List<AiMemoryEntity> {
        return groupBy { cleanMemoryKey(it.key) }
            .values
            .map { chooseCanonical(it) }
            .sortedByDescending { memoryScore(it, query) }
            .take(16)
    }

    private fun cleanMemoryKey(key: String): String =
        key.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(64)

    private fun memoryScore(memory: AiMemoryEntity, query: String?): Double {
        val categoryBase = when (memory.category.lowercase()) {
            "identity" -> 5.0
            "relationship" -> 4.0
            "preference" -> 4.0
            "goal" -> 4.0
            "interest" -> 2.5
            "context" -> 2.0
            else -> 1.5
        }
        var score = categoryBase
        score += memory.confidence.coerceIn(0f, 1f).toDouble() * 2.0
        if (memory.source == "onboarding" || memory.source == "user_edit") score += 1.5

        val ageDays = ((System.currentTimeMillis() - memory.lastUsedAt).coerceAtLeast(0L)) / 86_400_000.0
        score += (2.0 - ageDays.coerceAtMost(60.0) / 30.0).coerceAtLeast(0.0)

        val tokens = queryTokens(query)
        if (tokens.isNotEmpty()) {
            val haystack = "${memory.key} ${memory.value} ${memory.category}".lowercase()
            val hits = tokens.count { haystack.contains(it) }
            score += (hits * 2.5).coerceAtMost(5.0)
        }

        if (cleanMemoryKey(memory.key) in setOf("name", "role", "language", "timezone", "primary_goals")) {
            score += 2.0
        }
        return score
    }

    private fun queryTokens(query: String?): Set<String> {
        if (query.isNullOrBlank()) return emptySet()
        val stop = setOf(
            "the", "and", "for", "you", "are", "what", "who", "when", "where", "why", "how",
            "tell", "about", "mera", "meri", "mere", "mujhe", "main", "mein", "kya", "kaise",
            "hai", "ho", "hu", "hun", "kar", "karo", "please", "pls",
        )
        return query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in stop }
            .toSet()
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
