package com.pingucodu.us.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.cycle.CycleRepository
import com.pingucodu.us.data.cycle.CycleStatusResult
import com.pingucodu.us.data.dates.DatesRepository
import com.pingucodu.us.data.dates.DatesResult
import com.pingucodu.us.data.goals.GoalsRepository
import com.pingucodu.us.data.goals.GoalsResult
import com.pingucodu.us.data.money.BalanceResult
import com.pingucodu.us.data.money.ExpenseRepository
import com.pingucodu.us.data.money.ExpensesResult
import com.pingucodu.us.data.network.CycleStatusDto
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.NudgeDto
import com.pingucodu.us.data.network.SavingsGoalDto
import com.pingucodu.us.data.network.SpecialDateDto
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.nudge.LatestNudgeResult
import com.pingucodu.us.data.nudge.NudgeRepository
import com.pingucodu.us.data.nudge.SendNudgeResult
import com.pingucodu.us.data.stash.StashItemsResult
import com.pingucodu.us.data.stash.StashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

/** The nudge button's transient feedback state - Sent/Failed flash for a moment, then go back to Idle. */
sealed interface NudgeStatus {
    data object Idle : NudgeStatus
    data object Sending : NudgeStatus
    data object Sent : NudgeStatus
    data class Failed(val message: String) : NudgeStatus
}

/**
 * [badgeCode] is the 2-letter (or "₹") tag shown in the row's source badge; only meaningful for STASH.
 * [author] is kept separate from [text] (rather than baked in) so the composable that renders it can
 * apply the name-masking setting, which a ViewModel has no access to.
 */
data class ActivityFeedItem(
    val source: ActivitySource,
    val text: String,
    val timeLabel: String,
    val badgeCode: String = "",
    val author: String? = null,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val username: String? = null,
    val net: Map<String, Long> = emptyMap(),
    val openExpenseCount: Int = 0,
    val recurringExpenses: List<ExpenseDto> = emptyList(),
    val cycleStatus: CycleStatusDto? = null,
    val stashSavedCount: Int = 0,
    val stashTodoCount: Int = 0,
    val activityFeed: List<ActivityFeedItem> = emptyList(),
    val nextCountdown: SpecialDateDto? = null,
    val nextMilestone: SpecialDateDto? = null,
    val topGoal: SavingsGoalDto? = null,
    val activeGoalCount: Int = 0,
    val latestNudge: NudgeDto? = null,
    val latestNudgeTimeLabel: String = "",
    val nudgeStatus: NudgeStatus = NudgeStatus.Idle,
    val errorMessage: String? = null,
)

private val SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val cycleRepository: CycleRepository,
    private val stashRepository: StashRepository,
    private val authRepository: AuthRepository,
    private val nudgeRepository: NudgeRepository,
    private val datesRepository: DatesRepository,
    private val goalsRepository: GoalsRepository,
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

    /** Called when the Home tab (re)enters composition - resets [HomeUiState.hasLoadedOnce] so
     * the dashboard shows skeleton cards instead of the pull-to-refresh spinner, which should
     * only appear when the user actually pulls down to refresh. */
    fun refreshOnEntry() {
        _uiState.update { it.copy(hasLoadedOnce = false) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (expensesResult, balanceResult, cycleResult, stashResult) = coroutineScope {
                val expensesDeferred = async { expenseRepository.getExpenses(status = "open") }
                val balanceDeferred = async { expenseRepository.getBalance() }
                val cycleDeferred = async { cycleRepository.getStatus() }
                val stashDeferred = async { stashRepository.getItems(status = "everything") }
                // The "us" cards below are extras: if they fail, their cards just keep their last value
                // instead of turning the whole dashboard into an error.
                val datesDeferred = async { datesRepository.getDates() }
                val goalsDeferred = async { goalsRepository.getGoals(status = "active") }
                val nudgeDeferred = async { nudgeRepository.getLatest() }
                applyExtras(datesDeferred.await(), goalsDeferred.await(), nudgeDeferred.await())
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
                    hasLoadedOnce = true,
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

    private fun applyExtras(dates: DatesResult, goals: GoalsResult, nudge: LatestNudgeResult) {
        _uiState.update { state ->
            val allDates = (dates as? DatesResult.Success)?.dates
            val activeGoals = (goals as? GoalsResult.Success)?.goals
            val latestNudge = if (nudge is LatestNudgeResult.Success) nudge.nudge else state.latestNudge
            state.copy(
                // The server sorts upcoming countdowns soonest-first and milestones by next anniversary.
                nextCountdown = if (allDates != null) allDates.firstOrNull { it.kind == "countdown" && !it.isPast } else state.nextCountdown,
                nextMilestone = if (allDates != null) allDates.firstOrNull { it.kind == "milestone" } else state.nextMilestone,
                // Closest to done first - that's the one worth teasing on Home.
                topGoal = if (activeGoals != null) {
                    activeGoals.maxByOrNull { it.savedCents.toDouble() / it.targetCents }
                } else {
                    state.topGoal
                },
                activeGoalCount = activeGoals?.size ?: state.activeGoalCount,
                latestNudge = latestNudge,
                latestNudgeTimeLabel = latestNudge?.let { relativeTime(it.createdAt) } ?: "",
            )
        }
    }

    /** [message] null = the server picks a random cute one. */
    fun sendNudge(message: String? = null) {
        if (_uiState.value.nudgeStatus == NudgeStatus.Sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(nudgeStatus = NudgeStatus.Sending) }
            val status = when (val result = nudgeRepository.sendNudge(message)) {
                is SendNudgeResult.Success -> NudgeStatus.Sent
                is SendNudgeResult.Rejected -> NudgeStatus.Failed(result.message)
                is SendNudgeResult.NetworkError -> NudgeStatus.Failed(result.message)
            }
            _uiState.update { it.copy(nudgeStatus = status) }
            delay(if (status is NudgeStatus.Sent) 2_000 else 3_000)
            _uiState.update { if (it.nudgeStatus == status) it.copy(nudgeStatus = NudgeStatus.Idle) else it }
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
            val author = if (item.type == "todo") null else item.author
            val code = when (item.type) {
                "movie" -> "mv"
                "link" -> "ln"
                "place" -> "pl"
                "note" -> "nt"
                else -> "td"
            }
            add(ActivityFeedItem(ActivitySource.STASH, item.title, relativeTime(item.createdAt), badgeCode = code, author = author))
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
