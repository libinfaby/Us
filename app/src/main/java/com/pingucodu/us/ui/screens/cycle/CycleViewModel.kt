package com.pingucodu.us.ui.screens.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.cycle.AddCycleLogResult
import com.pingucodu.us.data.cycle.AddObservationResult
import com.pingucodu.us.data.cycle.CycleLogsResult
import com.pingucodu.us.data.cycle.CycleRepository
import com.pingucodu.us.data.cycle.CycleStatusResult
import com.pingucodu.us.data.cycle.DeleteCycleLogResult
import com.pingucodu.us.data.cycle.DeleteObservationResult
import com.pingucodu.us.data.cycle.ObservationsResult
import com.pingucodu.us.data.network.CycleLogDto
import com.pingucodu.us.data.network.CycleObservationDto
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
    val observations: List<CycleObservationDto> = emptyList(),
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val dialogError: String? = null,
    val isSubmitting: Boolean = false,
    val observationError: String? = null,
    val isSubmittingObservation: Boolean = false,
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
            val (statusResult, logsResult, observationsResult) = coroutineScope {
                val statusDeferred = async { cycleRepository.getStatus() }
                val logsDeferred = async { cycleRepository.getLogs() }
                val observationsDeferred = async { cycleRepository.getObservations() }
                Triple(statusDeferred.await(), logsDeferred.await(), observationsDeferred.await())
            }
            _uiState.update { state ->
                val error = (statusResult as? CycleStatusResult.NetworkError)?.message
                    ?: (logsResult as? CycleLogsResult.NetworkError)?.message
                    ?: (observationsResult as? ObservationsResult.NetworkError)?.message
                state.copy(
                    isLoading = false,
                    status = (statusResult as? CycleStatusResult.Success)?.status ?: state.status,
                    logs = when (logsResult) {
                        is CycleLogsResult.Success -> logsResult.logs
                        is CycleLogsResult.Forbidden -> emptyList()
                        is CycleLogsResult.NetworkError -> state.logs
                    },
                    observations = when (observationsResult) {
                        is ObservationsResult.Success -> observationsResult.observations
                        is ObservationsResult.Forbidden -> emptyList()
                        is ObservationsResult.NetworkError -> state.observations
                    },
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

    fun addLog(logDate: String, flow: String?, note: String?, tags: List<String> = emptyList(), partnerNote: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, dialogError = null) }
            when (val result = cycleRepository.addLog(logDate, flow, note, tags, partnerNote)) {
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

    fun addObservation(tags: List<String>, note: String?, date: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingObservation = true, observationError = null) }
            when (val result = cycleRepository.addObservation(tags, note, date)) {
                is AddObservationResult.Success -> {
                    _uiState.update { it.copy(isSubmittingObservation = false) }
                    refresh()
                }
                is AddObservationResult.Forbidden ->
                    _uiState.update { it.copy(isSubmittingObservation = false, observationError = result.message) }
                is AddObservationResult.NetworkError ->
                    _uiState.update { it.copy(isSubmittingObservation = false, observationError = result.message) }
            }
        }
    }

    fun deleteObservation(date: String) {
        viewModelScope.launch {
            when (val result = cycleRepository.deleteObservation(date)) {
                DeleteObservationResult.Success -> refresh()
                is DeleteObservationResult.Forbidden -> _uiState.update { it.copy(observationError = result.message) }
                is DeleteObservationResult.NetworkError -> _uiState.update { it.copy(observationError = result.message) }
            }
        }
    }
}
