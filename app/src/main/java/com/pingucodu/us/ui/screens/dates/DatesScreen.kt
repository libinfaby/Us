package com.pingucodu.us.ui.screens.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.SpecialDateDto
import com.pingucodu.us.data.network.SpecialDateRequest
import com.pingucodu.us.ui.components.DateField
import com.pingucodu.us.ui.components.ErrorBanner
import com.pingucodu.us.ui.components.NeoBottomSheet
import com.pingucodu.us.ui.components.NeoChoiceChip
import com.pingucodu.us.ui.components.NeoDatePickerDialog
import com.pingucodu.us.ui.components.NeoField
import com.pingucodu.us.ui.components.SectionLabel
import com.pingucodu.us.ui.components.SubmitButton
import com.pingucodu.us.ui.components.clickableNoRipple
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.NeoConfirmDialog
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.SkeletonCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.hardShadow
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)
private val DATE_LABEL = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
/**
 * How long ago a milestone was (e.g. 1y 4m 12d) and when its next monthly anniversary falls,
 * or null when [SpecialDateDto.date] doesn't parse or is still ahead.
 */
data class MilestoneProgress(val years: Int, val months: Int, val days: Int, val nextMonths: Int, val daysUntilNext: Int)

fun milestoneProgress(date: SpecialDateDto, today: LocalDate = LocalDate.now()): MilestoneProgress? {
    val start = runCatching { LocalDate.parse(date.date) }.getOrNull() ?: return null
    if (start.isAfter(today)) return null
    val elapsed = Period.between(start, today)
    // plusMonths clamps to the month's end (31 jan + 1 month = 28 feb), so step until we land on or after today.
    var nextMonths = elapsed.toTotalMonths().toInt()
    while (start.plusMonths(nextMonths.toLong()).isBefore(today)) nextMonths++
    val daysUntilNext = ChronoUnit.DAYS.between(today, start.plusMonths(nextMonths.toLong())).toInt()
    return MilestoneProgress(elapsed.years, elapsed.months, elapsed.days, nextMonths, daysUntilNext)
}

/** Soonest monthly anniversary first. */
fun List<SpecialDateDto>.sortedByNextMonthlyAnniversary(): List<SpecialDateDto> =
    sortedBy { milestoneProgress(it)?.daysUntilNext ?: Int.MAX_VALUE }

/** "1 year 5 months", "2 years", "7 months". */
private fun monthsLabel(totalMonths: Int): String {
    val years = totalMonths / 12
    val months = totalMonths % 12
    return listOfNotNull(
        years.takeIf { it > 0 }?.let { "$it ${if (it == 1) "year" else "years"}" },
        months.takeIf { it > 0 }?.let { "$it ${if (it == 1) "month" else "months"}" },
    ).joinToString(" ")
}

/** Big number + unit shown in a date's badge, e.g. ("37", "days") or ("2m 10d", "ago"); the unit is empty for "today". */
fun dateBadge(date: SpecialDateDto): Pair<String, String> {
    if (date.kind == KIND_MILESTONE) {
        val progress = milestoneProgress(date)
            ?: return (date.years ?: 0).toString() to if (date.years == 1) "yr" else "yrs"
        return when {
            progress.daysUntilNext == 0 -> "today" to ""
            else -> listOfNotNull(
                progress.years.takeIf { it > 0 }?.let { "${it}y" },
                progress.months.takeIf { it > 0 }?.let { "${it}m" },
                progress.days.takeIf { it > 0 }?.let { "${it}d" },
            ).joinToString(" ") to "ago"
        }
    }
    return when {
        date.isPast -> "✓" to "done"
        date.daysUntil == 0 -> "today" to ""
        else -> (date.daysUntil ?: 0).toString() to if (date.daysUntil == 1) "day" else "days"
    }
}

/** One-line detail under a date's title, e.g. "sat 27 sep 2026 · tomorrow" or "since … · 1 year 5 months in 12 days". */
fun dateSubtitle(date: SpecialDateDto): String {
    val formatted = runCatching { LocalDate.parse(date.date).format(DATE_LABEL).lowercase() }.getOrDefault(date.date)
    return when {
        date.kind == KIND_MILESTONE -> {
            val progress = milestoneProgress(date) ?: return formatted
            val label = monthsLabel(progress.nextMonths)
            when {
                progress.nextMonths == 0 -> "$formatted · today"
                progress.daysUntilNext == 0 -> "$label today"
                progress.daysUntilNext == 1 -> "since $formatted · $label tomorrow"
                else -> "since $formatted · $label in ${progress.daysUntilNext} days"
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
                    ErrorBanner(uiState.errorMessage!!)
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
                        onEdit = viewModel::openEditForm,
                        onDelete = { toDelete = it },
                    )
                    Spacer(Modifier.height(24.dp))
                    DateSection(
                        label = "our dates",
                        emptyText = "first date, first trip, the day you moved in - we'll remind you every year.",
                        dates = uiState.milestones,
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
    onEdit: (SpecialDateDto) -> Unit,
    onDelete: (SpecialDateDto) -> Unit,
) {
    SectionLabel(label)
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
            if (unit.isNotEmpty()) Text(unit, style = PinguCoduType.monoLabel)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(date.title, style = MaterialTheme.typography.titleMedium)
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
    // Older dates kept their emoji separately - fold it into the title so saving doesn't lose it.
    var title by remember { mutableStateOf(editing?.let { listOfNotNull(it.emoji, it.title).joinToString(" ") } ?: "") }
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
            NeoChoiceChip(label = "countdown", selected = kind == KIND_COUNTDOWN, onClick = { kind = KIND_COUNTDOWN })
            NeoChoiceChip(
                label = "milestone",
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
            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        )
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
            ErrorBanner(dateProblem)
        }
        Spacer(Modifier.height(20.dp))

        if (formError != null) {
            ErrorBanner(formError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (editing == null) "save it" else "save changes", enabled = isValid && !isSubmitting) {
            // Empty emoji clears an older date's separate emoji, now that it lives in the title.
            onSubmit(SpecialDateRequest(kind = kind, title = title.trim(), emoji = "", date = date))
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
