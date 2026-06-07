package com.theblankstate.preamble.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SyncMutationDao {
    @Query("SELECT * FROM sync_mutations ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<SyncMutation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mutation: SyncMutation): Long

    @Update
    suspend fun update(mutation: SyncMutation)

    @Delete
    suspend fun delete(mutation: SyncMutation)

    @Query("DELETE FROM sync_mutations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_mutations WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)

    @Query("SELECT COUNT(*) FROM sync_mutations")
    suspend fun getCount(): Int
}
