package com.pingucodu.us.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.GoalContributionDto
import com.pingucodu.us.data.network.SavingsGoalDto
import com.pingucodu.us.data.network.SavingsGoalRequest
import com.pingucodu.us.ui.components.DateField
import com.pingucodu.us.ui.components.ErrorBanner
import com.pingucodu.us.ui.components.NeoBottomSheet
import com.pingucodu.us.ui.components.NeoChoiceChip
import com.pingucodu.us.ui.components.NeoDatePickerDialog
import com.pingucodu.us.ui.components.NeoField
import com.pingucodu.us.ui.components.PillActionButton
import com.pingucodu.us.ui.components.SectionLabel
import com.pingucodu.us.ui.components.SubmitButton
import com.pingucodu.us.ui.components.clickableNoRipple
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Green
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.NeoConfirmDialog
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.SkeletonGoalCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow
import com.pingucodu.us.ui.util.LocalNameMask
import com.pingucodu.us.ui.util.formatRupees
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

private val GoalCardShape = RoundedCornerShape(18.dp)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val QUICK_AMOUNTS = listOf(50_000L, 100_000L, 500_000L)

/** Parses "2000", "2,000" or "2000.50" rupees into paise; null for anything else or ≤ 0. */
private fun parseRupeesToCents(input: String): Long? =
    input.replace(",", "").trim().toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO && it.scale() <= 2 }
        ?.movePointRight(2)
        ?.toLong()

private fun centsToInput(cents: Long): String =
    BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

/** "₹4,000/month to hit it by 31 dec 2026", or null when there's no target date or nothing left. */
private fun monthlyHint(goal: SavingsGoalDto, today: LocalDate): String? {
    val target = goal.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val remaining = goal.targetCents - goal.savedCents
    if (remaining <= 0) return null
    val dateLabel = target.format(SHORT_DATE).lowercase()
    val days = ChronoUnit.DAYS.between(today, target)
    if (days <= 0) return "target date $dateLabel has passed"
    val months = ceil(days / 30.44).coerceAtLeast(1.0)
    val perMonth = ceil(remaining / months / 100.0).toLong() * 100
    return "${formatRupees(perMonth)}/month to hit it by $dateLabel"
}

/** Chunky neo-brutalist progress bar - [progress] is clamped to 0..1. */
@Composable
fun GoalProgressBar(progress: Float, modifier: Modifier = Modifier, fillColor: Color = Teal, height: Int = 18) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .border(2.5.dp, Ink, shape)
            .background(Color.White, shape),
    ) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .border(2.5.dp, Ink, shape)
                    .background(fillColor, shape),
            )
        }
    }
}

fun goalProgress(goal: SavingsGoalDto): Float =
    if (goal.targetCents <= 0) 0f else goal.savedCents.toFloat() / goal.targetCents

@Composable
fun GoalsContent(modifier: Modifier = Modifier, viewModel: GoalsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var goalToDelete by remember { mutableStateOf<SavingsGoalDto?>(null) }
    var contributionToDelete by remember { mutableStateOf<Pair<SavingsGoalDto, GoalContributionDto>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshOnEntry()
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.hasLoadedOnce,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp),
            ) {
                if (uiState.errorMessage != null) {
                    ErrorBanner(uiState.errorMessage!!)
                    Spacer(Modifier.height(12.dp))
                }

                when {
                    uiState.isLoading && !uiState.hasLoadedOnce -> repeat(2) {
                        SkeletonGoalCard()
                        Spacer(Modifier.height(16.dp))
                    }
                    uiState.goals.isEmpty() -> EmptyGoals(onAdd = viewModel::openAddGoal)
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            uiState.active.forEach { goal ->
                                GoalCard(
                                    goal = goal,
                                    currentUsername = uiState.currentUsername,
                                    expanded = uiState.expandedGoalId == goal.id,
                                    onToggleExpanded = { viewModel.toggleExpanded(goal.id) },
                                    onAddMoney = { viewModel.openContribution(goal) },
                                    onEdit = { viewModel.openEditGoal(goal) },
                                    onDelete = { goalToDelete = goal },
                                    onDeleteContribution = { contributionToDelete = goal to it },
                                )
                            }
                        }
                        if (uiState.reached.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            SectionLabel("done 🎉")
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                uiState.reached.forEach { goal ->
                                    GoalCard(
                                        goal = goal,
                                        currentUsername = uiState.currentUsername,
                                        expanded = uiState.expandedGoalId == goal.id,
                                        onToggleExpanded = { viewModel.toggleExpanded(goal.id) },
                                        onAddMoney = { viewModel.openContribution(goal) },
                                        onEdit = { viewModel.openEditGoal(goal) },
                                        onDelete = { goalToDelete = goal },
                                        onDeleteContribution = { contributionToDelete = goal to it },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(210.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 125.dp)
                .size(66.dp)
                .hardShadow(CircleShape)
                .background(Pink, CircleShape)
                .border(BorderWidth, Ink, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = viewModel::openAddGoal,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.headlineLarge)
        }
    }

    if (uiState.showGoalForm) {
        GoalFormSheet(
            editing = uiState.editingGoal,
            formError = uiState.formError,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissForms,
            onSubmit = viewModel::submitGoal,
        )
    }

    if (uiState.contributionGoal != null) {
        ContributionSheet(
            goal = uiState.contributionGoal!!,
            formError = uiState.formError,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissForms,
            onSubmit = viewModel::submitContribution,
        )
    }

    if (goalToDelete != null) {
        val target = goalToDelete!!
        NeoConfirmDialog(
            title = "delete this goal?",
            message = "\"${target.name}\" and all ${target.contributions.size} entries go away for good. " +
                "this doesn't move any real money.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.deleteGoal(target.id)
                goalToDelete = null
            },
            onDismiss = { goalToDelete = null },
        )
    }

    if (contributionToDelete != null) {
        val (goal, contribution) = contributionToDelete!!
        NeoConfirmDialog(
            title = "remove this entry?",
            message = "${formatRupees(kotlin.math.abs(contribution.amountCents))} comes off \"${goal.name}\".",
            confirmLabel = "remove",
            onConfirm = {
                viewModel.deleteContribution(goal.id, contribution.id)
                contributionToDelete = null
            },
            onDismiss = { contributionToDelete = null },
        )
    }
}

@Composable
private fun EmptyGoals(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(GoalCardShape)
            .border(BorderWidth, Ink, GoalCardShape)
            .background(YellowSoft, GoalCardShape)
            .clickableNoRipple(onAdd)
            .padding(18.dp),
    ) {
        Text("SAVE UP TOGETHER", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text("start a goal 🎯", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "a trip, a gift, a rainy-day fund. log what you each put aside and watch the bar fill up. " +
                "goals don't touch your expense balance.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GoalCard(
    goal: SavingsGoalDto,
    currentUsername: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onAddMoney: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteContribution: (GoalContributionDto) -> Unit,
) {
    val nameMask = LocalNameMask.current
    val today = remember { LocalDate.now() }
    val reached = goal.status == "reached" || goal.savedCents >= goal.targetCents
    val percent = (goalProgress(goal) * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(GoalCardShape)
            .border(BorderWidth, Ink, GoalCardShape)
            .background(MaterialTheme.colorScheme.surface, GoalCardShape)
            .clickableNoRipple(onToggleExpanded)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .border(2.dp, Ink, RoundedCornerShape(50))
                    .background(if (reached) Green else Teal, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(if (reached) "done!" else "$percent%", style = PinguCoduType.monoLabel)
            }
        }
        Spacer(Modifier.height(12.dp))
        GoalProgressBar(progress = goalProgress(goal), fillColor = if (reached) Green else Teal)
        Spacer(Modifier.height(10.dp))
        Text(
            "${formatRupees(goal.savedCents)} of ${formatRupees(goal.targetCents)}",
            style = MaterialTheme.typography.titleMedium,
        )
        val split = goal.byUser.entries
            .sortedByDescending { it.key == currentUsername }
            .joinToString(" · ") { (user, cents) -> "${nameMask.resolve(user)} ${formatRupees(cents)}" }
        if (split.isNotEmpty()) {
            Text(split, style = MaterialTheme.typography.bodySmall, color = DescriptionGrey)
        }
        if (!reached) {
            monthlyHint(goal, today)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = DescriptionGrey)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .hardShadow(RoundedCornerShape(12.dp), offsetX = 3.dp, offsetY = 3.dp)
                .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
                .background(Ink, RoundedCornerShape(12.dp))
                .clickableNoRipple(onAddMoney)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("+ add money", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            PillActionButton(
                label = if (expanded) "hide" else "history",
                background = Color.White,
                contentColor = Ink,
                onClick = onToggleExpanded,
            )
            PillActionButton(label = "edit", background = Color.White, contentColor = Ink, onClick = onEdit)
            PillActionButton(label = "delete", background = Color.White, contentColor = Ink, onClick = onDelete)
        }

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            DashedDivider()
            if (goal.contributions.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("nothing added yet", style = MaterialTheme.typography.bodySmall, color = DescriptionGrey)
            }
            goal.contributions.forEach { contribution ->
                ContributionRow(
                    contribution = contribution,
                    isMine = contribution.username == currentUsername,
                    onDelete = { onDeleteContribution(contribution) },
                )
            }
        }
    }
}

@Composable
private fun ContributionRow(contribution: GoalContributionDto, isMine: Boolean, onDelete: () -> Unit) {
    val nameMask = LocalNameMask.current
    val isWithdrawal = contribution.amountCents < 0
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${nameMask.resolve(contribution.username)} ${if (isWithdrawal) "took out" else "added"} " +
                    formatRupees(kotlin.math.abs(contribution.amountCents)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isWithdrawal) Coral else Ink,
            )
            val date = contribution.createdAt.take(10)
            val dateLabel = runCatching { LocalDate.parse(date).format(SHORT_DATE).lowercase() }.getOrDefault(date)
            Text(
                listOfNotNull(dateLabel, contribution.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = DescriptionGrey,
            )
        }
        if (isMine) {
            Text("×", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickableNoRipple(onDelete).padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun GoalFormSheet(
    editing: SavingsGoalDto?,
    formError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (SavingsGoalRequest) -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var target by remember { mutableStateOf(editing?.targetCents?.let(::centsToInput) ?: "") }
    var targetDate by remember { mutableStateOf(editing?.targetDate) }
    var showPicker by remember { mutableStateOf(false) }

    val targetCents = parseRupeesToCents(target)
    val isValid = name.isNotBlank() && targetCents != null

    NeoBottomSheet(title = if (editing == null) "new goal" else "edit goal", onDismiss = onDismiss) {
        SectionLabel("saving for")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = name,
            onValueChange = { name = it },
            placeholder = "a trip you are planning",
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("target (₹)")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = target,
            onValueChange = { target = it },
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("by when - optional")
        Spacer(Modifier.height(8.dp))
        DateField(
            label = "no deadline",
            value = targetDate,
            onClick = { showPicker = true },
            onClear = { targetDate = null },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        if (formError != null) {
            ErrorBanner(formError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (editing == null) "start saving" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(
                SavingsGoalRequest(
                    name = name.trim(),
                    // Goals no longer carry an emoji - "" clears any old one on save.
                    emoji = "",
                    targetCents = targetCents,
                    targetDate = targetDate,
                ),
            )
        }
    }

    if (showPicker) {
        NeoDatePickerDialog(
            initialDate = targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now().plusMonths(3),
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                targetDate = picked.toString()
                showPicker = false
            },
        )
    }
}

@Composable
private fun ContributionSheet(
    goal: SavingsGoalDto,
    formError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (amountCents: Long, note: String?) -> Unit,
) {
    var withdraw by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val amountCents = parseRupeesToCents(amount)
    val tooMuch = withdraw && amountCents != null && amountCents > goal.savedCents
    val isValid = amountCents != null && !tooMuch

    NeoBottomSheet(title = if (withdraw) "take out" else "add money", onDismiss = onDismiss) {
        Text(
            "${goal.name} · ${formatRupees(goal.savedCents)} of ${formatRupees(goal.targetCents)}",
            style = PinguCoduType.monoLabel,
            color = DescriptionGrey,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeoChoiceChip(label = "+ put in", selected = !withdraw, onClick = { withdraw = false })
            NeoChoiceChip(label = "− take out", selected = withdraw, onClick = { withdraw = true }, selectedColor = Pink)
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("amount (₹)")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = amount,
            onValueChange = { amount = it },
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_AMOUNTS.forEach { cents ->
                NeoChoiceChip(
                    label = formatRupees(cents),
                    selected = amountCents == cents,
                    onClick = { amount = centsToInput(cents) },
                    selectedColor = YellowSoft,
                )
            }
        }
        if (tooMuch) {
            Spacer(Modifier.height(8.dp))
            ErrorBanner("only ${formatRupees(goal.savedCents)} saved so far")
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("note - optional")
        Spacer(Modifier.height(8.dp))
        NeoField(value = note, onValueChange = { note = it.take(120) }, placeholder = "add a note", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))

        if (formError != null) {
            ErrorBanner(formError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (withdraw) "take out" else "add", enabled = isValid && !isSubmitting) {
            val cents = amountCents ?: return@SubmitButton
            onSubmit(if (withdraw) -cents else cents, note.trim().ifBlank { null })
        }
    }
}
