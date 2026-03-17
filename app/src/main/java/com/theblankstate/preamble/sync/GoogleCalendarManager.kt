package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.theblankstate.preamble.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Manages Google Calendar integration:
 * - OAuth2 sign-in with Calendar read-write scope
 * - Fetching events from all user's calendars
 * - Creating, updating, deleting events for two-way sync
 * - Converting events to app Task model
 */
object GoogleCalendarManager {

    private const val TAG = "GoogleCalendarManager"
    private const val PREFS_NAME = "google_calendar_prefs"
    private const val KEY_LINKED = "calendar_linked"
    private const val KEY_ACCOUNT_EMAIL = "calendar_account_email"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 1000L

    private val _isLinked = MutableStateFlow(false)
    val isLinked: StateFlow<Boolean> = _isLinked.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long>(0)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _linkedEmail = MutableStateFlow<String?>(null)
    val linkedEmail: StateFlow<String?> = _linkedEmail.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isLinked.value = prefs.getBoolean(KEY_LINKED, false)
        _linkedEmail.value = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC, 0)
    }

    /**
     * Build the GoogleSignInClient with Calendar scope for linking.
     */
    fun getSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(CalendarScopes.CALENDAR),
                Scope("https://www.googleapis.com/auth/tasks")
            )
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Called after successful sign-in Activity result.
     * Saves linked state.
     */
    fun onSignInSuccess(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LINKED, true)
            .putString(KEY_ACCOUNT_EMAIL, email)
            .apply()
        _isLinked.value = true
        _linkedEmail.value = email
        Log.d(TAG, "Google Calendar linked: $email")
    }

    /**
     * Unlink Google Calendar.
     */
    fun unlink(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LINKED, false)
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_LAST_SYNC)
            .apply()
        _isLinked.value = false
        _linkedEmail.value = null
        _lastSyncTime.value = 0

        // Sign out from the calendar-specific GoogleSignIn
        try {
            getSignInClient(context).signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out calendar", e)
        }
        Log.d(TAG, "Google Calendar unlinked")
    }

    /**
     * Fetch all calendar events and return them as Tasks.
     * Fetches events from 1 year ago to 1 year ahead.
     * @param updatedAfter If non-null, only fetch events updated after this timestamp (incremental sync)
     */
    suspend fun fetchCalendarEvents(context: Context, updatedAfter: Long? = null): List<Task> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.e(TAG, "No signed-in account found")
            return@withContext emptyList()
        }

        try {
            _isSyncing.value = true

            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(CalendarScopes.CALENDAR)
            )
            credential.selectedAccount = account.account

            val calendarService = Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("Preamble")
                .build()

            val allEvents = mutableListOf<Task>()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeSdf = SimpleDateFormat("HH:mm", Locale.US)

            // Fetch from 6 months ago to 1 year ahead
            val now = System.currentTimeMillis()
            val sixMonthsAgo = now - (180L * 24 * 60 * 60 * 1000)
            val oneYearAhead = now + (365L * 24 * 60 * 60 * 1000)

            val timeMin = DateTime(sixMonthsAgo)
            val timeMax = DateTime(oneYearAhead)

            // Get all calendar list entries
            val calendarList = calendarService.calendarList().list().execute()
            val calendars = calendarList.items ?: emptyList()

            Log.d(TAG, "Found ${calendars.size} calendars")

            for (cal in calendars) {
                try {
                    var pageToken: String? = null
                    do {
                        val eventsRequest = calendarService.events().list(cal.id)
                            .setTimeMin(timeMin)
                            .setTimeMax(timeMax)
                            .setSingleEvents(true)
                            .setOrderBy("startTime")
                            .setMaxResults(250)
                            .setPageToken(pageToken)

                        // For incremental sync, only fetch events updated since last sync
                        if (updatedAfter != null) {
                            eventsRequest.setUpdatedMin(DateTime(updatedAfter))
                            // updatedMin requires orderBy=updated, not startTime
                            eventsRequest.setOrderBy("updated")
                            eventsRequest.setSingleEvents(false)
                        }

                        val eventsResult = eventsRequest.execute()
                        val events = eventsResult.items ?: emptyList()

                        for (event in events) {
                            val task = eventToTask(event, cal.id, sdf, timeSdf)
                            if (task != null) {
                                allEvents.add(task)
                            }
                        }

                        pageToken = eventsResult.nextPageToken
                    } while (pageToken != null)

                    Log.d(TAG, "Fetched events from calendar: ${cal.summary}")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error fetching events from calendar ${cal.summary}", e)
                }
            }

            // Update last sync time
            val syncTime = System.currentTimeMillis()
            _lastSyncTime.value = syncTime
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, syncTime)
                .apply()

            Log.d(TAG, "Total events fetched: ${allEvents.size}")
            allEvents
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching calendar events", e)
            emptyList()
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Convert a Google Calendar Event to our Task model.
     */
    private fun eventToTask(
        event: Event,
        calendarId: String,
        sdf: SimpleDateFormat,
        timeSdf: SimpleDateFormat
    ): Task? {
        val title = event.summary ?: return null

        // Get the date from start
        val start = event.start ?: return null
        val startDate: Date
        val deadlineTime: String?

        if (start.dateTime != null) {
            // Timed event
            startDate = Date(start.dateTime.value)
            deadlineTime = timeSdf.format(startDate)
        } else if (start.date != null) {
            // All-day event (like festivals, holidays)
            startDate = Date(start.date.value)
            deadlineTime = null
        } else {
            return null
        }

        val dateStr = sdf.format(startDate)

        // Create a stable ID based on the Google event ID so we can deduplicate
        val stableId = "gcal_${event.id}"

        return Task(
            id = stableId,
            title = title,
            isCompleted = false,
            createdDate = dateStr,
            createdTimestamp = startDate.time,
            completedTimestamp = null,
            deadlineTime = deadlineTime,
            updatedTimestamp = event.updated?.value ?: System.currentTimeMillis(),
            source = "google_calendar",
            description = event.description,
            tags = "Google Calendar",
            googleCalendarId = calendarId
        )
    }

    // ── Write Infrastructure ──

    private suspend fun <T> retryOnFailure(block: suspend () -> T): T {
        var lastException: Throwable? = null
        for (attempt in 0..MAX_RETRIES) {
            try { return block() }
            catch (e: Throwable) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Attempt $attempt failed, retrying...", e)
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastException!!
    }

    private fun buildCalendarService(context: Context): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(CalendarScopes.CALENDAR)
        )
        credential.selectedAccount = account.account
        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Preamble").build()
    }

    // ── Write Methods ──

    suspend fun createCalendarEvent(
        context: Context,
        title: String,
        date: String,
        deadlineTime: String?,
        calendarId: String = "primary",
        description: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext null
        try {
            retryOnFailure {
                val service = buildCalendarService(context) ?: error("No Calendar service")
                val event = Event().setSummary(title)
                if (description != null) event.description = description

                if (deadlineTime != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    val startDateTime = DateTime("${date}T${deadlineTime}:00")
                    event.start = EventDateTime().setDateTime(startDateTime)
                    val cal = java.util.Calendar.getInstance()
                    cal.time = sdf.parse("${date}T${deadlineTime}:00")!!
                    cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
                    val endDateTime = DateTime(sdf.format(cal.time))
                    event.end = EventDateTime().setDateTime(endDateTime)
                } else {
                    event.start = EventDateTime().setDate(DateTime(date))
                    event.end = EventDateTime().setDate(DateTime(date))
                }

                val created = service.events().insert(calendarId, event).execute()
                Log.d(TAG, "Created calendar event: ${created.id}")
                created.id
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create calendar event", e)
            null
        }
    }

    suspend fun updateCalendarEvent(
        context: Context,
        eventId: String,
        calendarId: String,
        title: String,
        date: String,
        deadlineTime: String?,
        description: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext false
        try {
            retryOnFailure {
                val service = buildCalendarService(context) ?: error("No Calendar service")
                val event = service.events().get(calendarId, eventId).execute()
                event.summary = title
                if (description != null) event.description = description

                if (deadlineTime != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    val startDateTime = DateTime("${date}T${deadlineTime}:00")
                    event.start = EventDateTime().setDateTime(startDateTime)
                    val cal = java.util.Calendar.getInstance()
                    cal.time = sdf.parse("${date}T${deadlineTime}:00")!!
                    cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
                    event.end = EventDateTime().setDateTime(DateTime(sdf.format(cal.time)))
                } else {
                    event.start = EventDateTime().setDate(DateTime(date))
                    event.end = EventDateTime().setDate(DateTime(date))
                }

                service.events().update(calendarId, eventId, event).execute()
                Log.d(TAG, "Updated calendar event $eventId")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update calendar event $eventId", e)
            false
        }
    }

    suspend fun deleteCalendarEvent(
        context: Context,
        eventId: String,
        calendarId: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext false
        try {
            retryOnFailure {
                val service = buildCalendarService(context) ?: error("No Calendar service")
                service.events().delete(calendarId, eventId).execute()
                Log.d(TAG, "Deleted calendar event $eventId from calendar $calendarId")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete calendar event $eventId", e)
            false
        }
    }
}
