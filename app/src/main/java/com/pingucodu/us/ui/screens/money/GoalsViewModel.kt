package com.pingucodu.us.ui.screens.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.goals.DeleteGoalResult
import com.pingucodu.us.data.goals.GoalsRepository
import com.pingucodu.us.data.goals.GoalsResult
import com.pingucodu.us.data.goals.SaveGoalResult
import com.pingucodu.us.data.network.SavingsGoalDto
import com.pingucodu.us.data.network.SavingsGoalRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsUiState(
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val goals: List<SavingsGoalDto> = emptyList(),
    val currentUsername: String? = null,
    val errorMessage: String? = null,
    val expandedGoalId: String? = null,
    val showGoalForm: Boolean = false,
    val editingGoal: SavingsGoalDto? = null,
    /** The goal the "add money" sheet is open for, or null when it's closed. */
    val contributionGoal: SavingsGoalDto? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
) {
    val active: List<SavingsGoalDto> get() = goals.filter { it.status == "active" }
    val reached: List<SavingsGoalDto> get() = goals.filter { it.status == "reached" }
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalsRepository: GoalsRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.loggedInUsername.collect { username ->
                _uiState.update { it.copy(currentUsername = username) }
            }
        }
    }

    /** Called when the goals section (re)enters composition - drops the stale list first so the
     * screen shows skeleton cards instead of the pull-to-refresh spinner, which should only
     * appear when the user actually pulls down to refresh. */
    fun refreshOnEntry() {
        _uiState.update { it.copy(goals = emptyList(), hasLoadedOnce = false) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = goalsRepository.getGoals(status = "everything")) {
                is GoalsResult.Success -> _uiState.update {
                    it.copy(isLoading = false, hasLoadedOnce = true, goals = result.goals)
                }
                is GoalsResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, hasLoadedOnce = true, errorMessage = result.message)
                }
            }
        }
    }

    fun toggleExpanded(goalId: String) {
        _uiState.update { it.copy(expandedGoalId = if (it.expandedGoalId == goalId) null else goalId) }
    }

    fun openAddGoal() {
        _uiState.update { it.copy(showGoalForm = true, editingGoal = null, formError = null) }
    }

    fun openEditGoal(goal: SavingsGoalDto) {
        _uiState.update { it.copy(showGoalForm = true, editingGoal = goal, formError = null) }
    }

    fun openContribution(goal: SavingsGoalDto) {
        _uiState.update { it.copy(contributionGoal = goal, formError = null) }
    }

    fun dismissForms() {
        _uiState.update {
            it.copy(showGoalForm = false, editingGoal = null, contributionGoal = null, formError = null, isSubmitting = false)
        }
    }

    fun submitGoal(request: SavingsGoalRequest) {
        val editingId = _uiState.value.editingGoal?.id
        submit { goalsRepository.saveGoal(editingId, request) }
    }

    /** Positive [amountCents] adds money, negative takes it out. */
    fun submitContribution(amountCents: Long, note: String?) {
        val goalId = _uiState.value.contributionGoal?.id ?: return
        submit { goalsRepository.addContribution(goalId, amountCents, note) }
    }

    fun deleteContribution(goalId: String, contributionId: String) {
        viewModelScope.launch {
            when (val result = goalsRepository.deleteContribution(goalId, contributionId)) {
                is SaveGoalResult.Success -> replaceGoal(result.goal)
                is SaveGoalResult.ValidationError -> _uiState.update { it.copy(errorMessage = result.message) }
                is SaveGoalResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            when (val result = goalsRepository.deleteGoal(id)) {
                DeleteGoalResult.Success -> _uiState.update { state -> state.copy(goals = state.goals.filterNot { it.id == id }) }
                is DeleteGoalResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    private fun submit(call: suspend () -> SaveGoalResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null) }
            when (val result = call()) {
                is SaveGoalResult.Success -> {
                    dismissForms()
                    replaceGoal(result.goal)
                }
                is SaveGoalResult.ValidationError -> _uiState.update { it.copy(isSubmitting = false, formError = result.message) }
                is SaveGoalResult.NetworkError -> _uiState.update { it.copy(isSubmitting = false, formError = result.message) }
            }
        }
    }

    /** Swaps in the server's fresh copy of a goal, or prepends it if it's new. */
    private fun replaceGoal(goal: SavingsGoalDto) {
        _uiState.update { state ->
            val exists = state.goals.any { it.id == goal.id }
            state.copy(goals = if (exists) state.goals.map { if (it.id == goal.id) goal else it } else listOf(goal) + state.goals)
        }
    }
}
