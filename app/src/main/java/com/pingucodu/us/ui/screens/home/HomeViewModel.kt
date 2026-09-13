package com.pingucodu.us.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.cycle.CycleRepository
import com.pingucodu.us.data.cycle.CycleStatusResult
import com.pingucodu.us.data.money.BalanceResult
import com.pingucodu.us.data.money.ExpenseRepository
import com.pingucodu.us.data.money.ExpensesResult
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val username: String? = null,
    val net: Map<String, Long> = emptyMap(),
    val openExpenseCount: Int = 0,
    val cycleStatus: CycleStatusDto? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val cycleRepository: CycleRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.loggedInUsername.collect { username ->
                _uiState.update { it.copy(username = username) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (expensesResult, balanceResult, cycleResult) = coroutineScope {
                val expensesDeferred = async { expenseRepository.getExpenses(status = "open") }
                val balanceDeferred = async { expenseRepository.getBalance() }
                val cycleDeferred = async { cycleRepository.getStatus() }
                Triple(expensesDeferred.await(), balanceDeferred.await(), cycleDeferred.await())
            }
            _uiState.update { state ->
                val error = (expensesResult as? ExpensesResult.NetworkError)?.message
                    ?: (balanceResult as? BalanceResult.NetworkError)?.message
                    ?: (cycleResult as? CycleStatusResult.NetworkError)?.message
                state.copy(
                    isLoading = false,
                    openExpenseCount = (expensesResult as? ExpensesResult.Success)?.expenses?.size
                        ?: state.openExpenseCount,
                    net = (balanceResult as? BalanceResult.Success)?.net ?: state.net,
                    cycleStatus = (cycleResult as? CycleStatusResult.Success)?.status ?: state.cycleStatus,
                    errorMessage = error,
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
