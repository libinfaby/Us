package com.pingucodu.us.ui.screens.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.money.AddExpenseResult
import com.pingucodu.us.data.money.BalanceResult
import com.pingucodu.us.data.money.CreateHangoutResult
import com.pingucodu.us.data.money.DeleteExpenseResult
import com.pingucodu.us.data.money.ExpenseRepository
import com.pingucodu.us.data.money.ExpensesResult
import com.pingucodu.us.data.money.HangoutsResult
import com.pingucodu.us.data.money.SettleAllResult
import com.pingucodu.us.data.money.SettleExpenseResult
import com.pingucodu.us.data.money.UpdateExpenseResult
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.ExpenseRequest
import com.pingucodu.us.data.network.HangoutDto
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

enum class ExpenseStatusFilter(val wireValue: String, val label: String) {
    OPEN("open", "open"),
    SETTLED("settled", "settled"),
    EVERYTHING("everything", "everything"),
}

data class MoneyUiState(
    val isLoading: Boolean = true,
    val expenses: List<ExpenseDto> = emptyList(),
    val hangouts: List<HangoutDto> = emptyList(),
    val net: Map<String, Long> = emptyMap(),
    val currentUsername: String? = null,
    val statusFilter: ExpenseStatusFilter = ExpenseStatusFilter.OPEN,
    val hangoutFilter: String? = null,
    val showNoHangoutOnly: Boolean = false,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val editingExpense: ExpenseDto? = null,
    val dialogError: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoneyUiState())
    val uiState: StateFlow<MoneyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.loggedInUsername.collect { username ->
                _uiState.update { it.copy(currentUsername = username) }
            }
        }
        refresh()
    }

    fun setStatusFilter(filter: ExpenseStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
        refresh()
    }

    fun setHangoutFilter(hangoutId: String?) {
        _uiState.update { it.copy(hangoutFilter = hangoutId, showNoHangoutOnly = false) }
        refresh()
    }

    fun setNoHangoutFilter() {
        _uiState.update { it.copy(hangoutFilter = null, showNoHangoutOnly = true) }
        refresh()
    }

    fun refresh() {
        val statusFilter = _uiState.value.statusFilter
        val hangoutFilter = _uiState.value.hangoutFilter
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (expensesResult, balanceResult, hangoutsResult) = coroutineScope {
                val expensesDeferred = async { expenseRepository.getExpenses(statusFilter.wireValue, hangoutFilter) }
                val balanceDeferred = async { expenseRepository.getBalance() }
                val hangoutsDeferred = async { expenseRepository.getHangouts() }
                Triple(expensesDeferred.await(), balanceDeferred.await(), hangoutsDeferred.await())
            }
            _uiState.update { state ->
                val error = (expensesResult as? ExpensesResult.NetworkError)?.message
                    ?: (balanceResult as? BalanceResult.NetworkError)?.message
                    ?: (hangoutsResult as? HangoutsResult.NetworkError)?.message
                state.copy(
                    isLoading = false,
                    expenses = (expensesResult as? ExpensesResult.Success)?.expenses ?: state.expenses,
                    net = (balanceResult as? BalanceResult.Success)?.net ?: state.net,
                    hangouts = (hangoutsResult as? HangoutsResult.Success)?.hangouts ?: state.hangouts,
                    errorMessage = error,
                )
            }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingExpense = null, dialogError = null) }
    }

    fun openEditDialog(expense: ExpenseDto) {
        _uiState.update { it.copy(showAddDialog = true, editingExpense = expense, dialogError = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingExpense = null, dialogError = null) }
    }

    fun submitExpense(request: ExpenseRequest) {
        val editing = _uiState.value.editingExpense
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, dialogError = null) }
            if (editing == null) {
                when (val result = expenseRepository.addExpense(request)) {
                    is AddExpenseResult.Success -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        dismissDialog()
                        refresh()
                    }
                    is AddExpenseResult.ValidationError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                    is AddExpenseResult.NetworkError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                }
            } else {
                when (val result = expenseRepository.updateExpense(editing.id, request)) {
                    is UpdateExpenseResult.Success -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        dismissDialog()
                        refresh()
                    }
                    is UpdateExpenseResult.ValidationError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                    is UpdateExpenseResult.NetworkError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                }
            }
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.deleteExpense(id)) {
                DeleteExpenseResult.Success -> refresh()
                is DeleteExpenseResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun settleExpense(id: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.settleExpense(id)) {
                is SettleExpenseResult.Success -> refresh()
                is SettleExpenseResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun settleAll() {
        val hangoutFilter = _uiState.value.hangoutFilter
        viewModelScope.launch {
            when (val result = expenseRepository.settleAll(hangoutFilter)) {
                is SettleAllResult.Success -> refresh()
                is SettleAllResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    suspend fun createHangoutAndReturn(name: String): HangoutDto? {
        return when (val result = expenseRepository.createHangout(name)) {
            is CreateHangoutResult.Success -> {
                _uiState.update { it.copy(hangouts = it.hangouts + result.hangout) }
                result.hangout
            }
            is CreateHangoutResult.NetworkError -> {
                _uiState.update { it.copy(dialogError = result.message) }
                null
            }
        }
    }
}
