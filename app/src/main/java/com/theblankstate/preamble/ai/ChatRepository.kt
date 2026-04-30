package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.theblankstate.preamble.data.ChatMessageDao
import com.theblankstate.preamble.data.ChatConversationPreview
import com.theblankstate.preamble.data.ChatMessageEntity
import com.theblankstate.preamble.data.PreambleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.UUID

/**
 * Single source of truth for AI chat messages.
 * Offline-first via Room; mirrors to Firestore at users/{uid}/ai_chat/{cid}/messages/{mid}.
 *
 * Retention: keeps last [HARD_CAP] visible messages per conversation, older messages
 * are summarized into a single role="summary" row by [ChatSummarizer] before pruning.
 *
 * Default conversation per user is "default" — exposed via [defaultConversationId].
 */
class ChatRepository private constructor(
    private val dao: ChatMessageDao,
    private val appContext: Context,
) {

    private val firestore by lazy { FirebaseFirestore.getInstance("preamble") }

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    fun defaultConversationId(): String = "default"

    fun observe(cid: String): Flow<List<ChatMessageEntity>> = dao.observe(cid)

    fun observeConversations(): Flow<List<ChatConversationPreview>> = dao.observeConversations()

    fun newConversationId(): String = "chat_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

    suspend fun snapshot(cid: String): List<ChatMessageEntity> = dao.snapshot(cid)

    suspend fun upsert(msg: ChatMessageEntity) = withContext(Dispatchers.IO) {
        dao.upsert(msg)
        syncOne(msg)
    }

    /**
     * Append a new message — generates id + timestamp + userId. Returns the saved row.
     *
     * @param skipSync set true when the row was created server-side (cloud path) and should
     *   not be re-mirrored to Firestore by this client. Prevents duplicate Firestore writes.
     */
    suspend fun append(
        cid: String,
        role: String,
        content: String,
        toolCalls: String? = null,
        toolResults: String? = null,
        isStreaming: Boolean = false,
        modelUsed: String? = null,
        skipSync: Boolean = false,
        id: String = UUID.randomUUID().toString(),
    ): ChatMessageEntity = withContext(Dispatchers.IO) {
        val u = uid() ?: "anonymous"
        val msg = ChatMessageEntity(
            id = id,
            conversationId = cid,
            userId = u,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            toolCalls = toolCalls,
            toolResults = toolResults,
            isStreaming = isStreaming,
            syncPending = if (skipSync) 0 else 1,
            modelUsed = modelUsed,
        )
        dao.upsert(msg)
        if (!isStreaming && !skipSync) syncOne(msg)
        msg
    }

    /** Update existing row in-place (e.g. streaming text growing). */
    suspend fun updateContent(
        id: String,
        content: String,
        isStreaming: Boolean,
        skipSync: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val existing = dao.findById(id) ?: return@withContext
        val updated = existing.copy(
            content = content,
            isStreaming = isStreaming,
            syncPending = if (skipSync || isStreaming) 0 else 1,
        )
        dao.upsert(updated)
        if (!isStreaming && !skipSync) syncOne(updated)
    }

    suspend fun updateAssistantTurn(
        id: String,
        content: String,
        toolCalls: String?,
        toolResults: String?,
        isStreaming: Boolean,
        modelUsed: String?,
        skipSync: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val existing = dao.findById(id) ?: return@withContext
        val updated = existing.copy(
            content = content,
            toolCalls = toolCalls,
            toolResults = toolResults,
            isStreaming = isStreaming,
            modelUsed = modelUsed ?: existing.modelUsed,
            syncPending = if (skipSync || isStreaming) 0 else 1,
        )
        dao.upsert(updated)
        if (!isStreaming && !skipSync) syncOne(updated)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val existing = dao.findById(id)
        dao.delete(id)
        val u = uid() ?: return@withContext
        runCatching {
            // Firestore delete — best effort
            val msgs = firestore.collection("users").document(u)
                .collection("ai_chat").document(existing?.conversationId ?: defaultConversationId())
                .collection("messages").document(id).delete()
            msgs.await()
        }
    }

    suspend fun clear(cid: String) = withContext(Dispatchers.IO) {
        dao.clear(cid)
        val u = uid() ?: return@withContext
        runCatching {
            val docs = firestore.collection("users").document(u)
                .collection("ai_chat").document(cid)
                .collection("messages").get().await()
            docs.forEach { it.reference.delete() }
        }
    }

    /**
     * Enforce retention. If visible-message count > HARD_CAP, summarizes oldest
     * (HARD_CAP - SOFT_KEEP) and prunes them. Called automatically after each
     * assistant turn in AiChatEngine.
     */
    suspend fun enforceRetention(cid: String) = withContext(Dispatchers.IO) {
        val count = dao.visibleCount(cid)
        if (count <= HARD_CAP) return@withContext
        // Summarize older portion before pruning
        val toSummarize = count - SOFT_KEEP
        if (toSummarize <= 0) return@withContext
        runCatching {
            ChatSummarizer.summarizeOldest(appContext, cid, toSummarize)
        }.onFailure { Log.w(TAG, "summarize failed before prune", it) }
        // Hard prune (keep only the last SOFT_KEEP rows; summary row(s) survive because role != user/assistant)
        // Hard cap stored rows to avoid unbounded growth
        dao.pruneOldest(cid, keep = SOFT_KEEP * 2)
    }

    /**
     * Pull remote conversation snapshot into local Room. Call on app start / sign-in.
     */
    suspend fun pullRemote(cid: String): Int = withContext(Dispatchers.IO) {
        val u = uid() ?: return@withContext 0
        runCatching {
            val docs = firestore.collection("users").document(u)
                .collection("ai_chat").document(cid)
                .collection("messages").get().await()
            val remoteRows = docs.documents.mapNotNull { doc ->
                val role = doc.getString("role") ?: return@mapNotNull null
                val content = doc.getString("content") ?: return@mapNotNull null
                ChatMessageEntity(
                    id = doc.id,
                    conversationId = cid,
                    userId = u,
                    role = role,
                    content = content,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    toolCalls = doc.getString("toolCalls"),
                    toolResults = doc.getString("toolResults"),
                    isStreaming = false,
                    syncPending = 0,
                    modelUsed = doc.getString("modelUsed"),
                )
            }.sortedBy { it.timestamp }
            val rows = filterLegacyCloudDuplicates(cid, remoteRows)
            if (rows.isNotEmpty()) dao.upsertAll(rows)
            pruneLocalLegacyCloudDuplicates(cid)
            rows.size
        }.onFailure { Log.w(TAG, "pullRemote failed", it) }.getOrDefault(0)
    }

    suspend fun flushPending() = withContext(Dispatchers.IO) {
        val pending = dao.pendingSync()
        pending.forEach { syncOne(it) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun syncOne(msg: ChatMessageEntity) {
        val u = uid() ?: return
        runCatching {
            val data = mutableMapOf<String, Any>(
                "role" to msg.role,
                "content" to msg.content,
                "timestamp" to msg.timestamp,
            )
            msg.toolCalls?.let { data["toolCalls"] = it }
            msg.toolResults?.let { data["toolResults"] = it }
            msg.modelUsed?.let { data["modelUsed"] = it }
            firestore.collection("users").document(u)
                .collection("ai_chat").document(msg.conversationId)
                .collection("messages").document(msg.id)
                .set(data, SetOptions.merge())
                .await()
            dao.markSynced(msg.id)
        }.onFailure { Log.w(TAG, "Firestore sync failed for ${msg.id}", it) }
    }

    private suspend fun filterLegacyCloudDuplicates(
        cid: String,
        remoteRows: List<ChatMessageEntity>,
    ): List<ChatMessageEntity> {
        if (remoteRows.isEmpty()) return emptyList()
        val accepted = dao.snapshot(cid).toMutableList()
        val upserts = mutableListOf<ChatMessageEntity>()

        for (row in remoteRows) {
            val existingIndex = accepted.indexOfFirst { it.id == row.id }
            if (existingIndex >= 0) {
                accepted[existingIndex] = row
                upserts += row
                continue
            }

            if (accepted.any { isLegacyCloudDuplicate(it, row) }) {
                continue
            }

            accepted += row
            upserts += row
        }

        return upserts
    }

    private suspend fun pruneLocalLegacyCloudDuplicates(cid: String) {
        val rows = dao.snapshot(cid).sortedBy { it.timestamp }
        val kept = mutableListOf<ChatMessageEntity>()
        val deleteIds = linkedSetOf<String>()

        for (row in rows) {
            val duplicate = kept.firstOrNull { isLegacyCloudDuplicate(it, row) }
            if (duplicate == null) {
                kept += row
                continue
            }

            val preferred = preferredDuplicateRow(duplicate, row)
            val discarded = if (preferred.id == duplicate.id) row else duplicate
            deleteIds += discarded.id

            if (preferred.id != duplicate.id) {
                kept.remove(duplicate)
                kept += preferred
            }
        }

        if (deleteIds.isNotEmpty()) {
            dao.deleteMany(deleteIds.toList())
        }
    }

    private fun isLegacyCloudDuplicate(a: ChatMessageEntity, b: ChatMessageEntity): Boolean {
        if (a.id == b.id || a.role != b.role) return false
        if (a.role != "user" && a.role != "assistant") return false
        if (isUuidLike(a.id) == isUuidLike(b.id)) return false
        if (abs(a.timestamp - b.timestamp) > LEGACY_DUPLICATE_WINDOW_MS) return false
        return messageFingerprint(a) == messageFingerprint(b)
    }

    private fun preferredDuplicateRow(a: ChatMessageEntity, b: ChatMessageEntity): ChatMessageEntity {
        val aUuid = isUuidLike(a.id)
        val bUuid = isUuidLike(b.id)
        if (aUuid != bUuid) return if (aUuid) a else b
        if (a.content.isBlank() != b.content.isBlank()) return if (a.content.isNotBlank()) a else b
        return if (a.timestamp <= b.timestamp) a else b
    }

    private fun messageFingerprint(row: ChatMessageEntity): String {
        val content = normalizeMessageText(row.content)
        val toolCalls = if (content.isBlank()) normalizeMessageText(row.toolCalls.orEmpty()) else ""
        return "${row.role}|$content|$toolCalls"
    }

    private fun normalizeMessageText(text: String): String =
        WHITESPACE_RE.replace(text.trim(), " ")

    private fun isUuidLike(id: String): Boolean = UUID_RE.matches(id)

    companion object {
        private const val TAG = "ChatRepository"
        const val HARD_CAP = 100
        const val SOFT_KEEP = 50
        private const val LEGACY_DUPLICATE_WINDOW_MS = 10 * 60 * 1000L
        private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val WHITESPACE_RE = Regex("\\s+")

        @Volatile private var INSTANCE: ChatRepository? = null

        fun get(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRepository(
                    PreambleDatabase.getInstance(context).chatMessageDao(),
                    context.applicationContext
                ).also { INSTANCE = it }
            }
        }
    }
}
