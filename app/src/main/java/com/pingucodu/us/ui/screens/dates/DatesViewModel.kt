package com.pingucodu.us.ui.screens.dates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.dates.DatesRepository
import com.pingucodu.us.data.dates.DatesResult
import com.pingucodu.us.data.dates.DeleteDateResult
import com.pingucodu.us.data.dates.SaveDateResult
import com.pingucodu.us.data.network.SpecialDateDto
import com.pingucodu.us.data.network.SpecialDateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DatesUiState(
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val dates: List<SpecialDateDto> = emptyList(),
    val errorMessage: String? = null,
    val showForm: Boolean = false,
    /** Kind preselected in the add form; ignored when [editing] is set. */
    val formKind: String = KIND_COUNTDOWN,
    val editing: SpecialDateDto? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
) {
    val upcoming: List<SpecialDateDto> get() = dates.filter { it.kind == KIND_COUNTDOWN && !it.isPast }
    val milestones: List<SpecialDateDto> get() = dates.filter { it.kind == KIND_MILESTONE }
    val past: List<SpecialDateDto> get() = dates.filter { it.kind == KIND_COUNTDOWN && it.isPast }
}

const val KIND_COUNTDOWN = "countdown"
const val KIND_MILESTONE = "milestone"

@HiltViewModel
class DatesViewModel @Inject constructor(private val datesRepository: DatesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DatesUiState())
    val uiState: StateFlow<DatesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = datesRepository.getDates()) {
                is DatesResult.Success -> _uiState.update {
                    it.copy(isLoading = false, hasLoadedOnce = true, dates = result.dates)
                }
                is DatesResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, hasLoadedOnce = true, errorMessage = result.message)
                }
            }
        }
    }

    fun openAddForm(kind: String = KIND_COUNTDOWN) {
        _uiState.update { it.copy(showForm = true, formKind = kind, editing = null, formError = null) }
    }

    fun openEditForm(date: SpecialDateDto) {
        _uiState.update { it.copy(showForm = true, editing = date, formError = null) }
    }

    fun dismissForm() {
        _uiState.update { it.copy(showForm = false, editing = null, formError = null, isSubmitting = false) }
    }

    fun submit(request: SpecialDateRequest) {
        val editingId = _uiState.value.editing?.id
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null) }
            when (val result = datesRepository.saveDate(editingId, request)) {
                is SaveDateResult.Success -> {
                    dismissForm()
                    refresh()
                }
                is SaveDateResult.ValidationError -> _uiState.update { it.copy(isSubmitting = false, formError = result.message) }
                is SaveDateResult.NetworkError -> _uiState.update { it.copy(isSubmitting = false, formError = result.message) }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            when (val result = datesRepository.deleteDate(id)) {
                DeleteDateResult.Success -> _uiState.update { state -> state.copy(dates = state.dates.filterNot { it.id == id }) }
                is DeleteDateResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
