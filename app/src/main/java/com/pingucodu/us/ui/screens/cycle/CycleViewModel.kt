package com.pingucodu.us.ui.screens.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.cycle.AddCycleLogResult
import com.pingucodu.us.data.cycle.CycleLogsResult
import com.pingucodu.us.data.cycle.CycleRepository
import com.pingucodu.us.data.cycle.CycleStatusResult
import com.pingucodu.us.data.cycle.DeleteCycleLogResult
import com.pingucodu.us.data.network.CycleLogDto
import com.pingucodu.us.data.network.CycleStatusDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CycleUiState(
    val isLoading: Boolean = true,
    val currentUsername: String? = null,
    val status: CycleStatusDto? = null,
    val logs: List<CycleLogDto> = emptyList(),
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val dialogError: String? = null,
    val isSubmitting: Boolean = false,
) {
    val canLog: Boolean get() = currentUsername != null && currentUsername == status?.trackedUser
}

@HiltViewModel
class CycleViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CycleUiState())
    val uiState: StateFlow<CycleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.loggedInUsername.collect { username ->
                _uiState.update { it.copy(currentUsername = username) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (statusResult, logsResult) = coroutineScope {
                val statusDeferred = async { cycleRepository.getStatus() }
                val logsDeferred = async { cycleRepository.getLogs() }
                statusDeferred.await() to logsDeferred.await()
            }
            _uiState.update { state ->
                val error = (statusResult as? CycleStatusResult.NetworkError)?.message
                    ?: (logsResult as? CycleLogsResult.NetworkError)?.message
                state.copy(
                    isLoading = false,
                    status = (statusResult as? CycleStatusResult.Success)?.status ?: state.status,
                    logs = (logsResult as? CycleLogsResult.Success)?.logs ?: state.logs,
                    errorMessage = error,
                )
            }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, dialogError = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, dialogError = null) }
    }

    fun addLog(logDate: String, flow: String, note: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, dialogError = null) }
            when (val result = cycleRepository.addLog(logDate, flow, note)) {
                is AddCycleLogResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, showAddDialog = false) }
                    refresh()
                }
                is AddCycleLogResult.Forbidden ->
                    _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                is AddCycleLogResult.NetworkError ->
                    _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
            }
        }
    }

    fun deleteLog(id: String) {
        viewModelScope.launch {
            when (val result = cycleRepository.deleteLog(id)) {
                DeleteCycleLogResult.Success -> refresh()
                is DeleteCycleLogResult.Forbidden -> _uiState.update { it.copy(errorMessage = result.message) }
                is DeleteCycleLogResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
