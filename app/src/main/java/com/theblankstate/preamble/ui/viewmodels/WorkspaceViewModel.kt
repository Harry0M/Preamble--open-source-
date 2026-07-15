package com.theblankstate.preamble.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theblankstate.preamble.data.CollabAssigneeStatus
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.Subtask
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.collab.DeepLinkInviteState
import com.theblankstate.preamble.collab.FriendRemovalImpactLogic
import com.theblankstate.preamble.collab.InviteLink
import com.theblankstate.preamble.collab.InviteValidation
import com.theblankstate.preamble.collab.InviteValidator
import com.theblankstate.preamble.collab.Leaderboard
import com.theblankstate.preamble.collab.NudgeRateLimit
import com.theblankstate.preamble.collab.OutgoingInvite
import com.theblankstate.preamble.collab.OutgoingInviteReconciler
import com.theblankstate.preamble.collab.PreambleId
import com.theblankstate.preamble.collab.Reactions
import com.theblankstate.preamble.collab.RequestsListOrganizer
import com.theblankstate.preamble.collab.TaskReaction
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.repository.WorkspaceInvite
import com.theblankstate.preamble.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class FriendRemovalImpact(
    val administeredTasks: List<Task> = emptyList(),
    val memberTasks: List<Task> = emptyList()
) {
    val totalTasks: Int get() = administeredTasks.size + memberTasks.size
    val requiresResolution: Boolean get() = totalTasks > 0
}

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = WorkspaceRepository()
    private val gson = Gson()
    private val database = PreambleDatabase.getInstance(application)
    private val taskDao = database.taskDao()

    private val sharedPrefs = application.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)

    private val _myPreambleIdState = MutableStateFlow(UserProfileStore.ensurePreambleId(application))
    private val _myNameState = MutableStateFlow(UserProfileStore.load(application).name ?: "Preamble user")

    var myPreambleId by androidx.compose.runtime.mutableStateOf(UserProfileStore.ensurePreambleId(application))
        private set

    var myName by androidx.compose.runtime.mutableStateOf(UserProfileStore.load(application).name ?: "Preamble user")
        private set

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "profile_preamble_id" || key == "profile_name") {
            viewModelScope.launch(Dispatchers.Main) {
                val newId = UserProfileStore.ensurePreambleId(application)
                val newName = UserProfileStore.load(application).name ?: "Preamble user"
                myPreambleId = newId
                myName = newName
                _myPreambleIdState.value = newId
                _myNameState.value = newName
            }
        }
    }
    private val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    /**
     * The raw leaderboard score documents streamed from the repository. Held as a StateFlow
     * (populated by [observeLeaderboardScores]) rather than collected inline by the
     * [leaderboard] combine, so its first emission and listener failures can drive
     * [socialHubLoadState] and so [retryLoad] can re-subscribe it after an Error_State
     * (Req 1.6, 1.7).
     */
    private val _leaderboardScores =
        MutableStateFlow<Map<String, Leaderboard.ScoreDoc>>(emptyMap())

    private val _invites = MutableStateFlow<List<WorkspaceInvite>>(emptyList())
    val invites: StateFlow<List<WorkspaceInvite>> = _invites.asStateFlow()

    /**
     * Locally-applied Outgoing_Invite mirrors that the snapshot listener has not yet
     * reflected. A successful [sendInvite] adds an optimistic entry here so the just-sent
     * invite is visible the instant the user lands on the Requests_List (Req 5.2); a failed
     * send reverts it so the outgoing set is left unchanged (Req 5.4). Entries are keyed by
     * the recipient's normalized Preamble_ID and deduped against the persisted mirror, so a
     * single invite is never shown twice once the listener catches up.
     */
    private val _optimisticOutgoing = MutableStateFlow<List<OutgoingInvite>>(emptyList())

    /**
     * The displayed Outgoing_Invites (Req 4.1, 4.4, 5.2). Derived from the repository's
     * mirror flow merged with any not-yet-listener-reflected optimistic entry, then
     * reconciled against the friend set via [OutgoingInviteReconciler.visibleOutgoing] so an
     * accepted invite (target now a friend) drops out automatically. A listener failure
     * retains the last loaded value and surfaces a non-fatal message (`catch` without
     * re-emit), never crashing the surface.
     */
    val outgoingInvites: StateFlow<List<OutgoingInvite>> =
        combine(repo.getOutgoingInvitesFlow(), _optimisticOutgoing, _friends) { mirrored, optimistic, friends ->
            val friendUids = friends.mapTo(mutableSetOf()) { it.uid }
            // Merge persisted mirror with optimistic entries, deduping by recipient
            // Preamble_ID so a just-sent invite appears exactly once. The persisted mirror
            // wins so the real target uid drives reconciliation once the listener catches up.
            val mergedByPreambleId = LinkedHashMap<String, OutgoingInvite>()
            mirrored.forEach { mergedByPreambleId[it.targetPreambleId] = it }
            optimistic.forEach { mergedByPreambleId.putIfAbsent(it.targetPreambleId, it) }
            OutgoingInviteReconciler.visibleOutgoing(mergedByPreambleId.values.toList(), friendUids)
        }
            .catch { error -> reportListenerFailure("outgoing invites", error) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The Requests_List view model grouping the signed-in user's Outgoing_Invites and
     * Incoming_Invites into separate sections (Req 5.3, 5.5) via [RequestsListOrganizer].
     */
    val requestsSections: StateFlow<RequestsListOrganizer.Sections> =
        combine(outgoingInvites, _invites) { outgoing, incoming ->
            RequestsListOrganizer.organize(outgoing, incoming)
        }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                RequestsListOrganizer.Sections(emptyList(), emptyList())
            )

    /**
     * The number of pending requests (Outgoing_Invites + Incoming_Invites) driving the
     * on-demand Requests control badge (Req 5.6). Derived from [requestsSections] as
     * `outgoing.size + incoming.size`, so it always equals the total number of grouped
     * invites the Requests_List shows.
     */
    val pendingRequestsCount: StateFlow<Int> =
        requestsSections
            .map { it.outgoing.size + it.incoming.size }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * The Social_Hub load state driving the Loading_State (Req 1.6) and the retry-able
     * Error_State (Req 1.7). It is `Loading` until both the friends and leaderboard listeners
     * have produced their first emission, `Loaded` thereafter, and `Error(message)` when either
     * list listener fails. The backing data StateFlows ([_friends], [_leaderboardScores]) retain
     * their last loaded value across a transient failure, so an Error_State is shown over the
     * previously loaded data rather than a blank surface.
     */
    private val _socialHubLoadState =
        MutableStateFlow<SocialHubLoadState>(SocialHubLoadState.Loading)
    val socialHubLoadState: StateFlow<SocialHubLoadState> = _socialHubLoadState.asStateFlow()

    /** Which list listeners have produced a first emission / are currently failed (Req 1.6, 1.7). */
    private val emittedSources = mutableSetOf<LoadSource>()
    private val failedSources = mutableSetOf<LoadSource>()

    /** The list listener jobs, retained so [retryLoad] can cancel and re-subscribe them. */
    private var friendsJob: Job? = null
    private var leaderboardScoresJob: Job? = null

    /**
     * One-shot event emitted when a Friend_Request is sent successfully, instructing the UI
     * to navigate the user to the Requests_List (Req 5.1). Buffered so an emission is not
     * dropped when there is momentarily no collector; consumed once per send.
     */
    private val _navigateToRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToRequests: SharedFlow<Unit> = _navigateToRequests.asSharedFlow()

    private val _collaborativeTasks = MutableStateFlow<List<Task>>(emptyList())
    val collaborativeTasks: StateFlow<List<Task>> = _collaborativeTasks.asStateFlow()

    private val _incomingAssignments = MutableStateFlow<List<Task>>(emptyList())
    val incomingAssignments: StateFlow<List<Task>> = _incomingAssignments.asStateFlow()

    private val _outgoingAssignments = MutableStateFlow<List<Task>>(emptyList())
    val outgoingAssignments: StateFlow<List<Task>> = _outgoingAssignments.asStateFlow()

    private val _uiState = MutableStateFlow<WorkspaceUiState>(WorkspaceUiState.Idle)
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    /**
     * Holds a Preamble_ID parsed from an invite link so the add-friend field can be
     * pre-filled (Requirement 2.2). The UI observes this, fills the entry field, and
     * calls [consumePrefill] once it has been applied. A `null` value means there is
     * nothing to pre-fill.
     */
    private val _prefillPreambleId = MutableStateFlow<String?>(null)
    val prefillPreambleId: StateFlow<String?> = _prefillPreambleId.asStateFlow()

    /**
     * The deep-linked Preamble_ID to present in the Invite_Entry_Experience, or `null`
     * when there is nothing to present (Req 7.2). It is derived from the same pending
     * pre-fill state ([_prefillPreambleId]) — there is one mechanism, not two — routed
     * through [DeepLinkInviteState.toPresent]. Because [onInviteConsumed] clears the
     * pending state via [DeepLinkInviteState.consume], re-rendering the Social_Hub after
     * a consume yields `null`, so the same invite is never re-presented (Req 7.3).
     */
    val deepLinkInviteToPresent: StateFlow<String?> =
        _prefillPreambleId
            .map { DeepLinkInviteState.toPresent(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Last LOCAL nudge send time per `(taskId, targetUid)` pair, used to gate the nudge
     * control via [NudgeRateLimit] (Req 12). Held in memory only — server-side enforcement in
     * the `sendNudge` callable is authoritative; this drives the instant optimistic UI.
     */
    private val lastNudgeSentAt = mutableMapOf<Pair<String, String>, Long>()

    /**
     * The set of `(taskId, targetUid)` pairs currently shown as "nudged" so the UI can reflect
     * the optimistic nudged state (Req 10.4). Reverted when the backend write fails/times out.
     */
    private val _nudgedTargets = MutableStateFlow<Set<Pair<String, String>>>(emptySet())
    val nudgedTargets: StateFlow<Set<Pair<String, String>>> = _nudgedTargets.asStateFlow()

    /**
     * The Friends_Leaderboard for the signed-in user plus friends, ranked by current
     * Weekly_Window points descending (Req 9.1, 9.2, 9.3). Computed from the repository's
     * leaderboard score documents combined with the friend list (names + uids) via the pure
     * [Leaderboard.ranking]; `now = System.currentTimeMillis()` so a window crossing changes
     * the ranking without any write (Req 9.5). Self is always included; an empty friend list
     * yields a single self row, which the UI renders with the no-friends empty-state (Req 9.6).
     * A failure retains the last computed value (`catch` without re-emit).
     */
    val leaderboard: StateFlow<List<Leaderboard.Entry>> =
        combine(_leaderboardScores, _friends, _myPreambleIdState, _myNameState) { scores, friends, currentPId, currentName ->
            val selfUid = currentUid ?: return@combine emptyList()
            val friendUids = friends
                .map(Friend::uid)
                .filter { it.isNotBlank() && it != selfUid }
                .toSet()
            val names = HashMap<String, String>().apply {
                put(selfUid, currentName)
                friends.forEach { if (it.uid.isNotBlank()) put(it.uid, it.name) }
            }
            // Supply the per-uid Preamble_ID map (self + the friend records already held) so the
            // emitted leaderboard rows carry `preambleId` and are searchable consistently with the
            // Friends_List (Req 9.3). The map does not affect ordering or points.
            val preambleIds = HashMap<String, String>().apply {
                put(selfUid, currentPId)
                friends.forEach { if (it.uid.isNotBlank()) put(it.uid, it.preambleId) }
            }
            Leaderboard.ranking(
                selfUid,
                friendUids,
                scores,
                names,
                System.currentTimeMillis(),
                preambleIds
            )
        }
            .catch { error -> Log.e(TAG, "Leaderboard computation failed", error) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        observeFriends()
        observeLeaderboardScores()
        observeInvites()
        observeCollaborativeTasks()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun observeFriends() {
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
            repo.getFriendsFlow()
                .catch {
                    reportListenerFailure("friends", it)
                    markLoadError(LoadSource.Friends)
                }
                .collect {
                    _friends.value = it
                    markLoaded(LoadSource.Friends)
                }
        }
    }

    /**
     * Subscribes the leaderboard score documents into [_leaderboardScores], tracking the first
     * emission and any listener failure so [socialHubLoadState] reflects the Social_Hub's load
     * progress (Req 1.6, 1.7). A failure retains the last loaded scores (no clear) and surfaces
     * an Error_State; [retryLoad] re-subscribes this flow.
     */
    private fun observeLeaderboardScores() {
        leaderboardScoresJob?.cancel()
        leaderboardScoresJob = viewModelScope.launch {
            repo.getLeaderboardScoresFlow()
                .catch {
                    Log.e(TAG, "leaderboard listener stopped", it)
                    markLoadError(LoadSource.Leaderboard)
                }
                .collect {
                    _leaderboardScores.value = it
                    markLoaded(LoadSource.Leaderboard)
                }
        }
    }

    /**
     * Records a successful first/subsequent emission for [source], clears any prior failure for
     * it, and recomputes [socialHubLoadState] (Req 1.6).
     */
    private fun markLoaded(source: LoadSource) {
        emittedSources.add(source)
        failedSources.remove(source)
        recomputeLoadState()
    }

    /** Records a list-listener failure for [source] so the Social_Hub shows the Error_State (Req 1.7). */
    private fun markLoadError(source: LoadSource) {
        failedSources.add(source)
        recomputeLoadState()
    }

    private fun recomputeLoadState() {
        _socialHubLoadState.value = when {
            failedSources.isNotEmpty() -> SocialHubLoadState.Error(
                "Could not load ${failedSources.joinToString(" and ") { it.label }}. Tap retry to try again."
            )
            emittedSources.containsAll(listOf(LoadSource.Friends, LoadSource.Leaderboard)) ->
                SocialHubLoadState.Loaded
            else -> SocialHubLoadState.Loading
        }
    }

    /**
     * Re-subscribes the friends/leaderboard list listeners after an Error_State so the user can
     * recover from a transient load failure (Req 1.7). Only the failed flows are re-subscribed;
     * their emitted flags are reset so the Social_Hub returns to the Loading_State until the fresh
     * emission arrives, at which point it returns to Loaded. Prior loaded data remains displayed
     * meanwhile because the backing StateFlows are never cleared.
     */
    fun retryLoad() {
        val sourcesToRetry = failedSources.toSet().ifEmpty {
            setOf(LoadSource.Friends, LoadSource.Leaderboard)
        }
        failedSources.clear()
        sourcesToRetry.forEach { emittedSources.remove(it) }
        recomputeLoadState()
        if (LoadSource.Friends in sourcesToRetry) observeFriends()
        if (LoadSource.Leaderboard in sourcesToRetry) observeLeaderboardScores()
    }

    private fun observeInvites() {
        viewModelScope.launch {
            repo.getPendingInvitesFlow()
                .catch { reportListenerFailure("invites", it) }
                .collect { _invites.value = it }
        }
    }

    private fun observeCollaborativeTasks() {
        viewModelScope.launch {
            repo.getCollaborativeTasksFlow()
                .catch { reportListenerFailure("collaborative tasks", it) }
                .collect { tasks ->
                    val uid = currentUid
                    _collaborativeTasks.value = tasks
                    _incomingAssignments.value = tasks.filter { it.collabAdminUid != uid }
                    _outgoingAssignments.value = tasks.filter { it.collabAdminUid == uid }
                    synchronizeCollaborativeTasksToRoom(tasks, uid)
                }
        }
    }

    private suspend fun synchronizeCollaborativeTasksToRoom(tasks: List<Task>, uid: String?) =
        withContext(Dispatchers.IO) {
            if (uid == null) return@withContext
            val locallyVisibleTasks = tasks.filter { task ->
                task.collabAdminUid == uid || task.assignmentStatus in setOf("accepted", "completed")
            }
            val visibleIds = locallyVisibleTasks.mapTo(mutableSetOf(), Task::id)

            // Mirror each visible task independently so that one member's mirror failure
            // does not abort the rest of the sync. A failure for a given task retains that
            // task's last successfully synced local copy (we never overwrite or delete it on
            // failure) and flags that a message must be surfaced (Requirement 7.6).
            var mirrorFailed = false
            locallyVisibleTasks.forEach { remoteTask ->
                try {
                    val localTask = taskDao.getTaskById(remoteTask.id)
                    val merged = if (localTask == null) {
                        remoteTask.copy(isSyncing = false, syncFailed = false)
                    } else {
                        mergeRemoteCollaboration(localTask, remoteTask)
                    }
                    taskDao.insertTask(merged)
                    synchronizeSubtaskRows(merged)
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to mirror collaborative task ${remoteTask.id} to Room", exception)
                    mirrorFailed = true
                }
            }

            // Prune local assigned tasks that are no longer visible. Guarded so a pruning
            // failure cannot crash the listener or block the message surfaced above.
            try {
                taskDao.getLocalAssignedTasks()
                    .filter { it.id !in visibleIds }
                    .forEach { staleTask ->
                        taskDao.deleteAllSubtasks(staleTask.id)
                        taskDao.deleteTask(staleTask)
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to prune stale collaborative tasks", exception)
                mirrorFailed = true
            }

            if (mirrorFailed) {
                // The last synced copy is retained above; tell the user the shared task
                // could not be updated (Requirement 7.6).
                _uiState.value = WorkspaceUiState.Error("A shared task could not be updated. Showing the last synced version.")
            }
        }

    private fun mergeRemoteCollaboration(local: Task, remote: Task): Task = local.copy(
        title = remote.title,
        isCompleted = remote.isCompleted,
        completedTimestamp = remote.completedTimestamp,
        completedDate = remote.completedDate,
        createdDate = remote.createdDate,
        deadlineTime = remote.deadlineTime,
        updatedTimestamp = remote.updatedTimestamp,
        priority = remote.priority,
        description = remote.description,
        recurrenceType = remote.recurrenceType,
        recurrenceInterval = remote.recurrenceInterval,
        recurrenceDays = remote.recurrenceDays,
        recurrenceEndDate = remote.recurrenceEndDate,
        tags = remote.tags,
        subtasksJson = remote.subtasksJson,
        remindersJson = remote.remindersJson,
        isHabit = remote.isHabit,
        isEvent = remote.isEvent,
        eventIcon = remote.eventIcon,
        eventColor = remote.eventColor,
        assignedByUid = remote.assignedByUid,
        assignedByName = remote.assignedByName,
        assignedToUid = remote.assignedToUid,
        assignedToName = remote.assignedToName,
        assignmentStatus = remote.assignmentStatus,
        collabAssigneesJson = remote.collabAssigneesJson,
        collabAdminUid = remote.collabAdminUid,
        collabAdminName = remote.collabAdminName,
        reactionsJson = remote.reactionsJson,
        collabSendStatus = remote.collabSendStatus,
        isSyncing = false,
        syncFailed = false
    )

    private suspend fun synchronizeSubtaskRows(parent: Task) {
        if (parent.subtasksJson.isNullOrBlank()) return
        parent.subtasks.forEach { subtask ->
            val existing = taskDao.getTaskById(subtask.id)
            val row = existing?.copy(
                title = subtask.title,
                isCompleted = subtask.isCompleted,
                updatedTimestamp = parent.updatedTimestamp
            ) ?: Task(
                id = subtask.id,
                title = subtask.title,
                isCompleted = subtask.isCompleted,
                createdDate = parent.createdDate,
                createdTimestamp = parent.createdTimestamp,
                updatedTimestamp = parent.updatedTimestamp,
                parentTaskId = parent.id,
                source = parent.source
            )
            taskDao.insertTask(row)
        }
    }

    fun sendInvite(targetId: String) {
        if (_uiState.value is WorkspaceUiState.Loading) return

        // Run the same local validation used by the invite-link entry path
        // (Requirement 2.3): reject empty (1.2), self (1.5), an existing friend (1.6),
        // and a known pending request (1.7) before any write. Directory non-existence
        // (1.4) is decided authoritatively by the repository's directory lookup.
        when (val validation = validateSubmission(targetId)) {
            InviteValidation.Ok -> Unit
            else -> {
                _uiState.value = WorkspaceUiState.Error(validation.message())
                return
            }
        }

        // Optimistically add the Outgoing_Invite mirror so the just-sent invite is already
        // visible when the user lands on the Requests_List after a successful send (Req 5.2).
        // It is keyed by the recipient's normalized Preamble_ID (the real target uid is
        // resolved server-side and arrives via the listener) and deduped against the
        // persisted mirror so it is never shown twice. A failed send reverts it (Req 5.4).
        val normalizedId = PreambleId.normalize(targetId)
        _optimisticOutgoing.update { current ->
            if (current.any { it.targetPreambleId == normalizedId }) {
                current
            } else {
                current + OutgoingInvite(
                    targetUid = normalizedId,
                    targetPreambleId = normalizedId,
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        _uiState.value = WorkspaceUiState.Loading
        viewModelScope.launch {
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.sendInvite(targetId, myName, myPreambleId)
            }.fold(
                onSuccess = {
                    // Client-local confirmation that NAMES the recipient of the invite
                    // (notifications Req 2.1, 2.2). This is a local snackbar/toast surfaced
                    // through WorkspaceScreen's uiState handling, NOT a push (Decision D2).
                    // Copy is plain — no reward/credit/referral language (Req 2.4).
                    _uiState.value = WorkspaceUiState.Success("Invite sent to $normalizedId")
                    // One-shot: take the user to the Requests_List on a successful send
                    // (Req 5.1). The optimistic mirror above guarantees the just-sent invite
                    // is already present when the list is shown (Req 5.2).
                    _navigateToRequests.tryEmit(Unit)
                },
                onFailure = { error ->
                    // Revert the optimistic mirror so a failed send leaves the outgoing-invite
                    // set unchanged (Req 5.4, Property 4) and surface the error (Req 5.4).
                    _optimisticOutgoing.update { list ->
                        list.filterNot { it.targetPreambleId == normalizedId }
                    }
                    // Inform the Inviter the invite was NOT sent, naming the recipient
                    // (notifications Req 2.3). Plain copy, no reward language (Req 2.4).
                    _uiState.value = WorkspaceUiState.Error(
                        error.userMessage("Couldn't send invite to $normalizedId")
                    )
                }
            )
        }
    }

    /**
     * Withdraw a sent Outgoing_Invite, delegating to the repository which deletes both the
     * recipient's incoming Friend_Request and the sender mirror (social-hub-redesign Req 5.4,
     * 4.1). The optimistic mirror (if any) is dropped immediately and restored on failure;
     * the persisted removal is reflected through the outgoing-invite listener.
     */
    fun withdrawInvite(targetUid: String) {
        val previousOptimistic = _optimisticOutgoing.value
        _optimisticOutgoing.update { list ->
            list.filterNot { it.targetUid == targetUid || it.targetPreambleId == targetUid }
        }
        viewModelScope.launch {
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.withdrawInvite(targetUid)
            }.fold(
                onSuccess = { _uiState.value = WorkspaceUiState.Success("Invite withdrawn") },
                onFailure = { error ->
                    _optimisticOutgoing.value = previousOptimistic
                    _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to withdraw invite"))
                }
            )
        }
    }

    /** `https://preamble.theblankstate.com/invite/{my normalized Preamble_ID}` (Requirement 2.1). */
    fun buildInviteLink(): String = InviteLink.build(myPreambleId)

    /** Adapts an Android [Uri] opened from an invite link to the pure parse path. */
    fun prefillFromInviteLink(uri: Uri) = prefillFromInviteLink(uri.toString())

    /**
     * Parses an invite link and, when it carries a well-formed Preamble_ID, pre-fills the
     * add-friend flow with the normalized id (Requirement 2.2). The pre-filled value is
     * then submitted through [sendInvite], which applies the exact same validation as
     * manual entry (Requirement 2.3). A malformed link does not pre-fill anything and
     * surfaces an "invalid invite link" message (Requirement 2.4).
     */
    fun prefillFromInviteLink(link: String) {
        when (val result = InviteLink.parse(link)) {
            is InviteLink.ParseResult.Valid -> {
                // Validate the embedded id with the same rules as manual entry before
                // pre-filling, so an immediately-rejectable id (self / already-friend)
                // surfaces its message rather than silently pre-filling (2.3).
                when (val validation = validateSubmission(result.preambleId)) {
                    InviteValidation.Ok -> {
                        _prefillPreambleId.value = result.preambleId
                        _uiState.value = WorkspaceUiState.Idle
                    }
                    else -> {
                        _prefillPreambleId.value = null
                        _uiState.value = WorkspaceUiState.Error(validation.message())
                    }
                }
            }
            InviteLink.ParseResult.Invalid -> {
                // Requirement 2.4: do not pre-fill; tell the user the link is invalid.
                _prefillPreambleId.value = null
                _uiState.value = WorkspaceUiState.Error("That invite link is invalid")
            }
        }
    }

    /** Clears the pending pre-fill once the UI has applied it to the entry field. */
    fun consumePrefill() {
        _prefillPreambleId.value = null
    }

    /**
     * Present an `invite/{id}` deep link on the Social_Hub (Req 7.2). The id is normalized
     * and held as the pending pre-fill so the Invite_Entry_Experience opens pre-filled,
     * routed through the same single pre-fill mechanism as link entry rather than a second
     * state holder.
     *
     * The Social_Hub always opens, even when the id cannot be resolved: the directory is
     * consulted via the existing [WorkspaceRepository.resolvePreambleId] null path, and when
     * the id does not resolve to exactly one account a not-found message is surfaced using
     * [InviteValidation.NotFound] (Req 7.4). A blank id pre-fills nothing.
     */
    fun presentDeepLinkInvite(inviteId: String) {
        val normalizedId = PreambleId.normalize(inviteId)
        if (PreambleId.isBlank(normalizedId)) {
            _prefillPreambleId.value = null
            return
        }
        // Pre-fill the entry experience immediately so the surface opens with the id
        // shown (Req 7.2), independent of whether the directory lookup succeeds.
        _prefillPreambleId.value = normalizedId
        viewModelScope.launch {
            // Req 7.4: a Preamble_ID that does not resolve to exactly one account still
            // opens the surface but surfaces a not-found message. The resolved directory
            // entry must carry a uid for the id to map to a single account.
            val resolved = repo.resolvePreambleId(normalizedId)
            if (resolved?.get("uid") as? String == null) {
                _uiState.value = WorkspaceUiState.Error(InviteValidation.NotFound.message())
            }
        }
    }

    /**
     * Consume the deep-linked invite once it has been presented, so re-rendering the
     * Social_Hub does not re-present the same invite (Req 7.3). Routed through
     * [DeepLinkInviteState.consume], which always clears the pending state and is
     * idempotent, so [deepLinkInviteToPresent] yields `null` after the first consume and
     * stays `null` on any subsequent consume.
     */
    fun onInviteConsumed() {
        _prefillPreambleId.update { DeepLinkInviteState.consume(it) }
    }

    /**
     * Local invite validation shared by manual entry and invite-link entry so both apply
     * identical rules (Requirement 2.3). Delegates to the pure [InviteValidator]; the
     * authoritative directory lookup (1.4) and any server-side pending check remain in
     * the repository. Outbound-pending state is not mirrored locally, so an empty pending
     * set is passed and a true duplicate is still caught by the repository.
     */
    private fun validateSubmission(rawId: String): InviteValidation =
        InviteValidator.validate(
            submittedPreambleId = rawId,
            ownPreambleId = myPreambleId,
            friendPreambleIds = _friends.value.mapTo(mutableSetOf()) { it.preambleId },
            pendingPreambleIds = emptySet()
        )

    private fun InviteValidation.message(): String = when (this) {
        InviteValidation.Ok -> ""
        InviteValidation.EmptyId -> "Enter a Preamble ID to send an invite"
        InviteValidation.SelfInvite -> "You can't send a friend request to yourself"
        InviteValidation.AlreadyFriends -> "You're already friends with that user"
        InviteValidation.AlreadyPending -> "A request to that user is already pending"
        InviteValidation.NotFound -> "No user exists with that Preamble ID"
    }

    fun acceptInvite(invite: WorkspaceInvite) {
        val previousInvites = _invites.value
        val previousFriends = _friends.value
        val optimisticFriend = Friend(
            uid = invite.senderUid,
            name = invite.senderName,
            preambleId = invite.senderPreambleId
        )
        _invites.update { list -> list.filterNot { it.id == invite.id } }
        _friends.update { list -> listOf(optimisticFriend) + list.filterNot { it.uid == invite.senderUid } }

        viewModelScope.launch {
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.acceptInvite(invite, myName, myPreambleId)
            }.onFailure { error ->
                _invites.value = previousInvites
                _friends.value = previousFriends
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to accept invite"))
            }
        }
    }

    fun declineInvite(inviteId: String) {
        val previous = _invites.value
        _invites.update { list -> list.filterNot { it.id == inviteId } }
        viewModelScope.launch {
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.declineInvite(inviteId)
            }.onFailure { error ->
                _invites.value = previous
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to decline invite"))
            }
        }
    }

    fun friendRemovalImpact(friendUid: String): FriendRemovalImpact {
        val uid = currentUid ?: return FriendRemovalImpact()
        // Delegate the partition to the pure, JVM-testable logic (Requirement 5.1, Property 13).
        // A task's full member set is the admin plus its assignees, so "shared with the friend"
        // covers both the friend-is-admin and friend-is-assignee cases.
        val partition = FriendRemovalImpactLogic.partition(
            currentUid = uid,
            friendUid = friendUid,
            tasks = _collaborativeTasks.value,
            adminUid = { it.collabAdminUid },
            memberUids = { task ->
                (task.collabAssigneeUids + listOfNotNull(task.collabAdminUid)).toSet()
            }
        )
        return FriendRemovalImpact(
            administeredTasks = partition.administeredTasks,
            memberTasks = partition.memberTasks
        )
    }

    /** Only removes immediately when no collaborative task needs a lifecycle decision. */
    fun removeFriend(friendUid: String) {
        val friend = _friends.value.firstOrNull { it.uid == friendUid } ?: return
        if (friendRemovalImpact(friendUid).requiresResolution) {
            _uiState.value = WorkspaceUiState.Error("Resolve shared tasks before removing ${friend.name}")
            return
        }
        removeFriendOptimistically(friend)
    }

    /**
     * Resolves every shared task with [friend] and then removes the friendship.
     *
     * Requirement 5.6: An admin-owned affected task is only resolved by a completed
     * Transfer_Ownership. If the user confirms while any admin-owned task is unresolved
     * (i.e. they did not opt to transfer), the removal is blocked, the friend record is
     * retained, every affected task is left unchanged, and the error names each
     * unresolved admin-owned task by title.
     *
     * Requirement 5.7: Each chosen action is applied (Transfer_Ownership for admin-owned
     * tasks, Self_Removal for member tasks) and the friend relationship record is deleted
     * only after all chosen actions have succeeded.
     *
     * Requirement 5.8: If applying any chosen action (or the final friend deletion) fails,
     * the friend record is retained, the friend is restored to the displayed friend list,
     * every affected task that was mutated locally is restored to its pre-attempt state,
     * and an error message is surfaced.
     */
    fun resolveTasksAndRemoveFriend(friend: Friend, transferOwnedTasks: Boolean) {
        val impact = friendRemovalImpact(friend.uid)

        // Requirement 5.6: block the removal while any admin-owned task is unresolved.
        // The only valid resolution for an admin-owned task is Transfer_Ownership, so a
        // confirm without the transfer choice leaves those tasks unresolved.
        if (impact.administeredTasks.isNotEmpty() && !transferOwnedTasks) {
            val unresolvedTitles = impact.administeredTasks.joinToString(", ") { it.title }
            _uiState.value = WorkspaceUiState.Error(
                "Transfer ownership before removing ${friend.name}. Unresolved tasks: $unresolvedTitles"
            )
            return
        }

        val previousFriends = _friends.value
        // Optimistic UI: remove the friend from the displayed list immediately. The
        // backing friend record is only deleted after every task action succeeds (5.7).
        _friends.update { list -> list.filterNot { it.uid == friend.uid } }
        _uiState.value = WorkspaceUiState.Loading

        viewModelScope.launch {
            // Track member tasks whose local rows we delete so we can restore them on failure (5.8).
            val locallyRemoved = mutableListOf<Task>()
            try {
                // Admin-owned tasks: transfer ownership to the friend and leave the task.
                // Each collaborative write is bounded by the 30 s write timeout; a timeout
                // is mapped to the same revert path as a failure (Requirements 14.5, 5.8).
                impact.administeredTasks.forEach { task ->
                    withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                        repo.transferOwnership(task.id, friend.uid, leaveCurrentAdmin = true)
                    }.getOrThrow()
                }
                // Member tasks: self-removal.
                impact.memberTasks.forEach { task ->
                    withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                        repo.leaveCollaborativeTask(task.id)
                    }.getOrThrow()
                    deleteLocalCollaborativeTask(task)
                    locallyRemoved += task
                }
                // Delete the friend relationship record only after all actions succeed (5.7).
                // Friend removal carries the dedicated 10 s timeout (Requirement 4.4).
                withWriteTimeout(FRIEND_REMOVAL_TIMEOUT_MS) {
                    repo.removeFriend(friend.uid)
                }.getOrThrow()
                _uiState.value = WorkspaceUiState.Success("Friend removed")
            } catch (exception: Exception) {
                // Requirement 5.8: retain the friend record, restore the friend to the list,
                // and restore every locally-removed task to its pre-attempt state.
                _friends.value = previousFriends
                locallyRemoved.forEach { restoreLocalCollaborativeTask(it) }
                _uiState.value = WorkspaceUiState.Error(
                    exception.userMessage("Could not finish removing the friend")
                )
            }
        }
    }

    private suspend fun restoreLocalCollaborativeTask(task: Task) {
        withContext(Dispatchers.IO) { taskDao.insertTask(task) }
        synchronizeSubtaskRows(task)
    }

    private fun removeFriendOptimistically(friend: Friend) {
        val previous = _friends.value
        _friends.update { list -> list.filterNot { it.uid == friend.uid } }
        viewModelScope.launch {
            withWriteTimeout(FRIEND_REMOVAL_TIMEOUT_MS) {
                repo.removeFriend(friend.uid)
            }.fold(
                onSuccess = { _uiState.value = WorkspaceUiState.Success("Friend removed") },
                onFailure = { error ->
                    _friends.value = previous
                    _uiState.value = WorkspaceUiState.Error(error.userMessage("Friend removal could not be completed"))
                }
            )
        }
    }

    fun assignCollaborativeTask(friend: Friend, task: Task) {
        val uid = currentUid ?: return
        val now = System.currentTimeMillis()
        val collaborativeTask = task.copy(
            collabAdminUid = uid,
            collabAdminName = myName,
            assignedByUid = uid,
            assignedByName = myName,
            assignedToUid = friend.uid,
            assignedToName = friend.name,
            assignmentStatus = "accepted",
            collabAssigneesJson = gson.toJson(
                listOf(CollabAssigneeStatus(friend.uid, friend.name, assignedTimestamp = now))
            )
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { taskDao.updateTask(collaborativeTask) }
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.assignTask(friend, collaborativeTask, myName)
            }.onFailure { error ->
                withContext(Dispatchers.IO) { taskDao.updateTask(task) }
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to assign task"))
            }
        }
    }

    /**
     * Admin-only: add an existing friend to a collaborative task as a `pending` member,
     * reflecting the addition in local state immediately (<200 ms) before the backend
     * write (Requirements 11.7, 11.8). On failure or the 30 s write timeout, the prior
     * task state is restored exactly and an error is surfaced (Requirements 11.6, 14.5).
     */
    fun addMember(task: Task, friend: Friend) {
        if (task.collabAssignees.any { it.uid == friend.uid } || friend.uid == task.collabAdminUid) return
        val previous = task
        val now = System.currentTimeMillis()
        val optimistic = task.copy(
            collabAssigneesJson = gson.toJson(
                task.collabAssignees + CollabAssigneeStatus(
                    uid = friend.uid,
                    name = friend.name,
                    status = "pending",
                    assignedTimestamp = now
                )
            )
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { taskDao.updateTask(optimistic) }
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.addMember(task.id, friend)
            }.onFailure { error ->
                withContext(Dispatchers.IO) { taskDao.updateTask(previous) }
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to add member"))
            }
        }
    }

    fun acceptAssignment(task: Task) {
        val uid = currentUid ?: return
        val optimistic = task.withMemberStatus(uid, "accepted", false)
        _incomingAssignments.update { list -> list.map { if (it.id == task.id) optimistic else it } }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskDao.insertTask(optimistic.copy(isSyncing = false, syncFailed = false))
                synchronizeSubtaskRows(optimistic)
            }
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.updateCollabAssignmentStatus(task.id, task.collabAdminUid.orEmpty(), uid, "accepted")
            }.onFailure { error ->
                deleteLocalCollaborativeTask(optimistic)
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to accept task"))
            }
        }
    }

    fun declineAssignment(task: Task) {
        val uid = currentUid ?: return
        val previous = _incomingAssignments.value
        _incomingAssignments.update { list -> list.filterNot { it.id == task.id } }
        viewModelScope.launch {
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.updateCollabAssignmentStatus(task.id, task.collabAdminUid.orEmpty(), uid, "declined")
            }.onFailure { error ->
                _incomingAssignments.value = previous
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to decline task"))
            }
        }
    }

    fun removeMember(task: Task, memberUid: String) {
        val previous = task
        val optimistic = task.copy(
            collabAssigneesJson = gson.toJson(task.collabAssignees.filterNot { it.uid == memberUid })
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { taskDao.updateTask(optimistic) }
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.removeCollaborator(task.id, memberUid)
            }.onFailure { error ->
                withContext(Dispatchers.IO) { taskDao.updateTask(previous) }
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to remove member"))
            }
        }
    }

    fun transferOwnership(task: Task, newAdminUid: String) {
        val newAdmin = task.collabAssignees.firstOrNull { it.uid == newAdminUid } ?: return
        val previous = task
        val optimistic = task.copy(
            collabAdminUid = newAdmin.uid,
            collabAdminName = newAdmin.name,
            collabAssigneesJson = gson.toJson(
                task.collabAssignees.filterNot { it.uid == newAdminUid } +
                    CollabAssigneeStatus(
                        uid = currentUid.orEmpty(),
                        name = myName,
                        status = "accepted",
                        assignedTimestamp = task.createdTimestamp
                    )
            )
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { taskDao.updateTask(optimistic) }
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.transferOwnership(task.id, newAdminUid)
            }.onFailure { error ->
                withContext(Dispatchers.IO) { taskDao.updateTask(previous) }
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to transfer ownership"))
            }
        }
    }

    fun leaveTask(task: Task) {
        viewModelScope.launch {
            deleteLocalCollaborativeTask(task)
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.leaveCollaborativeTask(task.id)
            }.onFailure { error ->
                withContext(Dispatchers.IO) { taskDao.insertTask(task) }
                synchronizeSubtaskRows(task)
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to leave task"))
            }
        }
    }

    fun recallAssignment(task: Task) {
        viewModelScope.launch {
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.deleteCollabTaskForAll(task.id, task.collabAssignees)
            }.onFailure { error ->
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to recall task"))
            }
        }
    }

    private suspend fun deleteLocalCollaborativeTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.deleteAllSubtasks(task.id)
        taskDao.deleteTask(task)
    }

    private fun Task.withMemberStatus(
        memberUid: String,
        status: String,
        completed: Boolean
    ): Task {
        val statuses = collabAssignees.map { member ->
            if (member.uid == memberUid) {
                member.copy(
                    status = status,
                    isCompleted = completed,
                    completedTimestamp = if (completed) System.currentTimeMillis() else null
                )
            } else member
        }
        return copy(
            assignmentStatus = status,
            isCompleted = completed,
            collabAssigneesJson = gson.toJson(statuses)
        )
    }

    /**
     * Optimistically add, change, or remove the signed-in user's reaction on [task] (Req 5).
     *
     * Snapshots the task's projected reactions, applies the pure [Reactions.apply] to the local
     * Room copy within 200 ms (mirroring the other collaborative optimistic mutations), then
     * launches [WorkspaceRepository.updateMyReaction] inside the 30 s write timeout. The tapped
     * [emoji] is passed straight through — the pure logic/repository decides add/change/remove,
     * so tapping the user's current emoji toggles it off. On failure or timeout the exact
     * pre-tap snapshot is restored and an error is surfaced (Req 5.1, 5.2, 5.3, 14.5).
     */
    fun updateMyReaction(task: Task, emoji: String) {
        val uid = currentUid ?: return
        val previous = task
        val now = System.currentTimeMillis()

        // Build the reactions map (keyed by reactor uid) from the projected reactions so the pure
        // toggle/change/remove logic can decide the new local state.
        val currentReactions: List<TaskReaction> = parseReactions(task.reactionsJson)
        val reactionsMap = LinkedHashMap<String, Any?>()
        currentReactions.forEach { reaction ->
            reactionsMap[reaction.reactorUid] = linkedMapOf<String, Any?>(
                "emoji" to reaction.emoji,
                "targetUid" to reaction.targetUid,
                "createdAt" to reaction.createdAt
            )
        }

        val updatedReactions: List<TaskReaction> =
            when (val result = Reactions.apply(reactionsMap, uid, emoji, null, now)) {
                is Reactions.ReactionResult.Rejected -> {
                    _uiState.value = WorkspaceUiState.Error(result.reason)
                    return
                }
                is Reactions.ReactionResult.Updated ->
                    result.reactions.toTaskReactions(currentReactions, uid, now)
            }

        val optimistic = task.copy(
            reactionsJson = if (updatedReactions.isEmpty()) null else gson.toJson(updatedReactions)
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) { taskDao.updateTask(optimistic) }
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                // Pass the tapped emoji through; the repo/pure logic toggles it off when it
                // matches the user's current reaction.
                repo.updateMyReaction(task.id, emoji)
            }.onFailure { error ->
                withContext(Dispatchers.IO) { taskDao.updateTask(previous) }
                _uiState.value = WorkspaceUiState.Error(error.userMessage("Failed to update reaction"))
            }
        }
    }

    /** True iff a nudge may be sent now for [targetUid] on [task], per the local 60-min window. */
    fun canNudge(task: Task, targetUid: String, now: Long = System.currentTimeMillis()): Boolean =
        NudgeRateLimit.canSend(lastNudgeSentAt[task.id to targetUid], now)

    /** Millis remaining before another nudge may be sent for [targetUid] on [task] (0 when allowed). */
    fun nudgeCooldownRemaining(task: Task, targetUid: String, now: Long = System.currentTimeMillis()): Long =
        NudgeRateLimit.cooldownRemaining(lastNudgeSentAt[task.id to targetUid], now)

    /**
     * Send a Nudge to [targetUid] for [task], reflecting an optimistic nudged state within 200 ms
     * (Req 10.4). The control is gated locally via [NudgeRateLimit] against the last LOCAL nudge
     * time for the `(task, target)` pair (Req 12.2, 12.4); the authoritative checks (membership,
     * pending target, server-side rate limit) live in the `sendNudge` callable. On failure or the
     * 30 s timeout the optimistic nudged state and the recorded local send time are reverted and
     * the server reason (e.g. "Only pending members can be nudged", "nudged recently") is surfaced
     * (Req 10.5, 11.2, 12.2, 14.5).
     */
    fun nudge(task: Task, targetUid: String) {
        val uid = currentUid ?: return
        if (targetUid == uid) {
            _uiState.value = WorkspaceUiState.Error("You cannot nudge yourself")
            return
        }
        val key = task.id to targetUid
        val now = System.currentTimeMillis()

        // Gate via the local rate limit so a too-soon repeat never reaches the backend (Req 12.2).
        if (!NudgeRateLimit.canSend(lastNudgeSentAt[key], now)) {
            _uiState.value = WorkspaceUiState.Error("You nudged recently, try again later")
            return
        }

        // Optimistic nudged state (<200 ms) + record the local send time for the pair.
        val previousSentAt = lastNudgeSentAt[key]
        lastNudgeSentAt[key] = now
        _nudgedTargets.update { it + key }

        viewModelScope.launch {
            withWriteTimeout(COLLABORATIVE_WRITE_TIMEOUT_MS) {
                repo.sendNudge(task.id, targetUid)
            }.fold(
                onSuccess = { _uiState.value = WorkspaceUiState.Success("Nudge sent") },
                onFailure = { error ->
                    // Revert the optimistic nudged state and the recorded send time (Req 10.5).
                    _nudgedTargets.update { it - key }
                    if (previousSentAt == null) lastNudgeSentAt.remove(key) else lastNudgeSentAt[key] = previousSentAt
                    _uiState.value = WorkspaceUiState.Error(error.userMessage("The nudge could not be sent"))
                }
            )
        }
    }

    /** Parses the projected reactions list from [reactionsJson], or an empty list when absent. */
    private fun parseReactions(reactionsJson: String?): List<TaskReaction> =
        reactionsJson?.let {
            runCatching {
                gson.fromJson<List<TaskReaction>>(
                    it,
                    object : TypeToken<List<TaskReaction>>() {}.type
                )
            }.getOrNull()
        } ?: emptyList()

    /**
     * Re-projects a reactions map (keyed by reactor uid) back into [TaskReaction]s for local
     * rendering, preserving each existing reactor's display name and defaulting the signed-in
     * user's name to [myName].
     */
    private fun Map<String, Any?>.toTaskReactions(
        previous: List<TaskReaction>,
        selfUid: String,
        now: Long
    ): List<TaskReaction> {
        val nameByUid = previous.associate { it.reactorUid to it.reactorName }
        return entries.mapNotNull { (reactorUid, value) ->
            val entry = value as? Map<*, *> ?: return@mapNotNull null
            val emoji = entry["emoji"] as? String ?: return@mapNotNull null
            TaskReaction(
                reactorUid = reactorUid,
                reactorName = nameByUid[reactorUid] ?: if (reactorUid == selfUid) myName else reactorUid,
                emoji = emoji,
                targetUid = entry["targetUid"] as? String,
                createdAt = (entry["createdAt"] as? Number)?.toLong() ?: now
            )
        }
    }

    fun refreshData() = Unit // Snapshot listeners are the refresh mechanism.

    fun resetState() {
        _uiState.value = WorkspaceUiState.Idle
    }

    private fun reportListenerFailure(label: String, error: Throwable) {
        // Requirement 14.1: a listener failure must never tear down the app. The last
        // successfully loaded data for this data set is retained (we never clear the
        // backing StateFlow here), and we surface a message naming the affected data set.
        Log.e(TAG, "$label listener stopped", error)
        _uiState.value = WorkspaceUiState.Error("Could not load $label. Showing the last loaded data.")
    }

    /**
     * Runs a repository write under a timeout and folds a timeout into the same failed
     * [Result] path as any other backend failure, so callers revert optimistic state and
     * surface a message identically (Requirements 4.4, 14.5).
     */
    private suspend fun <T> withWriteTimeout(
        timeoutMillis: Long,
        block: suspend () -> Result<T>
    ): Result<T> = try {
        withTimeout(timeoutMillis) { block() }
    } catch (timeout: TimeoutCancellationException) {
        Log.e(TAG, "Write timed out after ${timeoutMillis}ms", timeout)
        Result.failure(timeout)
    }

    private fun Throwable.userMessage(fallback: String): String = when {
        // A timeout's technical message ("Timed out waiting for 10000 ms") is not useful
        // to the user; fall back to the caller's friendly text (Requirements 4.4, 14.5).
        this is TimeoutCancellationException -> fallback
        else -> localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }

    private companion object {
        const val TAG = "WorkspaceViewModel"

        /** Friend removal is treated as failed after 10 s (Requirement 4.4). */
        const val FRIEND_REMOVAL_TIMEOUT_MS = 10_000L

        /** All other collaborative / friend writes are treated as failed after 30 s (Requirement 14.5). */
        const val COLLABORATIVE_WRITE_TIMEOUT_MS = 30_000L
    }
}

sealed class WorkspaceUiState {
    data object Idle : WorkspaceUiState()
    data object Loading : WorkspaceUiState()
    data class Success(val message: String) : WorkspaceUiState()
    data class Error(val message: String) : WorkspaceUiState()
}

/**
 * Which Social_Hub list listener an emission/failure relates to, used to derive
 * [WorkspaceViewModel.socialHubLoadState] (Req 1.6, 1.7).
 */
private enum class LoadSource(val label: String) {
    Friends("friends"),
    Leaderboard("leaderboard")
}

/**
 * The Social_Hub load state (Req 1.6, 1.7): `Loading` until the first friends/leaderboard
 * emission, `Loaded` once both have emitted, or `Error(message)` when a list listener fails.
 */
sealed class SocialHubLoadState {
    data object Loading : SocialHubLoadState()
    data object Loaded : SocialHubLoadState()
    data class Error(val message: String) : SocialHubLoadState()
}
