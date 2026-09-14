package com.pingucodu.us.ui.screens.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.data.stash.AddStashItemResult
import com.pingucodu.us.data.stash.DeleteStashItemResult
import com.pingucodu.us.data.stash.StashItemsResult
import com.pingucodu.us.data.stash.StashRepository
import com.pingucodu.us.data.stash.ToggleStashItemResult
import com.pingucodu.us.data.stash.UpdateStashItemResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StashStatusFilter(val wireValue: String, val label: String) {
    SAVED("saved", "saved"),
    DONE("done", "done"),
    EVERYTHING("everything", "everything"),
}

val STASH_TYPES = listOf("movie", "link", "place", "note", "todo")

data class StashUiState(
    val isLoading: Boolean = true,
    val items: List<StashItemDto> = emptyList(),
    val statusFilter: StashStatusFilter = StashStatusFilter.SAVED,
    val typeFilter: String? = null,
    val currentUsername: String? = null,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val editingItem: StashItemDto? = null,
    val dialogError: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class StashViewModel @Inject constructor(
    private val stashRepository: StashRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StashUiState())
    val uiState: StateFlow<StashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.loggedInUsername.collect { username ->
                _uiState.update { it.copy(currentUsername = username) }
            }
        }
        refresh()
    }

    fun setStatusFilter(filter: StashStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
        refresh()
    }

    fun setTypeFilter(type: String?) {
        _uiState.update { it.copy(typeFilter = type) }
        refresh()
    }

    fun refresh() {
        val statusFilter = _uiState.value.statusFilter
        val typeFilter = _uiState.value.typeFilter
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = stashRepository.getItems(statusFilter.wireValue, typeFilter)) {
                is StashItemsResult.Success -> _uiState.update { it.copy(isLoading = false, items = result.items) }
                is StashItemsResult.NetworkError ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingItem = null, dialogError = null) }
    }

    fun openEditDialog(item: StashItemDto) {
        _uiState.update { it.copy(showAddDialog = true, editingItem = item, dialogError = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingItem = null, dialogError = null) }
    }

    fun submitItem(request: StashItemRequest) {
        val editing = _uiState.value.editingItem
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, dialogError = null) }
            if (editing == null) {
                when (val result = stashRepository.addItem(request)) {
                    is AddStashItemResult.Success -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        dismissDialog()
                        refresh()
                    }
                    is AddStashItemResult.ValidationError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                    is AddStashItemResult.NetworkError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                }
            } else {
                when (val result = stashRepository.updateItem(editing.id, request)) {
                    is UpdateStashItemResult.Success -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        dismissDialog()
                        refresh()
                    }
                    is UpdateStashItemResult.ValidationError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                    is UpdateStashItemResult.NetworkError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                }
            }
        }
    }

    fun toggleItem(id: String) {
        viewModelScope.launch {
            when (val result = stashRepository.toggleItem(id)) {
                is ToggleStashItemResult.Success -> refresh()
                is ToggleStashItemResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            when (val result = stashRepository.deleteItem(id)) {
                DeleteStashItemResult.Success -> refresh()
                is DeleteStashItemResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
