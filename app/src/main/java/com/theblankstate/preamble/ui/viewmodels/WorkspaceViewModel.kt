package com.theblankstate.preamble.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.repository.WorkspaceInvite
import com.theblankstate.preamble.repository.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = WorkspaceRepository()
    
    val myPreambleId: String = UserProfileStore.ensurePreambleId(application)
    private val myName: String = UserProfileStore.load(application).name ?: "Anonymous User"

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _invites = MutableStateFlow<List<WorkspaceInvite>>(emptyList())
    val invites: StateFlow<List<WorkspaceInvite>> = _invites.asStateFlow()
    
    private val _uiState = MutableStateFlow<WorkspaceUiState>(WorkspaceUiState.Idle)
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getFriendsFlow().collect { _friends.value = it }
        }
        viewModelScope.launch {
            repo.getPendingInvitesFlow().collect { _invites.value = it }
        }
    }

    fun sendInvite(targetId: String) {
        if (targetId.isBlank()) return
        _uiState.value = WorkspaceUiState.Loading
        viewModelScope.launch {
            val result = repo.sendInvite(targetId, myName, myPreambleId)
            if (result.isSuccess) {
                _uiState.value = WorkspaceUiState.Success("Invite sent successfully!")
            } else {
                _uiState.value = WorkspaceUiState.Error(result.exceptionOrNull()?.message ?: "Failed to send invite")
            }
        }
    }

    fun acceptInvite(invite: WorkspaceInvite) {
        viewModelScope.launch {
            repo.acceptInvite(invite, myName, myPreambleId)
        }
    }

    fun declineInvite(inviteId: String) {
        viewModelScope.launch {
            repo.declineInvite(inviteId)
        }
    }

    fun removeFriend(friendUid: String) {
        viewModelScope.launch {
            repo.removeFriend(friendUid)
        }
    }

    fun refreshData() {
        // Data is real-time via Firestore Flow listeners. 
        // This function provides a hook for PullToRefresh if we ever need explicit reloading.
    }

    fun resetState() {
        _uiState.value = WorkspaceUiState.Idle
    }
}

sealed class WorkspaceUiState {
    object Idle : WorkspaceUiState()
    object Loading : WorkspaceUiState()
    data class Success(val message: String) : WorkspaceUiState()
    data class Error(val message: String) : WorkspaceUiState()
}
