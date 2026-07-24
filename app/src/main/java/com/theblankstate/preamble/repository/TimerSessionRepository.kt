package com.theblankstate.preamble.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.data.FocusSession
import com.theblankstate.preamble.data.FocusSessionDao
import com.theblankstate.preamble.data.PerTaskTimerStats
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.TaskFocusSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class TimerSessionRepository(
    private val context: Context,
    private val dao: FocusSessionDao = PreambleDatabase.getInstance(context).focusSessionDao()
) {
    private val auth get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()

    suspend fun saveSession(session: FocusSession) = withContext(Dispatchers.IO) {
        try {
            // 1. Save to Room database locally
            dao.insertSession(session)

            // 2. Persist to Firestore if user is authenticated
            val userId = auth.currentUser?.uid
            if (AuthManager.isSignedIn() && userId != null) {
                val docRef = firestore.collection("users")
                    .document(userId)
                    .collection("timer_sessions")
                    .document(session.id)

                val data = mapOf(
                    "sessionId" to session.id,
                    "taskId" to session.taskId,
                    "taskName" to session.taskTitle,
                    "durationSelected" to session.durationSelectedSeconds,
                    "actualDurationCompleted" to session.actualDurationCompletedSeconds,
                    "startedAt" to session.startTimestamp,
                    "endedAt" to session.endTimestamp,
                    "completionStatus" to session.completionStatus,
                    "createdTimestamp" to session.createdTimestamp,
                    "date" to session.date,
                    "updatedAt" to System.currentTimeMillis()
                )

                docRef.set(data).await()
                Log.d("TimerSessionRepo", "Successfully synced session ${session.id} to Firestore")
            }
        } catch (e: Exception) {
            Log.e("TimerSessionRepo", "Error saving timer session", e)
        }
    }

    suspend fun getTodayFocusSeconds(): Int = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dao.getTotalFocusSecondsForDate(today)
    }

    suspend fun getTodaySessionCount(): Int = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dao.getSessionCountForDate(today)
    }

    suspend fun getTotalFocusSecondsAllTime(): Int = withContext(Dispatchers.IO) {
        dao.getTotalFocusSecondsAllTime()
    }

    suspend fun getTotalSessionCountAllTime(): Int = withContext(Dispatchers.IO) {
        dao.getTotalSessionCount()
    }

    suspend fun getTopFocusedTasks(limit: Int = 5): List<TaskFocusSummary> = withContext(Dispatchers.IO) {
        dao.getTopFocusedTasks(limit)
    }

    suspend fun getPerTaskStats(taskId: String): PerTaskTimerStats? = withContext(Dispatchers.IO) {
        dao.getPerTaskStats(taskId)
    }

    suspend fun getAllPerTaskStats(): List<PerTaskTimerStats> = withContext(Dispatchers.IO) {
        dao.getAllPerTaskStats()
    }

    suspend fun getRecentSessions(limit: Int = 20): List<FocusSession> = withContext(Dispatchers.IO) {
        dao.getRecentSessions(limit)
    }

    fun getRecentSessionsFlow(limit: Int = 20): Flow<List<FocusSession>> {
        return dao.getRecentSessionsFlow(limit)
    }

    suspend fun getWeeklyFocusData(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        val dates = mutableListOf<String>()
        for (i in 6 downTo 0) {
            val c = cal.clone() as java.util.Calendar
            c.add(java.util.Calendar.DAY_OF_YEAR, -i)
            dates.add(sdf.format(c.time))
        }
        val statsList = dao.getFocusStatsByDates(dates)
        val statsMap = statsList.associate { it.date to it.totalSeconds }
        dates.map { date ->
            val seconds = statsMap[date] ?: 0
            val dayName = try {
                val parsed = sdf.parse(date)
                SimpleDateFormat("EEE", Locale.US).format(parsed ?: Date())
            } catch (e: Exception) {
                ""
            }
            dayName to seconds
        }
    }
}
