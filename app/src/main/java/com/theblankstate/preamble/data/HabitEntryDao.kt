package com.theblankstate.preamble.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitEntryDao {

    // ── Daily tracking ──

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getEntry(habitId: String, date: String): HabitEntry?

    @Query("SELECT * FROM habit_entries WHERE date = :date")
    fun getEntriesForDate(date: String): Flow<List<HabitEntry>>

    @Query("SELECT * FROM habit_entries WHERE date = :date AND isCompleted = 1")
    fun getCompletedForDate(date: String): Flow<List<HabitEntry>>

    @Query("SELECT * FROM habit_entries WHERE date = :date")
    suspend fun getEntriesForDateSync(date: String): List<HabitEntry>

    // ── Stats: date range ──

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getEntriesInRange(habitId: String, from: String, to: String): List<HabitEntry>

    // ── Heatmap ──

    @Query("SELECT date, isCompleted FROM habit_entries WHERE habitId = :habitId ORDER BY date ASC")
    suspend fun getHeatmapData(habitId: String): List<HabitHeatmapEntry>

    @Query("SELECT date, isCompleted FROM habit_entries WHERE habitId = :habitId AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getHeatmapDataInRange(habitId: String, from: String, to: String): List<HabitHeatmapEntry>

    // ── Streak calculation ──

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND isCompleted = 1 ORDER BY date DESC")
    suspend fun getCompletedEntries(habitId: String): List<HabitEntry>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId ORDER BY date DESC")
    suspend fun getAllEntriesForHabit(habitId: String): List<HabitEntry>

    // ── Bulk stats ──

    @Query("SELECT COUNT(*) FROM habit_entries WHERE habitId = :habitId AND date BETWEEN :from AND :to")
    suspend fun getTotalEntries(habitId: String, from: String, to: String): Int

    @Query("SELECT COUNT(*) FROM habit_entries WHERE habitId = :habitId AND date BETWEEN :from AND :to AND isCompleted = 1")
    suspend fun getCompletedCount(habitId: String, from: String, to: String): Int

    @Query("SELECT COUNT(*) FROM habit_entries WHERE habitId = :habitId AND isCompleted = 1")
    suspend fun getTotalCompletedCount(habitId: String): Int

    // ── Write operations ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: HabitEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<HabitEntry>)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId")
    suspend fun deleteAllForHabit(habitId: String)

    @Query("DELETE FROM habit_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: String)

    // ── Habit list queries ──

    @Query("SELECT DISTINCT habitId FROM habit_entries WHERE date = :date AND isCompleted = 1")
    suspend fun getCompletedHabitIdsForDate(date: String): List<String>

    @Query("SELECT habitId, COUNT(*) as total, SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed FROM habit_entries WHERE date BETWEEN :from AND :to GROUP BY habitId")
    suspend fun getHabitStatsForRange(from: String, to: String): List<HabitRangeStats>
}

data class HabitRangeStats(
    val habitId: String,
    val total: Int,
    val completed: Int
)
