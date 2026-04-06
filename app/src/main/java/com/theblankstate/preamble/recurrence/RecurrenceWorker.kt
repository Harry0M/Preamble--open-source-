package com.theblankstate.preamble.recurrence

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.repository.TaskRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class RecurrenceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val dao = app.database.taskDao()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = TaskRepository.todayString()

        // Only pre-generate today + tomorrow as physical rows.
        // Calendar view handles all other dates via virtual expansion (no DB rows needed).
        // Physical rows are only needed for: HomeScreen today list + tomorrow's alarm scheduling.
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 2)
        val toDate = sdf.format(cal.time)

        val templates = dao.getAllRecurrenceTemplates()
        var generated = 0
        Log.d("Recurrence", "Worker: found ${templates.size} templates, range=$today → $toDate")

        for (template in templates) {
            // Skip Google Calendar templates — Google handles its own recurrence expansion
            if (template.source == "google_calendar") continue
            // Skip rollover templates — they exist as exactly one task instance
            if (template.recurrenceType == "rollover") continue
            
            val dates = RecurrenceGenerator.generateDates(template, today, toDate)
            for (date in dates) {
                val exists = dao.instanceExistsForDate(template.id, date)
                if (exists == 0) {
                    val instance = template.copy(
                        id = UUID.randomUUID().toString(),
                        createdDate = date,
                        isCompleted = false,
                        completedTimestamp = null,
                        createdTimestamp = System.currentTimeMillis(),
                        updatedTimestamp = System.currentTimeMillis(),
                        recurrenceType = null,
                        recurrenceInterval = null,
                        recurrenceDays = null,
                        recurrenceEndDate = null,
                        recurrenceParentId = template.id
                    )
                    dao.insertTask(instance)
                    generated++
                    Log.d("Recurrence", "Worker: materialized '${template.title}' for $date")
                }
            }
        }
        Log.d("Recurrence", "Worker: DONE — generated $generated physical instances")
        return Result.success()
    }

    companion object {
        private const val TAG = "RecurrenceWorker"
        const val WORK_NAME = "recurrence_generation"
    }
}
