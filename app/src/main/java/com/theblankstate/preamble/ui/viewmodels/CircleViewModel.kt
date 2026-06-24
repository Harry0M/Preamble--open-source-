package com.theblankstate.preamble.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.theblankstate.preamble.circles.CircleDocument
import com.theblankstate.preamble.circles.CircleMemberOps
import com.theblankstate.preamble.circles.CircleTask
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.repository.Circle
import com.theblankstate.preamble.repository.CircleCompleter
import com.theblankstate.preamble.repository.CircleMember
import com.theblankstate.preamble.repository.CircleRepository
import com.theblankstate.preamble.repository.CircleTaskModel
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.repository.WorkspaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Optimistic-UI ViewModel for Shared Circles, deliberately mirroring [WorkspaceViewModel]'s
 * structure, threading, and error-message style.
 *
 * Every Circle and Circle_Task action follows the single proven pattern: pre-validate through
 * the pure `com.theblankstate.preamble.circles` results BEFORE any optimistic mutation,
 * snapshot the prior state, apply the change immediately (well within 200 ms) by updating the
 * backing [StateFlow], then launch the repository write inside [withWriteTimeout] (a 30 s
 * [WRITE_TIMEOUT_MS] cap). On failure or timeout the exact pre-action snapshot is restored and
 * an error message is surfaced through [uiState]. Real-time snapshot listeners reconcile the
 * optimistic state once the backend confirms.
 *
 * The friends list is sourced exactly as [WorkspaceViewModel] sources it (via
 * [WorkspaceRepository.getFriendsFlow]) so the add-member friends-only gate (Requirement 4.3)
 * can be enforced client-side (Decision D6).
 */
class CircleViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CircleRepository()
    private val friendsRepo = WorkspaceRepository()

    private val myName: String = UserProfileStore.load(application).name ?: DEFAULT_NAME
    private val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private val _circles = MutableStateFlow<List<Circle>>(emptyList())
    val circles: StateFlow<List<Circle>> = _circles.asStateFlow()

    private val _circleTasks = MutableStateFlow<List<CircleTaskModel>>(emptyList())
    val circleTasks: StateFlow<List<CircleTaskModel>> = _circleTasks.asStateFlow()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _uiState = MutableStateFlow<CircleUiState>(CircleUiState.Idle)
    val uiState: StateFlow<CircleUiState> = _uiState.asStateFlow()

    /** The Circle currently open on the Circle_Detail_Screen, and the job collecting its tasks. */
    private var openCircleId: String? = null
    private var taskObservationJob: Job? = null

    init {
        observeCircles()
        observeFriends()
    }

    private fun observeCircles() {
        viewModelScope.launch {
            repo.getCirclesFlow()
                .catch { reportListenerFailure("Circles", it) }
                .collect { _circles.value = it }
        }
    }

    private fun observeFriends() {
        viewModelScope.launch {
            friendsRepo.getFriendsFlow()
                .catch { reportListenerFailure("friends", it) }
                .collect { _friends.value = it }
        }
    }

    // =============================================================================================
    // TASK 8.1 — Circle lifecycle actions with snapshot-and-revert
    // =============================================================================================

    /**
     * Create a Circle (Requirements 1.2, 1.4, 1.5). Rejects an empty normalized name before any
     * mutation, reflects the new Circle optimistically (<200 ms), and removes it on failure/timeout.
     */
    fun createCircle(name: String) {
        val uid = currentUid ?: return
        val normalized = CircleDocument.normalizeName(name)
        if (normalized.isEmpty()) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_EMPTY_NAME)
            return
        }

        val previous = _circles.value
        val now = System.currentTimeMillis()
        val optimistic = Circle(
            id = "optimistic-${System.nanoTime()}",
            name = normalized,
            adminUid = uid,
            memberUids = listOf(uid),
            members = listOf(
                CircleMember(uid = uid, name = myName, role = CircleDocument.ROLE_ADMIN, status = "active")
            ),
            createdAt = now,
            updatedAt = now
        )
        _circles.update { listOf(optimistic) + it }

        viewModelScope.launch {
            withWriteTimeout { repo.createCircle(name) }
                .onFailure { error ->
                    _circles.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The Circle could not be created"))
                }
        }
    }

    /**
     * Admin renames a Circle (Requirements 3.3, 3.4, 3.6). Rejects an empty normalized name,
     * reflects the new name optimistically, and restores the prior name on failure/timeout.
     */
    fun renameCircle(circle: Circle, newName: String) {
        val normalized = CircleDocument.normalizeName(newName)
        if (normalized.isEmpty()) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_EMPTY_NAME)
            return
        }

        val previous = _circles.value
        val now = System.currentTimeMillis()
        _circles.update { list ->
            list.map { if (it.id == circle.id) it.copy(name = normalized, updatedAt = now) else it }
        }

        viewModelScope.launch {
            withWriteTimeout { repo.renameCircle(circle.id, newName) }
                .onFailure { error ->
                    _circles.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The rename could not be saved"))
                }
        }
    }

    /**
     * Admin adds a friend to a Circle (Requirements 4.2, 4.3, 4.4, 4.6, 4.8). Pre-validates the
     * friends-only gate, duplicate, and over-capacity through the pure rejection reasons before any
     * mutation, reflects the addition optimistically, and restores the prior member list on failure.
     */
    fun addMember(circle: Circle, friend: Friend) {
        // Friends-only gate (Req 4.3, Decision D6): only a uid in the user's friend list may be added.
        if (_friends.value.none { it.uid == friend.uid }) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_NOT_FRIEND)
            return
        }
        if (friend.uid in circle.memberUids) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_ALREADY_MEMBER)
            return
        }
        if (circle.memberUids.size + 1 > CircleDocument.MAX_MEMBERS) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_CIRCLE_FULL)
            return
        }

        val previous = _circles.value
        val now = System.currentTimeMillis()
        val newMember = CircleMember(
            uid = friend.uid,
            name = friend.name,
            role = CircleDocument.ROLE_MEMBER,
            status = "active"
        )
        _circles.update { list ->
            list.map {
                if (it.id == circle.id) {
                    it.copy(
                        memberUids = it.memberUids + friend.uid,
                        members = it.members + newMember,
                        updatedAt = now
                    )
                } else it
            }
        }

        viewModelScope.launch {
            withWriteTimeout { repo.addMember(circle.id, friend) }
                .onFailure { error ->
                    _circles.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The member could not be added"))
                }
        }
    }

    /**
     * Admin removes a non-admin member (Requirements 5.2, 5.3, 5.6). Rejects an admin self-removal
     * or a non-member before any mutation, reflects the removal optimistically, and restores the
     * prior member list on failure/timeout.
     */
    fun removeMember(circle: Circle, memberUid: String) {
        if (memberUid == circle.adminUid) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_ADMIN_CANNOT_REMOVE_SELF)
            return
        }
        if (memberUid !in circle.memberUids) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_NOT_A_MEMBER)
            return
        }

        val previous = _circles.value
        val now = System.currentTimeMillis()
        _circles.update { list ->
            list.map {
                if (it.id == circle.id) {
                    it.copy(
                        memberUids = it.memberUids.filterNot { uid -> uid == memberUid },
                        members = it.members.map { member ->
                            if (member.uid == memberUid) member.copy(status = "removed") else member
                        },
                        updatedAt = now
                    )
                } else it
            }
        }

        viewModelScope.launch {
            withWriteTimeout { repo.removeMember(circle.id, memberUid) }
                .onFailure { error ->
                    _circles.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The member could not be removed"))
                }
        }
    }

    /**
     * Non-admin leaves a Circle (Requirements 6.2, 6.3, 6.4). Rejects an admin leave before any
     * mutation, removes the Circle from the user's list optimistically, and restores it on
     * failure/timeout.
     */
    fun leaveCircle(circle: Circle) {
        val uid = currentUid ?: return
        if (uid == circle.adminUid) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_ADMIN_CANNOT_LEAVE)
            return
        }

        val previous = _circles.value
        _circles.update { list -> list.filterNot { it.id == circle.id } }

        viewModelScope.launch {
            withWriteTimeout { repo.leaveCircle(circle.id) }
                .onFailure { error ->
                    _circles.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("Leaving the Circle did not complete"))
                }
        }
    }

    /**
     * Admin deletes a Circle (Requirements 7.2, 7.5). Rejects a non-admin delete before any
     * mutation, removes the Circle from the list optimistically, and restores it on failure/timeout.
     * The Cloud Function cascades the Circle_Task deletes.
     */
    fun deleteCircle(circle: Circle) {
        val uid = currentUid ?: return
        if (uid != circle.adminUid) {
            _uiState.value = CircleUiState.Error(CircleMemberOps.REASON_NOT_ADMIN)
            return
        }

        val previous = _circles.value
        _circles.update { list -> list.filterNot { it.id == circle.id } }

        viewModelScope.launch {
            withWriteTimeout { repo.deleteCircle(circle.id) }
                .onFailure { error ->
                    _circles.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The Circle could not be deleted"))
                }
        }
    }

    // =============================================================================================
    // TASK 8.2 — Circle_Task actions and openCircle
    // =============================================================================================

    /**
     * Open a Circle on the Circle_Detail_Screen, starting a fresh collection of its shared task
     * list (Requirements 10.1, 10.2). A listener error retains the last-loaded tasks and surfaces a
     * message without crashing (Requirements 10.4, 15.1). Re-opening cancels the prior collection.
     */
    fun openCircle(circleId: String) {
        if (openCircleId == circleId && taskObservationJob?.isActive == true) return
        openCircleId = circleId
        taskObservationJob?.cancel()
        _circleTasks.value = emptyList()
        taskObservationJob = viewModelScope.launch {
            repo.getCircleTasksFlow(circleId)
                .catch { reportListenerFailure("Circle tasks", it) }
                .collect { _circleTasks.value = it }
        }
    }

    /**
     * Add a Circle_Task to the open Circle's shared list (Requirements 9.2, 9.3, 9.6). Rejects an
     * empty title before any mutation, reflects the new task optimistically (<200 ms), and removes
     * it on failure/timeout.
     */
    fun addTask(circleId: String, title: String) {
        val uid = currentUid ?: return
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = CircleUiState.Error("A task title is required")
            return
        }

        val previous = _circleTasks.value
        val now = System.currentTimeMillis()
        val optimistic = CircleTaskModel(
            id = "optimistic-${System.nanoTime()}",
            circleId = circleId,
            authorUid = uid,
            title = trimmed,
            isCompleted = false,
            completedBy = null,
            completedAt = null,
            createdAt = now,
            updatedAt = now
        )
        _circleTasks.update { listOf(optimistic) + it }

        viewModelScope.launch {
            withWriteTimeout { repo.addCircleTask(circleId, title) }
                .onFailure { error ->
                    _circleTasks.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The task could not be added"))
                }
        }
    }

    /**
     * Set a Circle_Task's Shared_Completion state (Requirements 11.2, 11.4, 11.5, 11.6). Reflects
     * the change optimistically, recording/clearing the Completer for the whole Circle, and
     * restores the prior completion state on failure/timeout.
     */
    fun setCompletion(task: CircleTaskModel, completed: Boolean) {
        val uid = currentUid ?: return
        val previous = _circleTasks.value
        val now = System.currentTimeMillis()
        _circleTasks.update { list ->
            list.map {
                if (it.id == task.id) {
                    it.copy(
                        isCompleted = completed,
                        completedBy = if (completed) CircleCompleter(uid = uid, name = myName) else null,
                        completedAt = if (completed) now else null,
                        updatedAt = now
                    )
                } else it
            }
        }

        viewModelScope.launch {
            withWriteTimeout { repo.setCircleTaskCompletion(task.id, completed) }
                .onFailure { error ->
                    _circleTasks.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The change could not be saved"))
                }
        }
    }

    /**
     * Edit a Circle_Task's title (Requirements 12.1, 12.5, 12.6). Gates on
     * [CircleTask.classifyEditDelete] (author or admin) and rejects an empty title before issuing
     * the write, reflects the new title optimistically, and restores the prior title on
     * failure/timeout.
     */
    fun editTaskTitle(task: CircleTaskModel, newTitle: String) {
        if (!isAuthorOrAdmin(task)) {
            _uiState.value = CircleUiState.Error(MESSAGE_NOT_AUTHORIZED)
            return
        }
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = CircleUiState.Error("A task title is required")
            return
        }

        val previous = _circleTasks.value
        val now = System.currentTimeMillis()
        _circleTasks.update { list ->
            list.map { if (it.id == task.id) it.copy(title = trimmed, updatedAt = now) else it }
        }

        viewModelScope.launch {
            withWriteTimeout { repo.editCircleTaskTitle(task.id, newTitle) }
                .onFailure { error ->
                    _circleTasks.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The change could not be saved"))
                }
        }
    }

    /**
     * Delete a Circle_Task (Requirements 12.3, 12.5, 12.6). Gates on
     * [CircleTask.classifyEditDelete] (author or admin) before issuing the write, removes the task
     * optimistically, and restores it on failure/timeout.
     */
    fun deleteTask(task: CircleTaskModel) {
        if (!isAuthorOrAdmin(task)) {
            _uiState.value = CircleUiState.Error(MESSAGE_NOT_AUTHORIZED)
            return
        }

        val previous = _circleTasks.value
        _circleTasks.update { list -> list.filterNot { it.id == task.id } }

        viewModelScope.launch {
            withWriteTimeout { repo.deleteCircleTask(task.id) }
                .onFailure { error ->
                    _circleTasks.value = previous
                    _uiState.value = CircleUiState.Error(error.userMessage("The change could not be saved"))
                }
        }
    }

    fun resetState() {
        _uiState.value = CircleUiState.Idle
    }

    // =============================================================================================
    // Helpers (mirroring WorkspaceViewModel)
    // =============================================================================================

    /**
     * Author/admin authorization gate for title-edit and delete (Requirements 12.1–12.4),
     * delegating to the pure [CircleTask.classifyEditDelete]. The Circle admin uid is resolved from
     * the loaded Circle list by the task's `circleId`.
     */
    private fun isAuthorOrAdmin(task: CircleTaskModel): Boolean {
        val requesterUid = currentUid ?: return false
        val adminUid = _circles.value.firstOrNull { it.id == task.circleId }?.adminUid.orEmpty()
        return CircleTask.classifyEditDelete(
            taskAuthorUid = task.authorUid,
            circleAdminUid = adminUid,
            requesterUid = requesterUid
        ) == CircleTask.EditDeleteDecision.ALLOW
    }

    private fun reportListenerFailure(label: String, error: Throwable) {
        // A listener failure must never tear down the app. The last successfully loaded data for
        // this data set is retained (we never clear the backing StateFlow here), and we surface a
        // message naming the affected data set (Requirements 10.4, 15.1).
        Log.e(TAG, "$label listener stopped", error)
        _uiState.value = CircleUiState.Error("Could not load $label. Showing the last loaded data.")
    }

    /**
     * Runs a repository write under the 30 s timeout and folds a timeout into the same failed
     * [Result] path as any other backend failure, so callers revert optimistic state and surface a
     * message identically (Requirement 15.2).
     */
    private suspend fun <T> withWriteTimeout(block: suspend () -> Result<T>): Result<T> = try {
        withTimeout(WRITE_TIMEOUT_MS) { block() }
    } catch (timeout: TimeoutCancellationException) {
        Log.e(TAG, "Write timed out after ${WRITE_TIMEOUT_MS}ms", timeout)
        Result.failure(timeout)
    }

    private fun Throwable.userMessage(fallback: String): String = when {
        // A timeout's technical message is not useful to the user; use the caller's friendly text.
        this is TimeoutCancellationException -> fallback
        else -> localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }

    private companion object {
        const val TAG = "CircleViewModel"
        const val DEFAULT_NAME = "Preamble user"
        const val MESSAGE_NOT_AUTHORIZED = "Only the task's author or the Circle admin can change this task."

        /** Every Circle / Circle_Task write is treated as failed after 30 s (Requirement 15.2). */
        const val WRITE_TIMEOUT_MS = 30_000L
    }
}

sealed class CircleUiState {
    data object Idle : CircleUiState()
    data object Loading : CircleUiState()
    data class Success(val message: String) : CircleUiState()
    data class Error(val message: String) : CircleUiState()
}
