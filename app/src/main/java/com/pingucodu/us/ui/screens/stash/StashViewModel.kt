package com.pingucodu.us.ui.screens.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.money.AddMemoryResult
import com.pingucodu.us.data.money.CreateHangoutResult
import com.pingucodu.us.data.money.DeleteHangoutResult
import com.pingucodu.us.data.money.DeleteMemoryResult
import com.pingucodu.us.data.money.ExpenseRepository
import com.pingucodu.us.data.money.HangoutsResult
import com.pingucodu.us.data.money.UpdateHangoutResult
import com.pingucodu.us.data.money.UpdateMemoryResult
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.data.network.HangoutMemoryDto
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

data class StashTypeSpec(val value: String, val label: String)

val STASH_TYPES = listOf(
    StashTypeSpec("movie", "movie"),
    StashTypeSpec("link", "link"),
    StashTypeSpec("place", "place"),
    StashTypeSpec("note", "note"),
    StashTypeSpec("todo", "to-do"),
)

const val HANGOUTS_CATEGORY = "hangouts"
const val ALL_CATEGORY = "all"

data class StashUiState(
    val isLoading: Boolean = true,
    val items: List<StashItemDto> = emptyList(),
    val allTags: List<String> = emptyList(),
    val category: String = ALL_CATEGORY,
    val currentUsername: String? = null,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val editingItem: StashItemDto? = null,
    val dialogError: String? = null,
    val isSubmitting: Boolean = false,

    val hangouts: List<HangoutDto> = emptyList(),
    val hangoutsLoaded: Boolean = false,
    val hangoutsLoading: Boolean = false,
    val showHangoutDialog: Boolean = false,
    val editingHangout: HangoutDto? = null,
    val hangoutDialogError: String? = null,
    val isHangoutSubmitting: Boolean = false,

    val memoryDialogHangout: HangoutDto? = null,
    val editingMemory: HangoutMemoryDto? = null,
    val memoryDialogError: String? = null,
    val isMemorySubmitting: Boolean = false,
) {
    val typeFilter: String? get() = if (category == ALL_CATEGORY || category == HANGOUTS_CATEGORY) null else category
    val isHangoutCategory: Boolean get() = category == HANGOUTS_CATEGORY
}

@HiltViewModel
class StashViewModel @Inject constructor(
    private val stashRepository: StashRepository,
    private val expenseRepository: ExpenseRepository,
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
        refreshAllTags()
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(category = category) }
        if (category == HANGOUTS_CATEGORY) {
            if (!_uiState.value.hangoutsLoaded) refreshHangouts()
        } else {
            refresh()
        }
    }

    fun refresh() {
        val typeFilter = _uiState.value.typeFilter
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = stashRepository.getItems(status = "everything", type = typeFilter)) {
                is StashItemsResult.Success -> _uiState.update { it.copy(isLoading = false, items = result.items) }
                is StashItemsResult.NetworkError ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    /** The full set of tags ever used, regardless of the current category filter — powers the
     * "suggested tags" chips in the add/edit form so previously-used tags are one tap away. */
    fun refreshAllTags() {
        viewModelScope.launch {
            when (val result = stashRepository.getItems(status = "everything", type = null)) {
                is StashItemsResult.Success ->
                    _uiState.update { it.copy(allTags = result.items.flatMap { item -> item.tags }.distinct()) }
                is StashItemsResult.NetworkError -> Unit
            }
        }
    }

    fun refreshHangouts() {
        viewModelScope.launch {
            _uiState.update { it.copy(hangoutsLoading = true, errorMessage = null) }
            when (val result = expenseRepository.getHangouts()) {
                is HangoutsResult.Success ->
                    _uiState.update { it.copy(hangoutsLoading = false, hangoutsLoaded = true, hangouts = result.hangouts) }
                is HangoutsResult.NetworkError ->
                    _uiState.update { it.copy(hangoutsLoading = false, errorMessage = result.message) }
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
                        refreshAllTags()
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
                        refreshAllTags()
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
                DeleteStashItemResult.Success -> {
                    refresh()
                    refreshAllTags()
                }
                is DeleteStashItemResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun openAddHangoutDialog() {
        _uiState.update { it.copy(showHangoutDialog = true, editingHangout = null, hangoutDialogError = null) }
    }

    fun openEditHangoutDialog(hangout: HangoutDto) {
        _uiState.update { it.copy(showHangoutDialog = true, editingHangout = hangout, hangoutDialogError = null) }
    }

    fun dismissHangoutDialog() {
        _uiState.update { it.copy(showHangoutDialog = false, editingHangout = null, hangoutDialogError = null) }
    }

    fun submitHangout(name: String, startDate: String?, endDate: String?) {
        val editing = _uiState.value.editingHangout
        viewModelScope.launch {
            _uiState.update { it.copy(isHangoutSubmitting = true, hangoutDialogError = null) }
            if (editing == null) {
                when (val result = expenseRepository.createHangout(name, startDate, endDate)) {
                    is CreateHangoutResult.Success -> {
                        _uiState.update { it.copy(isHangoutSubmitting = false) }
                        dismissHangoutDialog()
                        refreshHangouts()
                    }
                    is CreateHangoutResult.NetworkError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                }
            } else {
                when (val result = expenseRepository.updateHangout(editing.id, name, startDate, endDate)) {
                    is UpdateHangoutResult.Success -> {
                        _uiState.update { it.copy(isHangoutSubmitting = false) }
                        dismissHangoutDialog()
                        refreshHangouts()
                    }
                    is UpdateHangoutResult.ValidationError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                    is UpdateHangoutResult.NetworkError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                }
            }
        }
    }

    fun deleteHangout(id: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.deleteHangout(id)) {
                DeleteHangoutResult.Success -> refreshHangouts()
                is DeleteHangoutResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun openAddMemoryDialog(hangout: HangoutDto) {
        _uiState.update { it.copy(memoryDialogHangout = hangout, editingMemory = null, memoryDialogError = null) }
    }

    fun openEditMemoryDialog(hangout: HangoutDto, memory: HangoutMemoryDto) {
        _uiState.update { it.copy(memoryDialogHangout = hangout, editingMemory = memory, memoryDialogError = null) }
    }

    fun dismissMemoryDialog() {
        _uiState.update { it.copy(memoryDialogHangout = null, editingMemory = null, memoryDialogError = null) }
    }

    fun submitMemory(text: String) {
        val hangout = _uiState.value.memoryDialogHangout ?: return
        val editing = _uiState.value.editingMemory
        viewModelScope.launch {
            _uiState.update { it.copy(isMemorySubmitting = true, memoryDialogError = null) }
            if (editing == null) {
                when (val result = expenseRepository.addMemory(hangout.id, text)) {
                    is AddMemoryResult.Success -> {
                        _uiState.update { it.copy(isMemorySubmitting = false) }
                        dismissMemoryDialog()
                        refreshHangouts()
                    }
                    is AddMemoryResult.ValidationError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                    is AddMemoryResult.NetworkError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                }
            } else {
                when (val result = expenseRepository.updateMemory(hangout.id, editing.id, text)) {
                    is UpdateMemoryResult.Success -> {
                        _uiState.update { it.copy(isMemorySubmitting = false) }
                        dismissMemoryDialog()
                        refreshHangouts()
                    }
                    is UpdateMemoryResult.ValidationError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                    is UpdateMemoryResult.NetworkError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                }
            }
        }
    }

    fun deleteMemory(hangoutId: String, memoryId: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.deleteMemory(hangoutId, memoryId)) {
                DeleteMemoryResult.Success -> refreshHangouts()
                is DeleteMemoryResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
