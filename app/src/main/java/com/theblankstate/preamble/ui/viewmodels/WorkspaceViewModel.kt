package com.theblankstate.preamble.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.repository.WorkspaceInvite
import com.theblankstate.preamble.repository.WorkspaceRepository
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = WorkspaceRepository()
    
    val myPreambleId: String = UserProfileStore.ensurePreambleId(application)
    private val myName: String = UserProfileStore.load(application).name ?: "Anonymous User"

    private val database = PreambleDatabase.getInstance(application)
    private val taskRepo = TaskRepository(database.taskDao())

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _invites = MutableStateFlow<List<WorkspaceInvite>>(emptyList())
    val invites: StateFlow<List<WorkspaceInvite>> = _invites.asStateFlow()

    private val _incomingAssignments = MutableStateFlow<List<Task>>(emptyList())
    val incomingAssignments: StateFlow<List<Task>> = _incomingAssignments.asStateFlow()

    private val _outgoingAssignments = MutableStateFlow<List<Task>>(emptyList())
    val outgoingAssignments: StateFlow<List<Task>> = _outgoingAssignments.asStateFlow()
    
    private val _uiState = MutableStateFlow<WorkspaceUiState>(WorkspaceUiState.Idle)
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getFriendsFlow().collect { _friends.value = it }
        }
        viewModelScope.launch {
            repo.getPendingInvitesFlow().collect { _invites.value = it }
        }
        viewModelScope.launch {
            repo.getIncomingAssignmentsFlow().collect { assignments ->
                _incomingAssignments.value = assignments
                
                // Sync with local Room database safely in IO dispatcher
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val acceptedOnRemote = assignments.filter { it.assignmentStatus == "accepted" || it.assignmentStatus == "completed" }
                        val acceptedRemoteIds = acceptedOnRemote.map { it.id }.toSet()

                        // 1. Ensure all accepted remote assignments are in local DB
                        for (remoteTask in acceptedOnRemote) {
                            val localTask = database.taskDao().getTaskById(remoteTask.id)
                            if (localTask == null) {
                                val taskToInsert = remoteTask.copy(
                                    isSyncing = false,
                                    syncFailed = false
                                )
                                database.taskDao().insertTask(taskToInsert)
                            } else {
                                // Update completion state if it changed on remote
                                if (localTask.isCompleted != remoteTask.isCompleted) {
                                    val updatedTask = localTask.copy(
                                        isCompleted = remoteTask.isCompleted,
                                        completedTimestamp = remoteTask.completedTimestamp,
                                        completedDate = remoteTask.completedDate,
                                        assignmentStatus = remoteTask.assignmentStatus
                                    )
                                    database.taskDao().updateTask(updatedTask)
                                }
                            }
                        }

                        // 2. Remove any local tasks that were recalled/deleted on remote
                        val localAssigned = database.taskDao().getLocalAssignedTasks()
                        for (localTask in localAssigned) {
                            if (localTask.id !in acceptedRemoteIds) {
                                database.taskDao().deleteTask(localTask)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WorkspaceViewModel", "Error syncing collaborative tasks with Room", e)
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.getOutgoingAssignmentsFlow().collect { _outgoingAssignments.value = it }
        }
    }

    fun sendInvite(targetId: String) {
        if (targetId.isBlank()) return
        _uiState.value = WorkspaceUiState.Loading
        viewModelScope.launch {
            try {
                val result = repo.sendInvite(targetId, myName, myPreambleId)
                if (result.isSuccess) {
                    _uiState.value = WorkspaceUiState.Success("Invite sent successfully!")
                } else {
                    _uiState.value = WorkspaceUiState.Error(result.exceptionOrNull()?.message ?: "Failed to send invite")
                }
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception sending invite", e)
                _uiState.value = WorkspaceUiState.Error(e.localizedMessage ?: "Failed to send invite due to an error")
            }
        }
    }

    fun acceptInvite(invite: WorkspaceInvite) {
        viewModelScope.launch {
            try {
                repo.acceptInvite(invite, myName, myPreambleId)
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception accepting invite", e)
            }
        }
    }

    fun declineInvite(inviteId: String) {
        viewModelScope.launch {
            try {
                repo.declineInvite(inviteId)
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception declining invite", e)
            }
        }
    }

    fun removeFriend(friendUid: String) {
        viewModelScope.launch {
            try {
                repo.removeFriend(friendUid)
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception removing friend", e)
            }
        }
    }

    fun refreshData() {
        // Data is real-time via Firestore Flow listeners. 
        // This function provides a hook for PullToRefresh if we ever need explicit reloading.
    }

    fun resetState() {
        _uiState.value = WorkspaceUiState.Idle
    }

    // ── Collaborative Assignment Actions ──

    fun assignCollaborativeTask(friend: Friend, task: Task) {
        viewModelScope.launch {
            try {
                repo.assignTask(friend, task, myName)
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception assigning task", e)
            }
        }
    }

    fun acceptAssignment(task: Task) {
        viewModelScope.launch {
            try {
                repo.updateAssignmentStatus(task.id, task.assignedByUid ?: "", "accepted")
                
                val localTask = task.copy(
                    assignmentStatus = "accepted",
                    isSyncing = false,
                    syncFailed = false
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    database.taskDao().insertTask(localTask)
                }
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception accepting assignment", e)
            }
        }
    }

    fun declineAssignment(task: Task) {
        viewModelScope.launch {
            try {
                repo.updateAssignmentStatus(task.id, task.assignedByUid ?: "", "declined")
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception declining assignment", e)
            }
        }
    }

    fun recallAssignment(task: Task) {
        viewModelScope.launch {
            try {
                repo.deleteAssignment(task.id, task.assignedToUid ?: "")
            } catch (e: Exception) {
                Log.e("WorkspaceViewModel", "Exception recalling assignment", e)
            }
        }
    }
}

sealed class WorkspaceUiState {
    object Idle : WorkspaceUiState()
    object Loading : WorkspaceUiState()
    data class Success(val message: String) : WorkspaceUiState()
    data class Error(val message: String) : WorkspaceUiState()
}
