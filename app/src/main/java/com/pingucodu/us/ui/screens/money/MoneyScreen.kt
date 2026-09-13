package com.pingucodu.us.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.Green
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Orange
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.Purple
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.Yellow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MoneyScreen(modifier: Modifier = Modifier, viewModel: MoneyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var expenseToDelete by remember { mutableStateOf<ExpenseDto?>(null) }
    var confirmSettleAll by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = Pink,
                contentColor = Ink,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "add expense")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            BalanceHeaderCard(
                net = uiState.net,
                currentUsername = uiState.currentUsername,
                showSettleAll = uiState.statusFilter == ExpenseStatusFilter.OPEN,
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
                onSelect = viewModel::setHangoutFilter,
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

            when {
                uiState.isLoading && uiState.expenses.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Ink)
                    }
                }
                uiState.expenses.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("no expenses yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.expenses, key = { it.id }) { expense ->
                            ExpenseCard(
                                expense = expense,
                                hangouts = uiState.hangouts,
                                currentUsername = uiState.currentUsername,
                                onMarkPaid = { viewModel.settleExpense(expense.id) },
                                onEdit = { viewModel.openEditDialog(expense) },
                                onLongPress = { expenseToDelete = expense },
                            )
                        }
                    }
                }
            }
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
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("delete expense?") },
            text = { Text("delete \"${expenseToDelete?.title}\"? this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expenseToDelete!!.id)
                    expenseToDelete = null
                }) { Text("delete", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { expenseToDelete = null }) { Text("cancel") } },
        )
    }

    if (confirmSettleAll) {
        AlertDialog(
            onDismissRequest = { confirmSettleAll = false },
            title = { Text("settle all?") },
            text = { Text("mark all open expenses" + (uiState.hangoutFilter?.let { " in this hangout" } ?: "") + " as settled?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.settleAll()
                    confirmSettleAll = false
                }) { Text("settle all") }
            },
            dismissButton = { TextButton(onClick = { confirmSettleAll = false }) { Text("cancel") } },
        )
    }
}

@Composable
private fun BalanceHeaderCard(
    net: Map<String, Long>,
    currentUsername: String?,
    showSettleAll: Boolean,
    onSettleAll: () -> Unit,
) {
    val myNet = currentUsername?.let { net[it] } ?: 0
    val other = net.keys.firstOrNull { it != currentUsername }

    val text = when {
        myNet == 0L || other == null -> "all settled up"
        myNet > 0 -> "$other owes you ${formatCents(myNet)}"
        else -> "you owe $other ${formatCents(-myNet)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .border(2.5.dp, Ink, RoundedCornerShape(16.dp))
            .background(Pink, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("OPEN BALANCE", style = MaterialTheme.typography.labelMedium)
            Text(text, style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        if (showSettleAll && myNet != 0L) {
            Button(
                onClick = onSettleAll,
                modifier = Modifier.border(2.dp, Ink, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Pink),
            ) {
                Text("settle all")
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
            Pill(label = filter.label, selected = filter == selected, onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun HangoutFilterRow(
    hangouts: List<HangoutDto>,
    selectedHangoutId: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(label = "all hangouts", selected = selectedHangoutId == null, onClick = { onSelect(null) })
        hangouts.forEach { h ->
            Pill(
                label = h.name,
                selected = selectedHangoutId == h.id,
                accentColor = hangoutColor(h.id),
                onClick = { onSelect(h.id) },
            )
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, accentColor: Color? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val background = when {
        selected && accentColor != null -> accentColor
        selected -> Pink
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(background, RoundedCornerShape(50))
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.5.dp, Ink, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            )
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(expense.title, style = MaterialTheme.typography.titleMedium)
            Text(formatCents(expense.amountCents), style = MaterialTheme.typography.titleMedium)
        }
        if (!expense.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(expense.subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TagChip(formatShortDate(expense.expenseDate))
            val placeOrCategory = expense.location ?: expense.category
            if (!placeOrCategory.isNullOrBlank()) TagChip(placeOrCategory)
            TagChip(if (expense.isRecurring) (expense.cadence ?: "recurring") else "one-off")
            expense.hangoutId?.let { hid ->
                val name = hangouts.firstOrNull { it.id == hid }?.name
                if (name != null) TagChip(name, background = hangoutColor(hid))
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Ink.copy(alpha = 0.15f))
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatSplitSummary(expense),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (expense.status == "open") {
                    OutlinedPillButton(label = "mark paid", onClick = onMarkPaid)
                }
                OutlinedPillButton(label = "edit", onClick = onEdit)
            }
        }
    }
}

@Composable
private fun TagChip(text: String, background: Color = MaterialTheme.colorScheme.background) {
    Box(
        modifier = Modifier
            .border(1.5.dp, Ink, RoundedCornerShape(50))
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OutlinedPillButton(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .border(1.5.dp, Ink, RoundedCornerShape(50))
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private val HANGOUT_PALETTE = listOf(Purple, Teal, Orange, Green, Yellow, Coral)

private fun hangoutColor(hangoutId: String): Color =
    HANGOUT_PALETTE[(hangoutId.hashCode().and(Int.MAX_VALUE)) % HANGOUT_PALETTE.size]

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
