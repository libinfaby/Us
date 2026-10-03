package com.pingucodu.us.ui.screens.hangouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.data.network.HangoutExpenseDto
import com.pingucodu.us.data.network.HangoutMemoryDto
import com.pingucodu.us.data.network.HangoutStashItemDto
import com.pingucodu.us.ui.components.ChevronArrow
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
import com.pingucodu.us.ui.screens.stash.relativeTime
import com.pingucodu.us.ui.screens.stash.toggleLabel
import com.pingucodu.us.ui.screens.stash.typeBadgeColor
import com.pingucodu.us.ui.screens.stash.typeLabel
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.NeoConfirmDialog
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.SkeletonHangoutCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.dashedBorder
import com.pingucodu.us.ui.theme.hardShadow
import com.pingucodu.us.ui.util.LocalNameMask
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val HANGOUT_PALETTE = listOf(Pink, Teal, YellowSoft)

/** Trips, meets and nights out: each hangout groups its memories, plus the stash items and
 * expenses linked to it from the other tabs. */
@Composable
fun HangoutsScreen(modifier: Modifier = Modifier, viewModel: HangoutsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var hangoutToDelete by remember { mutableStateOf<HangoutDto?>(null) }
    var memoryToDelete by remember { mutableStateOf<Pair<HangoutDto, HangoutMemoryDto>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading && uiState.hasLoadedOnce,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(4.dp))
            if (uiState.errorMessage != null) {
                ErrorBanner(
                    uiState.errorMessage!!,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            HangoutsSection(
                hangouts = uiState.hangouts,
                currentUsername = uiState.currentUsername,
                isLoading = uiState.isLoading && !uiState.hasLoadedOnce,
                onAddHangout = viewModel::openAddHangoutDialog,
                onEditHangout = viewModel::openEditHangoutDialog,
                onAddMemory = viewModel::openAddMemoryDialog,
                onEditMemory = viewModel::openEditMemoryDialog,
                onDeleteMemory = { hangout, memory -> memoryToDelete = hangout to memory },
                onLongPressHangout = { hangoutToDelete = it },
            )
        }
    }

    if (uiState.showHangoutDialog) {
        HangoutFormDialog(
            hangout = uiState.editingHangout,
            dialogError = uiState.hangoutDialogError,
            isSubmitting = uiState.isHangoutSubmitting,
            onDismiss = viewModel::dismissHangoutDialog,
            onSubmit = viewModel::submitHangout,
        )
    }

    if (uiState.memoryDialogHangout != null) {
        MemoryFormDialog(
            hangout = uiState.memoryDialogHangout!!,
            memory = uiState.editingMemory,
            dialogError = uiState.memoryDialogError,
            isSubmitting = uiState.isMemorySubmitting,
            onDismiss = viewModel::dismissMemoryDialog,
            onSubmit = viewModel::submitMemory,
        )
    }

    if (hangoutToDelete != null) {
        val target = hangoutToDelete!!
        NeoConfirmDialog(
            title = "delete this?",
            message = "\"${target.name}\" and its memories go away for good. its expenses keep their date and amount but lose this hangout.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.deleteHangout(target.id)
                hangoutToDelete = null
            },
            onDismiss = { hangoutToDelete = null },
        )
    }

    if (memoryToDelete != null) {
        val (hangout, memory) = memoryToDelete!!
        NeoConfirmDialog(
            title = "delete this memory?",
            message = "\"${memory.text}\" goes away for good.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.deleteMemory(hangout.id, memory.id)
                memoryToDelete = null
            },
            onDismiss = { memoryToDelete = null },
        )
    }
}

private fun formatHangoutDate(date: String): String? = runCatching { LocalDate.parse(date) }.getOrNull()?.let {
    "${it.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).lowercase()} ${it.dayOfMonth}"
}

private fun formatCents(cents: Long): String = "₹%.2f".format(cents / 100.0)

private fun hangoutSubtitle(hangout: HangoutDto): String {
    val start = hangout.startDate?.let(::formatHangoutDate)
    val end = hangout.endDate?.let(::formatHangoutDate)
    val dateLabel = when {
        start != null && end != null && start != end -> "$start – $end"
        start != null -> start
        end != null -> end
        else -> null
    }
    return listOfNotNull(dateLabel, formatCents(hangout.totalCents)).joinToString(" · ")
}

private fun memoryCountLabel(count: Int): String = if (count == 1) "1 note" else "$count notes"

@Composable
private fun HangoutsSection(
    hangouts: List<HangoutDto>,
    currentUsername: String?,
    isLoading: Boolean,
    onAddHangout: () -> Unit,
    onEditHangout: (HangoutDto) -> Unit,
    onAddMemory: (HangoutDto) -> Unit,
    onEditMemory: (HangoutDto, HangoutMemoryDto) -> Unit,
    onDeleteMemory: (HangoutDto, HangoutMemoryDto) -> Unit,
    onLongPressHangout: (HangoutDto) -> Unit,
) {
    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(3) { SkeletonHangoutCard() }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 210.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            NewHangoutButton(onClick = onAddHangout)
        }
        itemsIndexed(hangouts, key = { _, h -> h.id }) { index, hangout ->
            HangoutCard(
                hangout = hangout,
                color = HANGOUT_PALETTE[index % HANGOUT_PALETTE.size],
                currentUsername = currentUsername,
                onEdit = { onEditHangout(hangout) },
                onAddMemory = { onAddMemory(hangout) },
                onEditMemory = { onEditMemory(hangout, it) },
                onDeleteMemory = { onDeleteMemory(hangout, it) },
                onLongPress = { onLongPressHangout(hangout) },
            )
        }
        if (hangouts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                    Text("no hangouts yet", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NewHangoutButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(2.dp, Ink, shape)
            .background(Color.White, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ new hangout", style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun HangoutCard(
    hangout: HangoutDto,
    color: Color,
    currentUsername: String?,
    onEdit: () -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (HangoutMemoryDto) -> Unit,
    onDeleteMemory: (HangoutMemoryDto) -> Unit,
    onLongPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(shape)
            .border(3.dp, Ink, shape)
            .background(Color.White, shape)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().background(color).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hangout.name, style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CountBadge(memoryCountLabel(hangout.memories.size))
                Spacer(Modifier.width(6.dp))
                PillActionButton(label = "edit", background = Color.White, contentColor = Ink, onClick = onEdit)
            }
            Spacer(Modifier.height(4.dp))
            Text(hangoutSubtitle(hangout), style = PinguCoduType.monoLabel, color = Ink)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Ink))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hangout.memories.isEmpty()) {
                Text(
                    "nothing written down yet. what did you like about this one?",
                    style = MaterialTheme.typography.bodySmall,
                    color = DescriptionGrey,
                )
            } else {
                hangout.memories.forEach { memory ->
                    MemoryRow(
                        memory = memory,
                        isPartnerMemory = currentUsername != null && memory.author != currentUsername,
                        onEdit = { onEditMemory(memory) },
                        onDelete = { onDeleteMemory(memory) },
                    )
                }
            }
            AddMemoryButton(onClick = onAddMemory)
            if (hangout.stashItems.isNotEmpty()) {
                LinkedSection(label = "stash", count = hangout.stashItems.size, key = "${hangout.id}-stash") {
                    hangout.stashItems.forEach { LinkedStashRow(it) }
                }
            }
            if (hangout.expenses.isNotEmpty()) {
                LinkedSection(label = "expenses", count = hangout.expenses.size, key = "${hangout.id}-expenses") {
                    hangout.expenses.forEach { LinkedExpenseRow(it) }
                }
            }
        }
    }
}

/** A collapsible list on a hangout card (its stash items, its expenses) - closed until tapped,
 * so a long trip's receipts don't bury the memories. */
@Composable
private fun LinkedSection(label: String, count: Int, key: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DashedDivider()
        Row(
            modifier = Modifier.fillMaxWidth().clickableNoRipple { expanded = !expanded }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$label · $count".uppercase(), style = PinguCoduType.monoLabel, color = Ink, modifier = Modifier.weight(1f))
            ChevronArrow(pointingUp = expanded)
        }
        if (expanded) content()
    }
}

@Composable
private fun LinkedStashRow(item: HangoutStashItemDto) {
    val done = toggleLabel(item.type) != null && item.status == "done"
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (done) 0.55f else 1f)
            .border(2.dp, Ink, shape)
            .background(Color.White, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .border(2.dp, Ink, RoundedCornerShape(7.dp))
                .background(typeBadgeColor(item.type), RoundedCornerShape(7.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(typeLabel(item.type), style = PinguCoduType.monoLabel)
        }
        Spacer(Modifier.width(10.dp))
        Text(item.title, style = MaterialTheme.typography.bodySmall, color = Ink, modifier = Modifier.weight(1f))
        toggleLabel(item.type)?.takeIf { done }?.let { (doneLabel, _) ->
            Spacer(Modifier.width(8.dp))
            Text(doneLabel, style = PinguCoduType.monoLabel, color = DescriptionGrey)
        }
    }
}

@Composable
private fun LinkedExpenseRow(expense: HangoutExpenseDto) {
    val settled = expense.status == "settled"
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (settled) 0.55f else 1f)
            .border(2.dp, Ink, shape)
            .background(Color.White, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(expense.title, style = MaterialTheme.typography.bodySmall, color = Ink)
            Spacer(Modifier.height(2.dp))
            val details = listOfNotNull(
                formatHangoutDate(expense.expenseDate),
                "paid by ${LocalNameMask.current.resolve(expense.paidBy)}",
                "settled".takeIf { settled },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
        }
        Spacer(Modifier.width(10.dp))
        Text(formatCents(expense.amountCents), style = PinguCoduType.monoLabel, color = Ink)
    }
}

@Composable
private fun CountBadge(label: String) {
    Box(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(Color.White, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(label, style = PinguCoduType.monoLabel, color = Ink)
    }
}

@Composable
private fun MemoryRow(memory: HangoutMemoryDto, isPartnerMemory: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Ink, shape)
            .background(if (isPartnerMemory) PinkTint else Color.White, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(memory.text, style = MaterialTheme.typography.bodySmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "- ${LocalNameMask.current.resolve(memory.author)} · ${relativeTime(memory.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = DescriptionGrey,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillActionButton(label = "edit", background = Color.White, contentColor = Ink, onClick = onEdit)
                if (!isPartnerMemory) {
                    PillActionButton(label = "delete", background = Color.White, contentColor = Ink, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun AddMemoryButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(3.dp, Ink, shape)
            .background(Pink, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ add a memory", style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

// ---------- add/edit hangout ----------

@Composable
private fun HangoutFormDialog(
    hangout: HangoutDto?,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, startDate: String?, endDate: String?, countdownDate: String?) -> Unit,
) {
    var name by remember { mutableStateOf(hangout?.name ?: "") }
    var addToCountdown by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(hangout?.startDate) }
    var endDate by remember { mutableStateOf(hangout?.endDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank()
    // Only a new hangout that hasn't started yet can become a countdown - countdowns need today or later.
    val countdownDate = (startDate ?: endDate)
        ?.takeIf { hangout == null }
        ?.takeIf { date -> runCatching { !LocalDate.parse(date).isBefore(LocalDate.now()) }.getOrDefault(false) }

    NeoBottomSheet(title = if (hangout == null) "new hangout" else "edit hangout", onDismiss = onDismiss) {
        Text(
            "a trip, a one-day meet, a movie night - anything you'll want to group expenses and memories under.",
            style = MaterialTheme.typography.bodySmall,
            color = DescriptionGrey,
        )
        Spacer(Modifier.height(18.dp))

        SectionLabel("name")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = name,
            onValueChange = { name = it },
            placeholder = "new hangout",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("dates - optional, leave them if it's a one-day thing")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(label = "from", value = startDate, onClick = { showStartPicker = true }, onClear = { startDate = null }, modifier = Modifier.weight(1f))
            DateField(label = "to", value = endDate, onClick = { showEndPicker = true }, onClear = { endDate = null }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        if (countdownDate != null) {
            SectionLabel("add to countdown?")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeoChoiceChip(label = "yes", selected = addToCountdown, onClick = { addToCountdown = true })
                NeoChoiceChip(label = "no", selected = !addToCountdown, onClick = { addToCountdown = false })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "shows up in our dates, and we'll both get a push the day before and on the day.",
                style = MaterialTheme.typography.bodySmall,
                color = DescriptionGrey,
            )
            Spacer(Modifier.height(20.dp))
        }

        if (dialogError != null) {
            ErrorBanner(dialogError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (hangout == null) "create hangout" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(name.trim(), startDate, endDate, countdownDate?.takeIf { addToCountdown })
        }
    }

    if (showStartPicker) {
        NeoDatePickerDialog(
            initialDate = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.now()),
            onDismiss = { showStartPicker = false },
            onConfirm = { picked -> startDate = picked.toString(); showStartPicker = false },
        )
    }
    if (showEndPicker) {
        NeoDatePickerDialog(
            initialDate = runCatching { LocalDate.parse(endDate) }.getOrDefault(LocalDate.now()),
            onDismiss = { showEndPicker = false },
            onConfirm = { picked -> endDate = picked.toString(); showEndPicker = false },
        )
    }
}


// ---------- add/edit memory ----------

@Composable
private fun MemoryFormDialog(
    hangout: HangoutDto,
    memory: HangoutMemoryDto?,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(memory?.text ?: "") }
    val isValid = text.isNotBlank()

    NeoBottomSheet(title = if (memory == null) "add a memory" else "edit memory", onDismiss = onDismiss) {
        Text(hangout.name, style = PinguCoduType.monoLabel, color = DescriptionGrey)
        Spacer(Modifier.height(16.dp))
        NeoField(value = text, onValueChange = { text = it }, placeholder = "what happened?", minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))

        if (dialogError != null) {
            ErrorBanner(dialogError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (memory == null) "add memory" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(text.trim())
        }
    }
}
