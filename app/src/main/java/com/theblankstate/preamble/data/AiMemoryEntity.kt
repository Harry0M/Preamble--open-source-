package com.theblankstate.preamble.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Long-term memory fact extracted from user chat/voice.
 * Injected into system prompt so every future model call has user context.
 */
@Entity(
    tableName = "ai_memory",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["category"]),
        Index(value = ["key"]),
    ]
)
data class AiMemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val key: String,
    val value: String,
    val category: String,
    val confidence: Float,
    val source: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    @ColumnInfo(defaultValue = "0") val syncPending: Int = 1,
)

@Dao
interface AiMemoryDao {
    @Query("SELECT * FROM ai_memory WHERE userId = :userId ORDER BY lastUsedAt DESC")
    fun observe(userId: String): Flow<List<AiMemoryEntity>>

    @Query("SELECT * FROM ai_memory WHERE userId = :userId ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun snapshot(userId: String, limit: Int = 40): List<AiMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE userId = :userId AND LOWER(`key`) = LOWER(:key) LIMIT 1")
    suspend fun findByKey(userId: String, key: String): AiMemoryEntity?

    @Upsert
    suspend fun upsert(memory: AiMemoryEntity)

    @Upsert
    suspend fun upsertAll(memories: List<AiMemoryEntity>)

    @Query("DELETE FROM ai_memory WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_memory WHERE userId = :userId")
    suspend fun clear(userId: String)

    @Query("UPDATE ai_memory SET lastUsedAt = :now WHERE id IN (:ids)")
    suspend fun touch(ids: List<String>, now: Long)

    @Query("SELECT * FROM ai_memory WHERE syncPending = 1")
    suspend fun pendingSync(): List<AiMemoryEntity>

    @Query("UPDATE ai_memory SET syncPending = 0 WHERE id = :id")
    suspend fun markSynced(id: String)
}
