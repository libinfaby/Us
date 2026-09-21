package com.pingucodu.us.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.NeoConfirmDialog
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BalanceCardShape = RoundedCornerShape(18.dp)

@Composable
fun MoneyScreen(modifier: Modifier = Modifier, viewModel: MoneyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var expenseToDelete by remember { mutableStateOf<ExpenseDto?>(null) }
    var expenseToTogglePaid by remember { mutableStateOf<ExpenseDto?>(null) }
    var confirmSettleAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Box(modifier = modifier.fillMaxSize().background(PinkTint)) {
        Column(modifier = Modifier.fillMaxSize()) {
            BalanceHeaderCard(
                net = uiState.net,
                currentUsername = uiState.currentUsername,
                onSettleAll = { confirmSettleAll = true },
            )

            StatusFilterPills(
                selected = uiState.statusFilter,
                onSelect = viewModel::setStatusFilter,
            )
            Spacer(Modifier.height(10.dp))

            HangoutFilterRow(
                hangouts = uiState.hangouts,
                selectedHangoutId = uiState.hangoutFilter,
                showNoHangoutOnly = uiState.showNoHangoutOnly,
                onSelect = viewModel::setHangoutFilter,
                onSelectNoHangout = viewModel::setNoHangoutFilter,
            )
            Spacer(Modifier.height(10.dp))

            if (uiState.errorMessage != null) {
                Text(
                    uiState.errorMessage!!,
                    color = Coral,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            val displayedExpenses = if (uiState.showNoHangoutOnly) {
                uiState.expenses.filter { it.hangoutId == null }
            } else {
                uiState.expenses
            }

            when {
                uiState.isLoading && uiState.expenses.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Ink)
                    }
                }
                displayedExpenses.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("no expenses yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 210.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(displayedExpenses, key = { it.id }) { expense ->
                            ExpenseCard(
                                expense = expense,
                                hangouts = uiState.hangouts,
                                currentUsername = uiState.currentUsername,
                                onMarkPaid = { expenseToTogglePaid = expense },
                                onEdit = { viewModel.openEditDialog(expense) },
                                onLongPress = { expenseToDelete = expense },
                            )
                        }
                    }
                }
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
                    onClick = { viewModel.openAddDialog() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.headlineLarge)
        }
    }

    if (uiState.showAddDialog) {
        ExpenseFormDialog(
            expense = uiState.editingExpense,
            hangouts = uiState.hangouts,
            currentUsername = uiState.currentUsername ?: "pingu",
            dialogError = uiState.dialogError,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissDialog,
            onCreateHangout = { name -> viewModel.createHangoutAndReturn(name) },
            onSubmit = viewModel::submitExpense,
        )
    }

    if (expenseToDelete != null) {
        val target = expenseToDelete!!
        NeoConfirmDialog(
            title = "delete this?",
            message = "\"${target.title}\" and its split go away for good. this one you can't take back.",
            confirmLabel = "delete",
            badgeLabel = "no undo",
            accentColor = Pink,
            onConfirm = {
                viewModel.deleteExpense(target.id)
                expenseToDelete = null
            },
            onDismiss = { expenseToDelete = null },
        )
    }

    if (expenseToTogglePaid != null) {
        val target = expenseToTogglePaid!!
        val isPaid = target.status != "open"
        NeoConfirmDialog(
            title = if (isPaid) "bring this back?" else "mark as paid?",
            message = if (isPaid) {
                "\"${target.title}\" will be marked unpaid again."
            } else {
                "mark \"${target.title}\" as paid?"
            },
            confirmLabel = if (isPaid) "bring it back" else "mark paid",
            badgeLabel = "can undo",
            onConfirm = {
                viewModel.settleExpense(target.id)
                expenseToTogglePaid = null
            },
            onDismiss = { expenseToTogglePaid = null },
        )
    }

    if (confirmSettleAll) {
        NeoConfirmDialog(
            title = "settle all?",
            message = "mark all open expenses" + (uiState.hangoutFilter?.let { " in this hangout" } ?: "") + " as settled?",
            confirmLabel = "settle all",
            badgeLabel = "can undo",
            onConfirm = {
                viewModel.settleAll()
                confirmSettleAll = false
            },
            onDismiss = { confirmSettleAll = false },
        )
    }
}

@Composable
private fun BalanceHeaderCard(
    net: Map<String, Long>,
    currentUsername: String?,
    onSettleAll: () -> Unit,
) {
    val myNet = currentUsername?.let { net[it] } ?: 0
    val other = net.keys.firstOrNull { it != currentUsername }

    val headline = when {
        myNet == 0L || other == null -> "all settled up"
        myNet > 0 -> "$other owes you"
        else -> "you owe $other"
    }
    val amount = if (myNet == 0L || other == null) null else formatCents(kotlin.math.abs(myNet))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .hardShadow(BalanceCardShape)
            .border(BorderWidth, Ink, BalanceCardShape)
            .background(Pink, BalanceCardShape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("OPEN BALANCE", style = MaterialTheme.typography.labelMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(headline, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (amount != null) {
                Text(amount, style = MaterialTheme.typography.displayLarge, color = Ink)
            }
        }
        if (myNet != 0L) {
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onSettleAll,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            ) {
                Text("settle all", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatusFilterPills(selected: ExpenseStatusFilter, onSelect: (ExpenseStatusFilter) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExpenseStatusFilter.entries.forEach { filter ->
            Pill(label = filter.label, selected = filter == selected, modifier = Modifier.weight(1f), onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun HangoutFilterRow(
    hangouts: List<HangoutDto>,
    selectedHangoutId: String?,
    showNoHangoutOnly: Boolean,
    onSelect: (String?) -> Unit,
    onSelectNoHangout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Pill(
            label = "all hangouts",
            selected = selectedHangoutId == null && !showNoHangoutOnly,
            dot = { DotIndicator(color = Color.White, shape = CircleShape) },
            onClick = { onSelect(null) },
        )
        hangouts.forEachIndexed { index, h ->
            Pill(
                label = h.name,
                selected = selectedHangoutId == h.id,
                dot = { DotIndicator(color = hangoutColor(index), shape = RoundedCornerShape(3.dp)) },
                onClick = { onSelect(h.id) },
            )
        }
        Pill(
            label = "no hangout",
            selected = showNoHangoutOnly,
            dot = { DotIndicator(color = Color.Transparent, shape = CircleShape) },
            onClick = onSelectNoHangout,
        )
    }
}

@Composable
private fun DotIndicator(color: Color, shape: Shape) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .border(2.dp, Ink, shape)
            .background(color, shape),
    )
}

@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dot: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .let { if (selected) it.hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp) else it }
            .border(BorderWidth, Ink, shape)
            .background(if (selected) Pink else Color.White, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot?.invoke()
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
private fun ExpenseCard(
    expense: ExpenseDto,
    hangouts: List<HangoutDto>,
    currentUsername: String?,
    onMarkPaid: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPaid = expense.status != "open"
    val hangoutIndex = expense.hangoutId?.let { hid -> hangouts.indexOfFirst { it.id == hid } } ?: -1
    val stripeColor = if (isPaid) Teal else Pink
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(shape)
            .border(BorderWidth, Ink, shape)
            .background(Color.White, shape)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            ),
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(stripeColor))
        Box(Modifier.fillMaxWidth().height(BorderWidth).background(Ink))

        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.title, style = MaterialTheme.typography.titleLarge)
                    if (!expense.subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(expense.subtitle, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
                    }
                }
                Text(formatCents(expense.amountCents), style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(11.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip(formatShortDate(expense.expenseDate))
                val placeOrCategory = expense.location ?: expense.category
                if (!placeOrCategory.isNullOrBlank()) TagChip(placeOrCategory)
                TagChip(
                    if (expense.isRecurring) (expense.cadence ?: "recurring") else "one-off",
                    background = if (expense.isRecurring) Pink else Color.White,
                )
                HangoutTagChip(
                    label = if (hangoutIndex >= 0) hangouts[hangoutIndex].name else "no hangout",
                    color = if (hangoutIndex >= 0) hangoutColor(hangoutIndex) else Color.Transparent,
                )
            }

            DashedDivider(modifier = Modifier.padding(top = 12.dp, bottom = 11.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    formatSplitSummary(expense),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                PillActionButton(
                    label = if (isPaid) "settled" else "mark paid",
                    background = if (isPaid) Teal else Ink,
                    contentColor = if (isPaid) Ink else Color.White,
                    onClick = onMarkPaid,
                )
                PillActionButton(label = "edit", background = Color.White, contentColor = Ink, onClick = onEdit)
            }
        }
    }
}

private val ChipTint = Color(0xFFFFD1E5)

@Composable
private fun TagChip(text: String, background: Color = ChipTint) {
    Box(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(6.dp))
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Text(text, style = PinguCoduType.monoLabel, color = Ink)
    }
}

@Composable
private fun HangoutTagChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(label, style = PinguCoduType.monoLabel, color = Ink)
    }
}

@Composable
private fun PillActionButton(
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(background, RoundedCornerShape(50))
            .combinedClickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = contentColor)
    }
}

private val HANGOUT_PALETTE = listOf(Pink, Teal, YellowSoft)

private fun hangoutColor(index: Int): Color = HANGOUT_PALETTE[index % HANGOUT_PALETTE.size]

private fun formatCents(cents: Long): String = "₹%.2f".format(cents / 100.0)

private fun formatShortDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)).lowercase()
} catch (e: Exception) {
    isoDate
}

private fun formatSplitSummary(expense: ExpenseDto): String {
    val paidBy = expense.paidBy
    return if (expense.splitType == "custom") {
        val pingu = expense.split["pingu"] ?: 0
        val codu = expense.split["codu"] ?: 0
        "$paidBy paid · pingu ${formatCents(pingu)} / codu ${formatCents(codu)}"
    } else {
        val half = expense.split[paidBy] ?: (expense.amountCents / 2)
        "$paidBy paid · split 50/50 · ${formatCents(half)} each"
    }
}
