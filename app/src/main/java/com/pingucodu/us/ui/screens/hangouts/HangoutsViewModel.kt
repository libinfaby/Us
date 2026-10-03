package com.pingucodu.us.ui.screens.hangouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.dates.DatesRepository
import com.pingucodu.us.data.dates.SaveDateResult
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
import com.pingucodu.us.data.network.SpecialDateRequest
import com.pingucodu.us.ui.screens.dates.KIND_COUNTDOWN
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HangoutsUiState(
    val hangouts: List<HangoutDto> = emptyList(),
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val currentUsername: String? = null,
    val errorMessage: String? = null,

    val showHangoutDialog: Boolean = false,
    val editingHangout: HangoutDto? = null,
    val hangoutDialogError: String? = null,
    val isHangoutSubmitting: Boolean = false,

    val memoryDialogHangout: HangoutDto? = null,
    val editingMemory: HangoutMemoryDto? = null,
    val memoryDialogError: String? = null,
    val isMemorySubmitting: Boolean = false,
)

@HiltViewModel
class HangoutsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val datesRepository: DatesRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HangoutsUiState())
    val uiState: StateFlow<HangoutsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.loggedInUsername.collect { username ->
                _uiState.update { it.copy(currentUsername = username) }
            }
        }
    }

    /** Called every time the tab is opened, not just once: expenses added on Money and items
     * linked from Stash show up on the cards too. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = expenseRepository.getHangouts()) {
                is HangoutsResult.Success ->
                    _uiState.update { it.copy(isLoading = false, hasLoadedOnce = true, hangouts = result.hangouts) }
                is HangoutsResult.NetworkError ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
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

    /** [countdownDate] non-null = also add a countdown to that date once the new hangout is saved. */
    fun submitHangout(name: String, startDate: String?, endDate: String?, countdownDate: String? = null) {
        val editing = _uiState.value.editingHangout
        viewModelScope.launch {
            _uiState.update { it.copy(isHangoutSubmitting = true, hangoutDialogError = null) }
            if (editing == null) {
                when (val result = expenseRepository.createHangout(name, startDate, endDate)) {
                    is CreateHangoutResult.Success -> {
                        val countdownError = countdownDate?.let { addCountdown(name, it) }
                        _uiState.update { it.copy(isHangoutSubmitting = false) }
                        dismissHangoutDialog()
                        refresh()
                        // After the refresh, which clears errorMessage when it starts.
                        if (countdownError != null) {
                            _uiState.update { it.copy(errorMessage = "hangout saved, but the countdown wasn't: $countdownError") }
                        }
                    }
                    is CreateHangoutResult.NetworkError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                }
            } else {
                when (val result = expenseRepository.updateHangout(editing.id, name, startDate, endDate)) {
                    is UpdateHangoutResult.Success -> {
                        _uiState.update { it.copy(isHangoutSubmitting = false) }
                        dismissHangoutDialog()
                        refresh()
                    }
                    is UpdateHangoutResult.ValidationError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                    is UpdateHangoutResult.NetworkError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                }
            }
        }
    }

    /** Returns the error message, or null when the countdown was added. */
    private suspend fun addCountdown(title: String, date: String): String? =
        when (val result = datesRepository.saveDate(null, SpecialDateRequest(kind = KIND_COUNTDOWN, title = title, date = date))) {
            is SaveDateResult.Success -> null
            is SaveDateResult.ValidationError -> result.message
            is SaveDateResult.NetworkError -> result.message
        }

    fun deleteHangout(id: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.deleteHangout(id)) {
                DeleteHangoutResult.Success -> refresh()
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
                        refresh()
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
                        refresh()
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
                DeleteMemoryResult.Success -> refresh()
                is DeleteMemoryResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
