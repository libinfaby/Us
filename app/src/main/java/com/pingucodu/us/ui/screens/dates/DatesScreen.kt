package com.pingucodu.us.ui.screens.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.SpecialDateDto
import com.pingucodu.us.data.network.SpecialDateRequest
import com.pingucodu.us.ui.components.DateField
import com.pingucodu.us.ui.components.NeoBottomSheet
import com.pingucodu.us.ui.components.NeoChoiceChip
import com.pingucodu.us.ui.components.NeoDatePickerDialog
import com.pingucodu.us.ui.components.NeoField
import com.pingucodu.us.ui.components.SectionLabel
import com.pingucodu.us.ui.components.SubmitButton
import com.pingucodu.us.ui.components.clickableNoRipple
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.NeoConfirmDialog
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.SkeletonCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)
private val DATE_LABEL = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private val EMOJI_PRESETS = listOf("🏖️", "✈️", "🎂", "💕", "🎉", "🎬", "🎵", "🍕", "🏔️", "💍", "🐧", "⭐")

/** Big number + unit shown in a date's badge, e.g. ("37", "days") or ("3", "yrs"). */
fun dateBadge(date: SpecialDateDto): Pair<String, String> = when {
    date.kind == KIND_MILESTONE && date.daysUntilNext == 0 -> "🎉" to "today"
    date.kind == KIND_MILESTONE -> (date.years ?: 0).toString() to if (date.years == 1) "yr" else "yrs"
    date.isPast -> "✓" to "done"
    date.daysUntil == 0 -> "🎉" to "today"
    else -> (date.daysUntil ?: 0).toString() to if (date.daysUntil == 1) "day" else "days"
}

/** One-line detail under a date's title, e.g. "sat 27 sep 2026 · tomorrow". */
fun dateSubtitle(date: SpecialDateDto): String {
    val formatted = runCatching { LocalDate.parse(date.date).format(DATE_LABEL).lowercase() }.getOrDefault(date.date)
    return when {
        date.kind == KIND_MILESTONE -> {
            val next = date.daysUntilNext
            val nextYears = (date.years ?: 0) + if (next == 0) 0 else 1
            val yearsLabel = "$nextYears ${if (nextYears == 1) "year" else "years"}"
            when (next) {
                0 -> "$yearsLabel today 💕"
                1 -> "since $formatted · $yearsLabel tomorrow"
                else -> "since $formatted · $yearsLabel in $next days"
            }
        }
        date.isPast -> formatted
        date.daysUntil == 1 -> "$formatted · tomorrow"
        else -> formatted
    }
}

@Composable
fun DatesScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DatesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var toDelete by remember { mutableStateOf<SpecialDateDto?>(null) }
    var showPast by remember { mutableStateOf(false) }

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
                    .padding(horizontal = 20.dp),
            ) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                }
                Text("our dates", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "countdowns to look forward to, and days worth remembering every year.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DescriptionGrey,
                )
                Spacer(Modifier.height(20.dp))

                if (uiState.errorMessage != null) {
                    Text(uiState.errorMessage!!, color = Coral, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }

                if (uiState.isLoading && !uiState.hasLoadedOnce) {
                    repeat(3) {
                        SkeletonCard(contentHeight = 84.dp)
                        Spacer(Modifier.height(14.dp))
                    }
                } else {
                    DateSection(
                        label = "coming up",
                        emptyText = "nothing to count down to yet - add a trip, a concert, a date night.",
                        dates = uiState.upcoming,
                        onAdd = { viewModel.openAddForm(KIND_COUNTDOWN) },
                        onEdit = viewModel::openEditForm,
                        onDelete = { toDelete = it },
                    )
                    Spacer(Modifier.height(24.dp))
                    DateSection(
                        label = "our dates",
                        emptyText = "first date, first trip, the day you moved in - we'll remind you every year.",
                        dates = uiState.milestones,
                        onAdd = { viewModel.openAddForm(KIND_MILESTONE) },
                        onEdit = viewModel::openEditForm,
                        onDelete = { toDelete = it },
                    )
                    if (uiState.past.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            if (showPast) "hide past (${uiState.past.size}) ▴" else "show past (${uiState.past.size}) ▾",
                            style = PinguCoduType.monoLabel,
                            color = DescriptionGrey,
                            modifier = Modifier.clickableNoRipple { showPast = !showPast },
                        )
                        if (showPast) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                uiState.past.forEach { date ->
                                    DateCard(
                                        date = date,
                                        onClick = { viewModel.openEditForm(date) },
                                        onLongClick = { toDelete = date },
                                        modifier = Modifier.alpha(0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp)
                .size(66.dp)
                .hardShadow(CircleShape)
                .background(Pink, CircleShape)
                .border(BorderWidth, Ink, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.openAddForm() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.headlineLarge)
        }
    }

    if (uiState.showForm) {
        DateFormSheet(
            editing = uiState.editing,
            initialKind = uiState.formKind,
            formError = uiState.formError,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissForm,
            onSubmit = viewModel::submit,
        )
    }

    if (toDelete != null) {
        val target = toDelete!!
        NeoConfirmDialog(
            title = "delete this?",
            message = "\"${target.title}\" goes away for good, along with its reminders.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.delete(target.id)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun DateSection(
    label: String,
    emptyText: String,
    dates: List<SpecialDateDto>,
    onAdd: () -> Unit,
    onEdit: (SpecialDateDto) -> Unit,
    onDelete: (SpecialDateDto) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(label)
        Spacer(Modifier.weight(1f))
        Text("+ add", style = PinguCoduType.monoLabel, color = Ink, modifier = Modifier.clickableNoRipple(onAdd))
    }
    Spacer(Modifier.height(10.dp))
    if (dates.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodySmall, color = DescriptionGrey)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            dates.forEach { date ->
                DateCard(date = date, onClick = { onEdit(date) }, onLongClick = { onDelete(date) })
            }
        }
    }
}

@Composable
fun DateCard(
    date: SpecialDateDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val (number, unit) = dateBadge(date)
    val badgeColor = if (date.kind == KIND_MILESTONE) Pink else Teal
    Row(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 64.dp)
                .border(2.5.dp, Ink, RoundedCornerShape(12.dp))
                .background(badgeColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(number, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text(unit, style = PinguCoduType.monoLabel)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(date.emoji, date.title).joinToString(" "),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(dateSubtitle(date), style = MaterialTheme.typography.bodySmall, color = DescriptionGrey)
        }
    }
}

@Composable
private fun DateFormSheet(
    editing: SpecialDateDto?,
    initialKind: String,
    formError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (SpecialDateRequest) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var kind by remember { mutableStateOf(editing?.kind ?: initialKind) }
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var emoji by remember { mutableStateOf(editing?.emoji) }
    var date by remember { mutableStateOf(editing?.date) }
    var showPicker by remember { mutableStateOf(false) }

    val pickedDate = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    // A countdown that already went by keeps its date when only renamed - the server allows that too.
    val dateUnchanged = editing != null && editing.date == date && editing.kind == kind
    val dateProblem = when {
        pickedDate == null -> null
        dateUnchanged -> null
        kind == KIND_COUNTDOWN && pickedDate.isBefore(today) -> "a countdown needs a date today or later"
        kind == KIND_MILESTONE && pickedDate.isAfter(today) -> "a date to remember needs to be today or earlier"
        else -> null
    }
    val isValid = title.isNotBlank() && pickedDate != null && dateProblem == null

    NeoBottomSheet(title = if (editing == null) "new date" else "edit date", onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeoChoiceChip(label = "⏳ countdown", selected = kind == KIND_COUNTDOWN, onClick = { kind = KIND_COUNTDOWN })
            NeoChoiceChip(
                label = "💕 remember yearly",
                selected = kind == KIND_MILESTONE,
                onClick = { kind = KIND_MILESTONE },
                selectedColor = Pink,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (kind == KIND_COUNTDOWN) {
                "something coming up. we'll both get a push the day before and on the day."
            } else {
                "a day that already happened. we'll both get a push on it every year."
            },
            style = MaterialTheme.typography.bodySmall,
            color = DescriptionGrey,
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("what")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = title,
            onValueChange = { title = it },
            placeholder = if (kind == KIND_COUNTDOWN) "goa trip" else "our first date",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("emoji - optional")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EMOJI_PRESETS.forEach { preset ->
                val selected = emoji == preset
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .border(2.dp, Ink, RoundedCornerShape(12.dp))
                        .background(if (selected) YellowSoft else Color.White, RoundedCornerShape(12.dp))
                        .clickableNoRipple { emoji = if (selected) null else preset },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(preset, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("when")
        Spacer(Modifier.height(8.dp))
        DateField(
            label = "pick a date",
            value = date,
            onClick = { showPicker = true },
            onClear = { date = null },
            modifier = Modifier.fillMaxWidth(),
        )
        if (dateProblem != null) {
            Spacer(Modifier.height(8.dp))
            Text(dateProblem, color = Coral, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))

        if (formError != null) {
            Text(formError, color = Coral, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (editing == null) "save it" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(SpecialDateRequest(kind = kind, title = title.trim(), emoji = emoji ?: "", date = date))
        }
    }

    if (showPicker) {
        NeoDatePickerDialog(
            initialDate = pickedDate ?: today,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                date = picked.toString()
                showPicker = false
            },
        )
    }
}
