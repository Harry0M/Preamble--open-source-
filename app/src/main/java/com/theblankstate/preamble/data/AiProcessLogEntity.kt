package com.theblankstate.preamble.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Record of every AI operation — chat, extraction, preview, reminder, etc.
 * Powers the "AI Activity" viewer in Settings.
 */
@Entity(
    tableName = "ai_process_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["op"]),
    ]
)
data class AiProcessLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val op: String,
    val provider: String,
    val model: String?,
    val input: String,
    val output: String?,
    val toolCalls: String?,
    val durationMs: Long,
    val success: Boolean,
    val error: String?,
    val thought: String?,
)

@Dao
interface AiProcessLogDao {
    @Query("SELECT * FROM ai_process_log ORDER BY timestamp DESC LIMIT :limit")
    fun observe(limit: Int = 200): Flow<List<AiProcessLogEntity>>

    @Query("SELECT * FROM ai_process_log WHERE op = :op ORDER BY timestamp DESC LIMIT :limit")
    fun observeByOp(op: String, limit: Int = 200): Flow<List<AiProcessLogEntity>>

    @Upsert
    suspend fun upsert(log: AiProcessLogEntity)

    @Query("DELETE FROM ai_process_log WHERE timestamp < :cutoff")
    suspend fun prune(cutoff: Long)

    @Query("DELETE FROM ai_process_log")
    suspend fun clear()
}
