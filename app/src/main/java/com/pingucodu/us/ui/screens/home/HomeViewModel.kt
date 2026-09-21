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
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.stash.StashItemsResult
import com.pingucodu.us.data.stash.StashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class ActivitySource { MONEY, CYCLE, STASH }

/** [badgeCode] is the 2-letter (or "₹") tag shown in the row's source badge; only meaningful for STASH. */
data class ActivityFeedItem(
    val source: ActivitySource,
    val text: String,
    val timeLabel: String,
    val badgeCode: String = "",
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val username: String? = null,
    val net: Map<String, Long> = emptyMap(),
    val openExpenseCount: Int = 0,
    val recurringExpenses: List<ExpenseDto> = emptyList(),
    val cycleStatus: CycleStatusDto? = null,
    val stashSavedCount: Int = 0,
    val stashTodoCount: Int = 0,
    val activityFeed: List<ActivityFeedItem> = emptyList(),
    val errorMessage: String? = null,
)

private val SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val cycleRepository: CycleRepository,
    private val stashRepository: StashRepository,
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
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (expensesResult, balanceResult, cycleResult, stashResult) = coroutineScope {
                val expensesDeferred = async { expenseRepository.getExpenses(status = "open") }
                val balanceDeferred = async { expenseRepository.getBalance() }
                val cycleDeferred = async { cycleRepository.getStatus() }
                val stashDeferred = async { stashRepository.getItems(status = "everything") }
                listOf(expensesDeferred.await(), balanceDeferred.await(), cycleDeferred.await(), stashDeferred.await())
            }
            _uiState.update { state ->
                val error = (expensesResult as? ExpensesResult.NetworkError)?.message
                    ?: (balanceResult as? BalanceResult.NetworkError)?.message
                    ?: (cycleResult as? CycleStatusResult.NetworkError)?.message
                    ?: (stashResult as? StashItemsResult.NetworkError)?.message
                val openExpenses = (expensesResult as? ExpensesResult.Success)?.expenses
                val openExpenseCount = openExpenses?.size ?: state.openExpenseCount
                val cycleStatus = (cycleResult as? CycleStatusResult.Success)?.status ?: state.cycleStatus
                val stashItems = (stashResult as? StashItemsResult.Success)?.items
                state.copy(
                    isLoading = false,
                    openExpenseCount = openExpenseCount,
                    recurringExpenses = openExpenses?.filter { it.isRecurring } ?: state.recurringExpenses,
                    net = (balanceResult as? BalanceResult.Success)?.net ?: state.net,
                    cycleStatus = cycleStatus,
                    stashSavedCount = stashItems?.count { it.status == "saved" } ?: state.stashSavedCount,
                    stashTodoCount = stashItems?.count { it.type == "todo" && it.status == "saved" }
                        ?: state.stashTodoCount,
                    activityFeed = buildActivityFeed(openExpenseCount, cycleStatus, stashItems),
                    errorMessage = error,
                )
            }
        }
    }

    private fun buildActivityFeed(
        openExpenseCount: Int,
        cycleStatus: CycleStatusDto?,
        stashItems: List<StashItemDto>?,
    ): List<ActivityFeedItem> = buildList {
        if (openExpenseCount > 0) {
            val plural = if (openExpenseCount == 1) "expense" else "expenses"
            add(ActivityFeedItem(ActivitySource.MONEY, "$openExpenseCount $plural still open", "now"))
        }
        if (cycleStatus?.currentDay != null) {
            val phaseLabel = cycleStatus.phase ?: cycleStatus.statusLabel
            add(ActivityFeedItem(ActivitySource.CYCLE, "cycle day ${cycleStatus.currentDay} · $phaseLabel", "today"))
        }
        stashItems?.sortedByDescending { it.createdAt }?.take(4)?.forEach { item ->
            val text = if (item.type == "todo") item.title else "${item.author} shared \"${item.title}\""
            val code = when (item.type) {
                "movie" -> "mv"
                "link" -> "ln"
                "place" -> "pl"
                "note" -> "nt"
                else -> "td"
            }
            add(ActivityFeedItem(ActivitySource.STASH, text, relativeTime(item.createdAt), badgeCode = code))
        }
    }.take(6)

    private fun relativeTime(sqliteDateTime: String): String = try {
        val then = LocalDateTime.parse(sqliteDateTime, SQLITE_DATETIME).toInstant(ZoneOffset.UTC)
        val minutes = Duration.between(then, Instant.now()).toMinutes()
        when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            else -> "${minutes / (60 * 24)}d"
        }
    } catch (e: Exception) {
        ""
    }
}
