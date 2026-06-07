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
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.gson.Gson
import com.theblankstate.preamble.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.TimeZone

/**
 * Manages Google Calendar integration:
 * - OAuth2 sign-in with Calendar read-write scope
 * - Fetching events from all user's calendars
 * - Creating, updating, deleting events for two-way sync
 * - Converting events to app Task model
 */
/**
 * Result from fetchCalendarEvents containing events and sync metadata.
 */
data class CalendarSyncResult(
    val events: List<Task>,
    val isIncremental: Boolean  // true if ALL calendars used sync tokens (no full re-sync)
)

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

    private val _isManualSyncing = MutableStateFlow(false)
    val isManualSyncing: StateFlow<Boolean> = _isManualSyncing.asStateFlow()

    /** True only while a background WorkManager sync is running (silent, no spinner normally). */
    private val _isBgSyncing = MutableStateFlow(false)
    val isBgSyncing: StateFlow<Boolean> = _isBgSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long>(0)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _linkedEmail = MutableStateFlow<String?>(null)
    val linkedEmail: StateFlow<String?> = _linkedEmail.asStateFlow()

    /**
     * Mutex to prevent multiple concurrent full syncs from writing duplicate data to Room.
     * Background and foreground syncs each have their own guard; this prevents
     * the same-type sync from running twice simultaneously.
     */
    private val syncMutex = Mutex()

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

        // Clear all sync tokens
        clearSyncTokens(context)

        // Sign out from the calendar-specific GoogleSignIn
        try {
            getSignInClient(context).signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out calendar", e)
        }
        Log.d(TAG, "Google Calendar unlinked")
    }

    fun resetSyncState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_SYNC)
            .apply()
        _lastSyncTime.value = 0
        clearSyncTokens(context)
        Log.d(TAG, "Google Calendar sync state reset")
    }

    // ── Sync Token Storage ──

    private const val SYNC_TOKEN_PREFS = "google_calendar_sync_tokens"

    private fun getSyncToken(context: Context, calendarId: String): String? {
        return context.getSharedPreferences(SYNC_TOKEN_PREFS, Context.MODE_PRIVATE)
            .getString("sync_token_$calendarId", null)
    }

    private fun saveSyncToken(context: Context, calendarId: String, token: String?) {
        if (token == null) {
            context.getSharedPreferences(SYNC_TOKEN_PREFS, Context.MODE_PRIVATE)
                .edit().remove("sync_token_$calendarId").apply()
        } else {
            context.getSharedPreferences(SYNC_TOKEN_PREFS, Context.MODE_PRIVATE)
                .edit().putString("sync_token_$calendarId", token).apply()
        }
    }

    private fun clearSyncTokens(context: Context) {
        context.getSharedPreferences(SYNC_TOKEN_PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    /**
     * Returns true if at least one calendar has a stored syncToken.
     * Used by onResume to skip incremental sync when no token exists yet
     * (i.e. the background full sync has not completed yet after first link).
     */
    fun hasAnySyncToken(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SYNC_TOKEN_PREFS, Context.MODE_PRIVATE)
        return prefs.all.any { (key, value) ->
            key.startsWith("sync_token_") && value is String && value.isNotEmpty()
        }
    }

    /**
     * Fetch calendar events using Google's recommended sync token approach.
     *
     * - **Full sync** (no sync token stored): Fetches all events with timeMin/timeMax,
     *   stores the nextSyncToken from the response for each calendar.
     * - **Incremental sync** (sync token exists): Sends the stored syncToken,
     *   API returns ONLY changed/cancelled events since last sync.
     * - **410 error** (token expired): Clears token and automatically does a full re-sync.
     *
     * @param forceFullSync If true, clears sync tokens and does a full re-sync
     */
    /**
     * Fetch all calendar events.
     *
     * - **isManual = true**: Pull-to-refresh triggered by user. Shows manual sync spinner.
     * - **isBackground = true**: Called from background WorkManager. Sets isBgSyncing flag.
     * - **forceFullSync = true**: Clears all syncTokens and forces a full re-fetch.
     *
     * Incremental vs Full is determined per-calendar by presence of a stored syncToken.
     * Background and foreground syncs have SEPARATE flags and can run concurrently.
     */
    suspend fun fetchCalendarEvents(
        context: Context,
        forceFullSync: Boolean = false,
        isManual: Boolean = false,
        isBackground: Boolean = false,
        onProgressUpdate: suspend (List<Task>) -> Unit = {}
    ): CalendarSyncResult = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.e(TAG, "❌ FETCH_ABORTED: no signed-in Google account")
            return@withContext CalendarSyncResult(emptyList(), isIncremental = false)
        }

        Log.i(TAG, "FETCH_CALENDAR_EVENTS account=${account.email} " +
            "forceFullSync=$forceFullSync isManual=$isManual isBackground=$isBackground")
        Log.i(TAG, "  STATE: isBgSyncing=${_isBgSyncing.value} isManualSyncing=${_isManualSyncing.value} " +
            "mutex.isLocked=${syncMutex.isLocked}")

        // Only block if the SAME sync type is already running to prevent duplicates.
        // Use syncMutex.tryLock() for race-condition-free check.
        if (isBackground) {
            if (!syncMutex.tryLock()) {
                Log.w(TAG, "⚡ SKIPPED (mutex locked) — background sync already running")
                return@withContext CalendarSyncResult(emptyList(), isIncremental = true)
            }
            Log.d(TAG, "  Mutex acquired for background sync")
        } else {
            if (_isManualSyncing.value) {
                Log.w(TAG, "⚡ SKIPPED — manual/pull-to-refresh sync already in progress")
                return@withContext CalendarSyncResult(emptyList(), isIncremental = true)
            }
        }

        if (forceFullSync) {
            Log.i(TAG, "  ⚠️ FORCE_FULL: clearing all sync tokens")
            clearSyncTokens(context)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC, 0).apply()
        }

        try {
            if (isManual) _isManualSyncing.value = true
            if (isBackground) _isBgSyncing.value = true
            _isSyncing.value = true
            Log.i(TAG, "  FLAGS_SET: isSyncing=true isManual=$isManual isBackground=$isBackground")

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

            // Cache: master recurring event ID -> parsed RRULE (thread-safe)
            val masterRruleCache = mutableMapOf<String, ParsedRecurrence>()
            val cacheMutex = Mutex()

            // Get all calendar list entries
            val calendarList = calendarService.calendarList().list().execute()
            val calendars = calendarList.items ?: emptyList()

            Log.i(TAG, "  CALENDARS_FOUND: ${calendars.size} calendars")
            calendars.forEachIndexed { i, cal ->
                val tok = getSyncToken(context, cal.id)
                Log.i(TAG, "    [$i] '${cal.summary}' id=${cal.id} " +
                    "token=${if (tok != null) "EXISTS(${tok.take(15)}...)" else "NONE→FULL_SYNC"}")
            }

            // Track whether ALL calendars used incremental sync
            var allIncremental = calendars.isNotEmpty()
            // Track whether ANY calendar had a partial failure (410 / error returning empty)
            var anyCalendarFailed = false

            // Fetch all calendars in parallel for speed
            val allEvents = mutableListOf<Task>()
            coroutineScope {
                val jobs = calendars.map { cal ->
                    async(Dispatchers.IO) {
                        fetchEventsForCalendar(
                            calendarService, cal, context,
                            masterRruleCache, cacheMutex,
                            onProgressUpdate
                        )
                    }
                }
                val calendarResults = jobs.awaitAll()
                for (result in calendarResults) {
                    allEvents.addAll(result.first)
                    if (!result.second) {
                        allIncremental = false
                        // If a calendar returned 0 events AND wasn't incremental,
                        // it likely had a 410 or error — mark as partial failure
                        if (result.first.isEmpty()) anyCalendarFailed = true
                    }
                }
            }

            // SAFETY: If any calendar failed (410/error) and returned empty while others
            // returned only incremental changes, treat the whole result as incremental.
            // This prevents the full sync path from deleting all events from healthy calendars.
            val effectiveIncremental = if (anyCalendarFailed && allEvents.isNotEmpty()) {
                Log.w(TAG, "⚠️ SAFETY: Some calendars failed — forcing incremental mode to prevent mass deletion")
                true
            } else {
                allIncremental
            }

            // Update last sync time
            val syncTime = System.currentTimeMillis()
            _lastSyncTime.value = syncTime
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC, syncTime).apply()

            Log.i(TAG, "FETCH_COMPLETE: totalEvents=${allEvents.size} " +
                "allIncremental=$allIncremental anyCalendarFailed=$anyCalendarFailed " +
                "effectiveIncremental=$effectiveIncremental " +
                "mode=${if (effectiveIncremental) "🔄 INCREMENTAL (safe)" else "🔄 FULL"}")
            CalendarSyncResult(allEvents, isIncremental = effectiveIncremental)
        } catch (e: Throwable) {
            Log.e(TAG, "❌ FETCH_FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            // Throw instead of returning empty — returning empty with isIncremental=false
            // causes syncCalendarEvents (full sync path) to DELETE all existing events!
            throw e
        } finally {
            _isSyncing.value = false
            if (isManual) _isManualSyncing.value = false
            if (isBackground) _isBgSyncing.value = false
            if (isBackground && syncMutex.isLocked) {
                try { syncMutex.unlock() } catch (_: IllegalStateException) { /* already unlocked */ }
            }
            Log.i(TAG, "  FLAGS_CLEARED: isSyncing=false isManual=$isManual isBackground=$isBackground")
        }
    }

    /**
     * Fetch events for a single calendar using Google's syncToken approach.
     *
     * - If a syncToken is stored for this calendar: incremental sync (only changes returned).
     * - If no syncToken: full sync with timeMin/timeMax, saves nextSyncToken for next time.
     * - On 410 Gone (token expired): clears token, queues a background full sync, returns empty.
     *
     * Returns (events, wasIncremental).
     */
    private suspend fun fetchEventsForCalendar(
        calendarService: Calendar,
        cal: com.google.api.services.calendar.model.CalendarListEntry,
        context: Context,
        masterRruleCache: MutableMap<String, ParsedRecurrence>,
        cacheMutex: Mutex,
        onProgressUpdate: suspend (List<Task>) -> Unit
    ): Pair<List<Task>, Boolean> {
        val events = mutableListOf<Task>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timeSdf = SimpleDateFormat("HH:mm", Locale.US)

        // Determine sync mode from stored syncToken
        val storedSyncToken = getSyncToken(context, cal.id)
        val wasIncremental = storedSyncToken != null

        Log.i(TAG, "►► SYNC_MODE cal='${cal.summary}' id='${cal.id}' " +
            "mode=${if (wasIncremental) "INCREMENTAL" else "FULL"} " +
            "storedToken=${if (storedSyncToken != null) storedSyncToken.take(20)+"..." else "null"}")

        try {
            var pageToken: String? = null
            var lastNextSyncToken: String? = null

            do {
                val eventsRequest = calendarService.events().list(cal.id)
                    .setSingleEvents(true)
                    .setMaxResults(250)
                    .setShowDeleted(true)
                    .setPageToken(pageToken)

                if (wasIncremental) {
                    // Incremental: send syncToken — Google returns ONLY changes since last sync.
                    // Do NOT set timeMin/timeMax or orderBy when using syncToken.
                    eventsRequest.setSyncToken(storedSyncToken)
                } else {
                    // Full sync: fetch time range.
                    // IMPORTANT: Do NOT set orderBy — Google Calendar API does NOT return
                    // nextSyncToken when orderBy is specified. Events are sorted by the app.
                    val now = System.currentTimeMillis()
                    val sixMonthsAgo = now - (180L * 24 * 60 * 60 * 1000)
                    val oneYearAhead = now + (365L * 24 * 60 * 60 * 1000)
                    eventsRequest.setTimeMin(DateTime(sixMonthsAgo))
                    eventsRequest.setTimeMax(DateTime(oneYearAhead))
                    // No setOrderBy() here — it prevents nextSyncToken from being returned!
                }

                val eventsResult = try {
                    Log.v(TAG, "  → Executing API request for '${cal.summary}' page=${pageToken?.take(10) ?: "FIRST"}")
                    eventsRequest.execute()
                } catch (e: GoogleJsonResponseException) {
                    if (e.statusCode == 410) {
                        Log.w(TAG, "⚠️ TOKEN_EXPIRED (410) cal='${cal.summary}' — clearing token, queuing full sync")
                        saveSyncToken(context, cal.id, null)
                        GoogleSyncWorker.enqueueFullSync(context)
                        return Pair(emptyList(), false)
                    }
                    Log.e(TAG, "❌ API_ERROR cal='${cal.summary}' statusCode=${e.statusCode}", e)
                    return Pair(emptyList(), wasIncremental)
                }

                val nextPageTok = eventsResult.nextPageToken
                val nextSyncTok = eventsResult.nextSyncToken
                val itemCount = eventsResult.items?.size ?: 0
                Log.i(TAG, "  PAGE_RESULT cal='${cal.summary}' " +
                    "items=$itemCount " +
                    "nextPageToken=${if (nextPageTok != null) nextPageTok.take(10)+"..." else "null"} " +
                    "nextSyncToken=${if (nextSyncTok != null) nextSyncTok.take(20)+"..." else "null"}")
                val pageEvents = mutableListOf<Task>()
                val eventItems = eventsResult.items ?: emptyList()

                for (event in eventItems) {
                    if (event.status == "cancelled") {
                        val cancelledId = "gcal_${event.id}"
                        val task = Task(
                            id = cancelledId,
                            title = "",
                            createdDate = "",
                            deletedFromGoogle = true,
                            source = "google_calendar"
                        )
                        events.add(task)
                        pageEvents.add(task)
                        Log.d(TAG, "  ✕ CANCELLED id=${event.id} cal='${cal.summary}'")
                        continue
                    }

                    val recurrence = if (!event.recurrence.isNullOrEmpty()) {
                        parseRRule(event.recurrence)
                    } else if (event.recurringEventId != null) {
                        cacheMutex.withLock {
                            masterRruleCache.getOrPut(event.recurringEventId) {
                                try {
                                    val master = calendarService.events().get(cal.id, event.recurringEventId).execute()
                                    parseRRule(master.recurrence)
                                } catch (e: Throwable) {
                                    Log.w(TAG, "Could not fetch master event ${event.recurringEventId}", e)
                                    ParsedRecurrence(null, null, null, null)
                                }
                            }
                        }
                    } else {
                        ParsedRecurrence(null, null, null, null)
                    }

                    val task = eventToTask(event, cal.id, sdf, utcSdf, timeSdf, recurrence, calendarName = cal.summary)
                    if (task != null) {
                        events.add(task)
                        pageEvents.add(task)
                    }
                }

                if (pageEvents.isNotEmpty()) {
                    try {
                        onProgressUpdate(pageEvents)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in progressive sync update", e)
                    }
                }

                // Save nextSyncToken from this page (last page's token is the real sync token)
                eventsResult.nextSyncToken?.let { lastNextSyncToken = it }
                pageToken = eventsResult.nextPageToken
                Log.v(TAG, "  PAGE_DONE cal='${cal.summary}' " +
                    "eventsThisPage=${pageEvents.size} totalSoFar=${events.size} " +
                    "hasMorePages=${pageToken != null}")
            } while (pageToken != null)

            // Persist the final nextSyncToken for future incremental syncs
            if (lastNextSyncToken != null) {
                saveSyncToken(context, cal.id, lastNextSyncToken)
                Log.i(TAG, "✅ TOKEN_SAVED cal='${cal.summary}' token=${lastNextSyncToken.take(20)}...")
            } else if (!wasIncremental) {
                Log.w(TAG, "⚠️ NO_SYNC_TOKEN cal='${cal.summary}' totalEvents=${events.size} " +
                    "[HINT: If this persists, Google may not support tokens for this calendar type]")
            } else {
                Log.d(TAG, "  Incremental sync complete for '${cal.summary}' — token carried over")
            }

            Log.i(TAG, "◄◄ FETCH_DONE cal='${cal.summary}' " +
                "totalEvents=${events.size} mode=${if (wasIncremental) "INCREMENTAL" else "FULL"}")        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching events from calendar ${cal.summary}", e)
        }

        return Pair(events, wasIncremental)
    }

    /**
     * Convert a Google Calendar Event to our Task model.
     */
    private fun eventToTask(
        event: Event,
        calendarId: String,
        sdf: SimpleDateFormat,
        utcSdf: SimpleDateFormat,
        timeSdf: SimpleDateFormat,
        recurrence: ParsedRecurrence = ParsedRecurrence(null, null, null, null),
        calendarName: String? = null
    ): Task? {
        val title = event.summary ?: return null

        // Get the date from start
        val start = event.start ?: return null
        val startDate: Date
        val deadlineTime: String?
        var isAllDay = false

        if (start.dateTime != null) {
            // Timed event
            startDate = Date(start.dateTime.value)
            deadlineTime = timeSdf.format(startDate)
        } else if (start.date != null) {
            // All-day event (like festivals, holidays)
            startDate = Date(start.date.value)
            deadlineTime = null
            isAllDay = true
        } else {
            return null
        }

        // For all-day events, format date in UTC to avoid timezone shift
        // (Google returns all-day dates as UTC midnight)
        val dateStr = if (isAllDay) utcSdf.format(startDate) else sdf.format(startDate)

        // Create a stable ID based on the Google event ID so we can deduplicate
        val stableId = "gcal_${event.id}"

        // For expanded recurring instances (has recurringEventId), do NOT set
        // recurrence template fields — Google already expanded them. Only store
        // display-only recurrence info. For master events with RRULE, set recurrence fields.
        val isExpandedInstance = event.recurringEventId != null
        val humanRecurrence = buildHumanReadableRecurrence(recurrence)

        // ── Map eventType ──
        // Google's eventType: "default","birthday","focusTime","fromGmail","outOfOffice","workingLocation"
        // We add "holiday" for events from subscribed holiday calendars
        val resolvedEventType = when {
            // Holiday calendars have IDs like "en.indian#holiday@group.v.calendar.google.com"
            calendarId.contains("#holiday@group.v.calendar.google.com") -> "holiday"
            event.eventType == "birthday" -> "birthday"
            event.eventType == "focusTime" -> "focusTime"
            event.eventType == "outOfOffice" -> "outOfOffice"
            event.eventType == "fromGmail" -> "fromGmail"
            else -> "default"
        }

        // ── Map end time ──
        val endTimeStr: String? = if (event.end?.dateTime != null) {
            timeSdf.format(Date(event.end.dateTime.value))
        } else null

        // ── Extract meeting link ──
        val meetLink: String? = event.conferenceData?.entryPoints
            ?.firstOrNull { it.entryPointType == "video" }?.uri
            ?: event.hangoutLink

        // ── Extract extended Google Calendar metadata ──
        val gson = Gson()

        // Attendees with response status
        val attendeesJson: String? = event.attendees?.takeIf { it.isNotEmpty() }?.map { attendee ->
            mapOf(
                "email" to (attendee.email ?: ""),
                "displayName" to (attendee.displayName ?: ""),
                "responseStatus" to (attendee.responseStatus ?: "needsAction"),
                "organizer" to (attendee.organizer ?: false),
                "self" to (attendee.self ?: false)
            )
        }?.let { gson.toJson(it) }

        // Reminders (custom overrides only, not default)
        val remindersJson: String? = event.reminders?.overrides?.takeIf { it.isNotEmpty() }?.map { reminder ->
            mapOf(
                "method" to (reminder.method ?: "popup"),
                "minutes" to (reminder.minutes ?: 10)
            )
        }?.let { gson.toJson(it) }

        // HTML link to open event in Google Calendar
        val htmlLink: String? = event.htmlLink

        // Organizer info
        val organizerJson: String? = event.organizer?.let { org ->
            gson.toJson(mapOf(
                "email" to (org.email ?: ""),
                "displayName" to (org.displayName ?: ""),
                "self" to (org.self ?: false)
            ))
        }

        // Visibility (default, public, private, confidential)
        val visibility: String? = event.visibility

        // Attachments (files attached to the event)
        val attachmentsJson: String? = event.attachments?.takeIf { it.isNotEmpty() }?.map { attachment ->
            mapOf(
                "title" to (attachment.title ?: ""),
                "fileUrl" to (attachment.fileUrl ?: ""),
                "mimeType" to (attachment.mimeType ?: ""),
                "iconLink" to (attachment.iconLink ?: "")
            )
        }?.let { gson.toJson(it) }

        // Conference phone dial-in info
        val conferencePhone: String? = event.conferenceData?.entryPoints
            ?.firstOrNull { it.entryPointType == "phone" }
            ?.let { entry ->
                buildString {
                    append(entry.label ?: entry.uri ?: "")
                    entry.pin?.let { pin -> append(" PIN: $pin") }
                    entry.accessCode?.let { code -> append(" Code: $code") }
                }
            }?.takeIf { it.isNotBlank() }

        // ── Map completion via extendedProperties ──
        // Google Calendar events don't have a native completion status, so we store it in extended properties
        val isPropCompleted = event.extendedProperties?.private?.get("preamble_completed") == "true"
        val completedTimestampStr = event.extendedProperties?.private?.get("preamble_completed_timestamp")
        val parsedCompletedTime = completedTimestampStr?.toLongOrNull()
        val preambleTags = event.extendedProperties?.private?.get("preamble_tags")

        return Task(
            id = stableId,
            title = title,
            isCompleted = isPropCompleted,
            createdDate = dateStr,
            createdTimestamp = startDate.time,
            completedTimestamp = parsedCompletedTime,
            deadlineTime = deadlineTime,
            updatedTimestamp = event.updated?.value ?: System.currentTimeMillis(),
            source = "google_calendar",
            description = event.description,
            tags = preambleTags, // Tags applied natively from Google Calendar
            googleCalendarId = calendarId,
            // Only set recurrence fields on master events, NOT expanded instances
            recurrenceType = if (!isExpandedInstance) recurrence.type else null,
            recurrenceInterval = if (!isExpandedInstance) recurrence.interval else null,
            recurrenceDays = if (!isExpandedInstance) recurrence.days else null,
            recurrenceEndDate = if (!isExpandedInstance) recurrence.endDate else null,
            recurrenceParentId = if (isExpandedInstance) "gcal_${event.recurringEventId}" else null,
            googleRecurrenceInfo = humanRecurrence,
            // New Calendar metadata
            eventType = resolvedEventType,
            calendarName = calendarName,
            location = event.location,
            endTime = endTimeStr,
            meetingLink = meetLink,
            // Extended Calendar metadata
            attendeesJson = attendeesJson,
            remindersJson = remindersJson,
            htmlLink = htmlLink,
            organizerJson = organizerJson,
            visibility = visibility,
            attachmentsJson = attachmentsJson,
            conferencePhone = conferencePhone
        )
    }

    /**
     * Build a human-readable recurrence description from parsed recurrence.
     * Examples: "Every day", "Every 2 weeks on Mon, Wed, Fri", "Every month", "Every year"
     */
    fun buildHumanReadableRecurrence(parsed: ParsedRecurrence): String? {
        if (parsed.type == null) return null

        val sb = StringBuilder("Every ")
        val interval = parsed.interval ?: 1

        when (parsed.type) {
            "daily" -> {
                if (interval == 1) sb.append("day") else sb.append("$interval days")
            }
            "weekly" -> {
                if (interval == 1) sb.append("week") else sb.append("$interval weeks")
                if (!parsed.days.isNullOrEmpty()) {
                    val dayNames = parsed.days.split(",").mapNotNull { dayNum ->
                        when (dayNum.trim().toIntOrNull()) {
                            1 -> "Sun"
                            2 -> "Mon"
                            3 -> "Tue"
                            4 -> "Wed"
                            5 -> "Thu"
                            6 -> "Fri"
                            7 -> "Sat"
                            else -> null
                        }
                    }
                    if (dayNames.isNotEmpty()) {
                        sb.append(" on ").append(dayNames.joinToString(", "))
                    }
                }
            }
            "monthly" -> {
                if (interval == 1) sb.append("month") else sb.append("$interval months")
            }
            "yearly" -> {
                if (interval == 1) sb.append("year") else sb.append("$interval years")
            }
        }

        if (!parsed.endDate.isNullOrEmpty()) {
            sb.append(" until ").append(parsed.endDate)
        }

        return sb.toString()
    }

    // ── RRULE Helpers ──

    /**
     * Parse a Google Calendar RRULE string into app recurrence fields.
     * Supports: FREQ=DAILY/WEEKLY/MONTHLY/YEARLY, INTERVAL, BYDAY, UNTIL, COUNT
     *
     * Google Calendar RRULE examples:
     * - RRULE:FREQ=DAILY
     * - RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
     * - RRULE:FREQ=MONTHLY;INTERVAL=2
     * - RRULE:FREQ=YEARLY
     * - RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;UNTIL=20261231T000000Z
     */
    data class ParsedRecurrence(
        val type: String?,           // "daily","weekly","monthly","yearly"
        val interval: Int?,
        val days: String?,           // comma-separated Calendar.DAY_OF_WEEK values
        val endDate: String?         // "yyyy-MM-dd"
    )

    private val dayMap = mapOf(
        "SU" to 1, "MO" to 2, "TU" to 3, "WE" to 4,
        "TH" to 5, "FR" to 6, "SA" to 7
    )

    private val reverseDayMap = dayMap.entries.associate { (k, v) -> v to k }

    private fun parseRRule(rruleList: List<String>?): ParsedRecurrence {
        if (rruleList.isNullOrEmpty()) return ParsedRecurrence(null, null, null, null)

        // Find the actual RRULE line (there may be EXDATE etc)
        val rruleLine = rruleList.find { it.startsWith("RRULE:") }
            ?: return ParsedRecurrence(null, null, null, null)

        val parts = rruleLine.removePrefix("RRULE:").split(";").associate { part ->
            val (key, value) = part.split("=", limit = 2)
            key.uppercase() to value
        }

        val freq = parts["FREQ"]
        val type = when (freq) {
            "DAILY" -> "daily"
            "WEEKLY" -> "weekly"
            "MONTHLY" -> "monthly"
            "YEARLY" -> "yearly"
            else -> null
        } ?: return ParsedRecurrence(null, null, null, null)

        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1

        // Parse BYDAY (e.g., "MO,WE,FR" -> Calendar.DAY_OF_WEEK values "2,4,6")
        val days = parts["BYDAY"]?.let { byDay ->
            byDay.split(",")
                .mapNotNull { dayStr -> dayMap[dayStr.trim().takeLast(2).uppercase()] }
                .sorted()
                .joinToString(",")
        }

        // Parse UNTIL (e.g., "20261231T000000Z" -> "2026-12-31")
        val endDate = parts["UNTIL"]?.let { until ->
            try {
                val dateStr = until.take(8) // "20261231"
                "${dateStr.substring(0, 4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
            } catch (e: Exception) {
                null
            }
        }

        return ParsedRecurrence(type, interval, days, endDate)
    }

    /**
     * Build RRULE string from app recurrence fields.
     */
    fun buildRRule(
        recurrenceType: String?,
        recurrenceInterval: Int?,
        recurrenceDays: String?,
        recurrenceEndDate: String?
    ): String? {
        if (recurrenceType == null) return null

        val freq = when (recurrenceType) {
            "daily", "custom" -> "DAILY"
            "weekly" -> "WEEKLY"
            "monthly" -> "MONTHLY"
            "yearly" -> "YEARLY"
            else -> return null
        }

        val parts = mutableListOf("FREQ=$freq")

        val interval = recurrenceInterval ?: 1
        if (interval > 1) {
            parts.add("INTERVAL=$interval")
        }

        // BYDAY for weekly recurrence
        if (recurrenceType == "weekly" && !recurrenceDays.isNullOrEmpty()) {
            val byDay = recurrenceDays.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .mapNotNull { reverseDayMap[it] }
                .joinToString(",")
            if (byDay.isNotEmpty()) {
                parts.add("BYDAY=$byDay")
            }
        }

        // UNTIL
        if (!recurrenceEndDate.isNullOrEmpty()) {
            val until = recurrenceEndDate.replace("-", "") + "T000000Z"
            parts.add("UNTIL=$until")
        }

        return "RRULE:" + parts.joinToString(";")
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
        description: String? = null,
        recurrenceType: String? = null,
        recurrenceInterval: Int? = null,
        recurrenceDays: String? = null,
        recurrenceEndDate: String? = null,
        tags: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext null
        try {
            retryOnFailure {
                val service = buildCalendarService(context) ?: error("No Calendar service")
                val event = Event().setSummary(title)
                if (description != null) event.description = description
                if (!tags.isNullOrBlank()) {
                    event.extendedProperties = Event.ExtendedProperties().setPrivate(mapOf("preamble_tags" to tags))
                }

                if (deadlineTime != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    val tzOffsetSdf = SimpleDateFormat("XXX", Locale.US)
                    val cal = java.util.Calendar.getInstance()
                    cal.time = sdf.parse("${date}T${deadlineTime}:00")!!
                    val tzOffset = tzOffsetSdf.format(cal.time)
                    val startDateTime = DateTime("${date}T${deadlineTime}:00${tzOffset}")
                    event.start = EventDateTime().setDateTime(startDateTime)
                        .setTimeZone(java.util.TimeZone.getDefault().id)
                    cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
                    val endDateTime = DateTime(sdf.format(cal.time) + tzOffset)
                    event.end = EventDateTime().setDateTime(endDateTime)
                        .setTimeZone(java.util.TimeZone.getDefault().id)
                } else {
                    // All-day event: use date string directly to avoid timezone offset
                    val dateOnly = DateTime(date)
                    event.start = EventDateTime().setDate(dateOnly)
                    event.end = EventDateTime().setDate(dateOnly)
                }

                // Set recurrence RRULE if provided
                val rrule = buildRRule(recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate)
                if (rrule != null) {
                    event.recurrence = listOf(rrule)
                }

                val created = service.events().insert(calendarId, event).execute()
                Log.d(TAG, "Created calendar event: ${created.id}")
                created.id
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create calendar event", e)
            throw e
        }
    }

    suspend fun updateCalendarEvent(
        context: Context,
        eventId: String,
        calendarId: String,
        title: String,
        date: String,
        deadlineTime: String?,
        description: String? = null,
        recurrenceType: String? = null,
        recurrenceInterval: Int? = null,
        recurrenceDays: String? = null,
        recurrenceEndDate: String? = null,
        tags: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext false
        try {
            retryOnFailure {
                val service = buildCalendarService(context) ?: error("No Calendar service")
                val event = service.events().get(calendarId, eventId).execute()
                event.summary = title
                if (description != null) event.description = description
                
                if (tags != null) {
                    val currentPrivate = event.extendedProperties?.private ?: emptyMap()
                    val newPrivate = currentPrivate.toMutableMap()
                    if (tags.isBlank()) {
                        newPrivate.remove("preamble_tags")
                    } else {
                        newPrivate["preamble_tags"] = tags
                    }
                    if (event.extendedProperties == null) {
                        event.extendedProperties = Event.ExtendedProperties()
                    }
                    event.extendedProperties.setPrivate(newPrivate)
                }

                if (deadlineTime != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    val tzOffsetSdf = SimpleDateFormat("XXX", Locale.US)
                    val cal = java.util.Calendar.getInstance()
                    cal.time = sdf.parse("${date}T${deadlineTime}:00")!!
                    val tzOffset = tzOffsetSdf.format(cal.time)
                    val startDateTime = DateTime("${date}T${deadlineTime}:00${tzOffset}")
                    event.start = EventDateTime().setDateTime(startDateTime)
                        .setTimeZone(java.util.TimeZone.getDefault().id)
                    cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
                    event.end = EventDateTime().setDateTime(DateTime(sdf.format(cal.time) + tzOffset))
                        .setTimeZone(java.util.TimeZone.getDefault().id)
                } else {
                    // All-day event: use date string directly to avoid timezone offset
                    val dateOnly = DateTime(date)
                    event.start = EventDateTime().setDate(dateOnly)
                    event.end = EventDateTime().setDate(dateOnly)
                }

                // Update recurrence RRULE
                val rrule = buildRRule(recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate)
                if (rrule != null) {
                    event.recurrence = listOf(rrule)
                } else {
                    // Remove recurrence if cleared
                    event.recurrence = null
                }

                service.events().update(calendarId, eventId, event).execute()
                Log.d(TAG, "Updated calendar event $eventId")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update calendar event $eventId", e)
            throw e
        }
    }

    suspend fun updateCalendarEventCompletion(
        context: Context,
        eventId: String,
        calendarId: String,
        isCompleted: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_isLinked.value) return@withContext false
        try {
            retryOnFailure {
                val service = buildCalendarService(context) ?: error("No Calendar service")
                val event = service.events().get(calendarId, eventId).execute()
                
                var extProps = event.extendedProperties
                if (extProps == null) {
                    extProps = com.google.api.services.calendar.model.Event.ExtendedProperties()
                    event.extendedProperties = extProps
                }
                var privateProps = extProps.private
                if (privateProps == null) {
                    privateProps = mutableMapOf()
                    extProps.private = privateProps
                }
                
                if (isCompleted) {
                    privateProps["preamble_completed"] = "true"
                    privateProps["preamble_completed_timestamp"] = System.currentTimeMillis().toString()
                } else {
                    privateProps["preamble_completed"] = "false"
                    privateProps.remove("preamble_completed_timestamp")
                }
                
                service.events().update(calendarId, eventId, event).execute()
                Log.d(TAG, "Updated completion status for calendar event $eventId")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update completion for calendar event $eventId", e)
            throw e
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
                try {
                    service.events().delete(calendarId, eventId).execute()
                    Log.d(TAG, "Deleted calendar event $eventId from calendar $calendarId")
                } catch (e: GoogleJsonResponseException) {
                    if (e.statusCode == 410 || e.statusCode == 404) {
                        // Already deleted or not found — treat as success
                        Log.d(TAG, "Calendar event $eventId already deleted (${e.statusCode})")
                    } else {
                        throw e
                    }
                }
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete calendar event $eventId", e)
            throw e
        }
    }
}
