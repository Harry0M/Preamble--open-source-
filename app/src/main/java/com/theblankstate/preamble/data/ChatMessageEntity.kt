package com.theblankstate.preamble.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Chat history persisted to Room (offline-first) and mirrored to Firestore at
 * users/{uid}/ai_chat/{conversationId}/messages/{messageId} so conversations
 * follow the user across devices.
 *
 * Capped at last 100 user-visible messages per Phase 3 spec; older messages
 * either auto-pruned or compressed by ChatSummarizer into a single system row.
 */
@Entity(
    tableName = "ai_chat_message",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"]),
    ]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val userId: String,
    val role: String,        // "user" | "assistant" | "system" | "summary"
    val content: String,
    val timestamp: Long,
    val toolCalls: String? = null,
    val toolResults: String? = null,
    val isStreaming: Boolean = false,
    val syncPending: Int = 1,
    /** Model that produced this assistant response (e.g., "gemini-2.5-flash-lite"). Null for user/system rows. */
    val modelUsed: String? = null,
)

data class ChatConversationPreview(
    val conversationId: String,
    val lastTimestamp: Long,
    val lastContent: String?,
    val messageCount: Int,
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM ai_chat_message WHERE conversationId = :cid ORDER BY timestamp ASC")
    fun observe(cid: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_message WHERE conversationId = :cid ORDER BY timestamp ASC")
    suspend fun snapshot(cid: String): List<ChatMessageEntity>

    @Query("SELECT * FROM ai_chat_message WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ChatMessageEntity?

    @Query("SELECT * FROM ai_chat_message WHERE conversationId = :cid AND role IN ('user','assistant') ORDER BY timestamp DESC LIMIT :limit")
    suspend fun lastVisible(cid: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM ai_chat_message WHERE conversationId = :cid AND role IN ('user','assistant')")
    suspend fun visibleCount(cid: String): Int

    @Query("""
        SELECT
            c.conversationId AS conversationId,
            MAX(c.timestamp) AS lastTimestamp,
            (
                SELECT m.content
                FROM ai_chat_message m
                WHERE m.conversationId = c.conversationId
                  AND m.role IN ('user','assistant')
                ORDER BY m.timestamp DESC
                LIMIT 1
            ) AS lastContent,
            COUNT(*) AS messageCount
        FROM ai_chat_message c
        WHERE c.role IN ('user','assistant')
        GROUP BY c.conversationId
        ORDER BY lastTimestamp DESC
    """)
    fun observeConversations(): Flow<List<ChatConversationPreview>>

    @Upsert
    suspend fun upsert(msg: ChatMessageEntity)

    @Upsert
    suspend fun upsertAll(msgs: List<ChatMessageEntity>)

    @Query("DELETE FROM ai_chat_message WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_chat_message WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<String>)

    @Query("DELETE FROM ai_chat_message WHERE conversationId = :cid")
    suspend fun clear(cid: String)

    @Query("SELECT * FROM ai_chat_message WHERE syncPending = 1")
    suspend fun pendingSync(): List<ChatMessageEntity>

    @Query("UPDATE ai_chat_message SET syncPending = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM ai_chat_message WHERE conversationId = :cid AND id NOT IN (SELECT id FROM ai_chat_message WHERE conversationId = :cid ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun pruneOldest(cid: String, keep: Int)
}
