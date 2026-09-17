package com.pingucodu.us.ui.screens.cycle

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.CycleLogDto
import com.pingucodu.us.data.network.CycleStatusDto
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.NeoSwitch
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.dashedBorder
import com.pingucodu.us.ui.theme.hardShadow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FLOWS = listOf("spotting", "light", "medium", "heavy")
private val CardShape = RoundedCornerShape(18.dp)
private val DAY_OF_WEEK_LABELS = listOf("s", "m", "t", "w", "t", "f", "s")

@Composable
fun CycleScreen(modifier: Modifier = Modifier, viewModel: CycleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var logToDelete by remember { mutableStateOf<CycleLogDto?>(null) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = remember { LocalDate.now() }

    val trackedUser = uiState.status?.trackedUser
    val partner = uiState.currentUsername?.let { me -> listOf("pingu", "codu").firstOrNull { it != me } }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (uiState.isLoading && uiState.status == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
                return@Column
            }

            val stats = remember(uiState.logs) { computeCycleStats(uiState.logs) }
            CycleHeaderCard(status = uiState.status, canLog = uiState.canLog, avgCycleLength = stats.avgCycleLength)

            if (uiState.errorMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(uiState.errorMessage!!, color = Coral, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))

            if (uiState.canLog) {
                CalendarCard(
                    month = displayedMonth,
                    logs = uiState.logs,
                    predictedNextDate = uiState.status?.predictedNextDate,
                    today = today,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it },
                    onPrevMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                    onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) },
                )
                Spacer(Modifier.height(16.dp))

                SelectedDayCard(
                    date = selectedDate,
                    logs = uiState.logs,
                    onLogThisDay = viewModel::openAddDialog,
                )

                if (stats.recentCycles.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    CycleBarChartCard(stats = stats, predictedNextDate = uiState.status?.predictedNextDate, today = today)
                }

                Spacer(Modifier.height(20.dp))
                NudgesCard(partner = partner ?: "them")

                if (uiState.logs.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text("LOG HISTORY", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.logs.sortedByDescending { it.logDate }.take(10).forEach { log ->
                            LogRow(log = log, onLongPress = { logToDelete = log })
                        }
                    }
                }
            } else {
                HeadsUpView(status = uiState.status, trackedUser = trackedUser ?: "your partner")
            }
            Spacer(Modifier.height(130.dp))
        }

        if (uiState.canLog) {
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
                        onClick = viewModel::openAddDialog,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = MaterialTheme.typography.headlineLarge)
            }
        }
    }

    if (uiState.showAddDialog) {
        AddLogDialog(
            initialDate = selectedDate.toString(),
            dialogError = uiState.dialogError,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissDialog,
            onSubmit = { date, flow, note -> viewModel.addLog(date, flow, note) },
        )
    }

    if (logToDelete != null) {
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            title = { Text("delete log?") },
            text = { Text("delete the entry for ${logToDelete?.logDate}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLog(logToDelete!!.id)
                    logToDelete = null
                }) { Text("delete", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { logToDelete = null }) { Text("cancel") } },
        )
    }
}

@Composable
private fun CycleHeaderCard(status: CycleStatusDto?, canLog: Boolean, avgCycleLength: Int?) {
    val label = if (canLog) "YOUR CYCLE" else "${(status?.trackedUser ?: "").uppercase()} · CYCLE"
    val headline = if (canLog) {
        status?.currentDay?.let { "day $it · ${status.statusLabel}" } ?: "not tracked yet"
    } else {
        status?.statusLabel ?: "not tracked yet"
    }
    val subtitle = if (canLog) {
        status?.predictedNextDate?.let { "next period ${formatShortDate(it)} · avg cycle ${avgCycleLength ?: 28} days" }
    } else {
        val next = status?.predictedNextDate?.let { "next period ${formatShortDate(it)}" }
        listOfNotNull(status?.currentDay?.let { "day $it" }, next).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(Teal, CardShape)
            .padding(20.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(headline, style = MaterialTheme.typography.displayMedium, color = Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    logs: List<CycleLogDto>,
    predictedNextDate: String?,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val days = remember(month, logs, predictedNextDate) { buildCalendarDays(month, logs, predictedNextDate, today) }
    val rows = remember(days) {
        val padded = days + List((7 - days.size % 7) % 7) { null }
        padded.chunked(7)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)).uppercase(),
                style = PinguCoduType.monoLabel,
                modifier = Modifier.weight(1f),
            )
            MonthNavButton("‹", onPrevMonth)
            Spacer(Modifier.width(6.dp))
            MonthNavButton("›", onNextMonth)
        }
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_OF_WEEK_LABELS.forEach { d ->
                Text(
                    d,
                    style = PinguCoduType.mono,
                    color = DescriptionGrey,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { day ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        if (day != null) {
                            DayCell(day = day, selected = day.date == selectedDate, onClick = { onSelectDate(day.date) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendSwatch(Teal, "logged flow")
            LegendSwatch(Color.White, "predicted")
            LegendSwatch(YellowSoft, "fertile")
        }
    }
}

@Composable
private fun MonthNavButton(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(30.dp)
            .border(1.5.dp, Ink, RoundedCornerShape(9.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DayCell(day: CycleDay, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val bg = when {
        day.hasLog -> Teal
        day.isFertile -> YellowSoft
        else -> PinkTint
    }
    val interactionSource = remember { MutableInteractionSource() }
    val base = Modifier
        .fillMaxSize()
        .background(bg, shape)
        .then(
            if (day.isPredictedPeriod && !day.hasLog) {
                Modifier.dashedBorder(2.dp, Ink, shape, dashLength = 3.dp, gapLength = 2.dp)
            } else {
                Modifier.border(if (day.isToday || selected) 3.dp else 2.dp, Ink.copy(alpha = if (day.isToday || selected) 1f else 0.35f), shape)
            }
        )
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)

    Box(modifier = base, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = MaterialTheme.typography.titleMedium.fontWeight))
            if (day.hasLog) {
                Box(Modifier.size(3.dp).background(Ink, CircleShape))
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .background(color, RoundedCornerShape(4.dp))
                .border(1.5.dp, Ink, RoundedCornerShape(4.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SelectedDayCard(date: LocalDate, logs: List<CycleLogDto>, onLogThisDay: () -> Unit) {
    val log = logs.firstOrNull { it.logDate == date.toString() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(RoundedCornerShape(16.dp), offsetX = 4.dp, offsetY = 4.dp)
            .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
            .background(Pink, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)).uppercase(), style = PinguCoduType.monoLabel)
        Spacer(Modifier.height(4.dp))
        Text(
            log?.let { "${it.flow ?: "logged"}${it.note?.let { n -> " · $n" } ?: ""}" } ?: "nothing logged yet for this day",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogThisDay,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        ) {
            Text("log this day", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CycleBarChartCard(stats: CycleStats, predictedNextDate: String?, today: LocalDate) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.background, CardShape)
            .padding(16.dp),
    ) {
        Text("LAST ${stats.recentCycles.size} CYCLES", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(14.dp))
        val maxLen = stats.recentCycles.maxOf { it.lengthDays }.coerceAtLeast(1)
        Row(
            modifier = Modifier.fillMaxWidth().height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.recentCycles.forEach { bar ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(bar.lengthDays.toString(), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    val fraction = (bar.lengthDays.toFloat() / maxLen).coerceIn(0.15f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(Pink, RoundedCornerShape(6.dp))
                            .border(1.5.dp, Ink, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.recentCycles.forEach { bar ->
                Text(
                    bar.monthLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val daysToNext = predictedNextDate?.let { pred ->
            runCatching { java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(pred)) }.getOrNull()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatChip(value = "${stats.avgCycleLength ?: 28}d", caption = "AVG CYCLE", modifier = Modifier.weight(1f))
            StatChip(value = "${stats.avgPeriodLength ?: 5}d", caption = "AVG PERIOD", modifier = Modifier.weight(1f))
            StatChip(value = daysToNext?.let { "${it}d" } ?: "—", caption = "TO NEXT", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatChip(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.5.dp, Ink, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(caption, style = PinguCoduType.monoLabel)
    }
}

@Composable
private fun NudgesCard(partner: String) {
    var periodExpected by remember { mutableStateOf(true) }
    var fertileWindow by remember { mutableStateOf(true) }
    var logSymptoms by remember { mutableStateOf(false) }
    var beNice by remember { mutableStateOf(true) }

    Text("NUDGES", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(horizontal = 16.dp),
    ) {
        NudgeRow("period expected in 3 days", periodExpected) { periodExpected = it }
        DashedDivider()
        NudgeRow("fertile window opens", fertileWindow) { fertileWindow = it }
        DashedDivider()
        NudgeRow("log symptoms every evening", logSymptoms) { logSymptoms = it }
        DashedDivider()
        NudgeRow("tell $partner to be extra nice", beNice) { beNice = it }
    }
}

@Composable
private fun NudgeRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        NeoSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HeadsUpView(status: CycleStatusDto?, trackedUser: String) {
    val currentDay = status?.currentDay
    val statusLabel = status?.statusLabel
    val predictedNextDate = status?.predictedNextDate
    val daysAway = predictedNextDate?.let {
        runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it)) }.getOrNull()
    }

    Text("YOUR HEADS-UP", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val statusLine = if (currentDay != null) {
            "$trackedUser is on day $currentDay of her cycle — ${statusLabel ?: "tracking"}."
        } else {
            "$trackedUser hasn't logged anything yet."
        }
        HeadsUpRow(statusLine, highlighted = true)

        if (predictedNextDate != null) {
            HeadsUpRow("next period expected ${formatShortDate(predictedNextDate)}${daysAway?.let { " ($it days away)" } ?: ""}.")
        }

        val tip = if (status?.onPeriod == true) {
            "she might want a heating pad and zero plans."
        } else {
            "nothing you need to do — just a heads-up."
        }
        HeadsUpRow(tip)

        Spacer(Modifier.height(2.dp))
        Text(
            "$trackedUser's detailed logs stay private. you get the phase and the plan, nothing else.",
            style = MaterialTheme.typography.labelSmall,
        )
    }

    Spacer(Modifier.height(20.dp))
    Text("BE-NICE KIT", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(YellowSoft, CardShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("hot water bag", "dark chocolate", "no plans day", "do the dishes", "send a dumb meme").forEach { idea ->
            Box(
                modifier = Modifier
                    .border(1.5.dp, Ink, RoundedCornerShape(50))
                    .background(Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(idea, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HeadsUpRow(text: String, highlighted: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
            .background(if (highlighted) Pink else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).border(1.5.dp, Ink, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun LogRow(log: CycleLogDto, onLongPress: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(RoundedCornerShape(14.dp))
            .border(BorderWidth, Ink, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formatShortDate(log.logDate), style = MaterialTheme.typography.titleMedium)
            if (!log.note.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(log.note, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (log.flow != null) {
            Box(
                modifier = Modifier
                    .border(1.5.dp, Ink, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(log.flow, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLogDialog(
    initialDate: String,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (logDate: String, flow: String, note: String?) -> Unit,
) {
    var logDate by remember { mutableStateOf(initialDate) }
    var flow by remember { mutableStateOf(FLOWS[1]) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(RoundedCornerShape(20.dp))
                .border(BorderWidth, Ink, RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text("log a day", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("date", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedTextField(
                    value = logDate,
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Ink,
                        disabledTextColor = Ink,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickableNoRipple { showDatePicker = true },
                )
            }
            Spacer(Modifier.height(16.dp))

            Text("flow", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FLOWS.forEach { f ->
                    Box(
                        modifier = Modifier
                            .border(2.dp, Ink, RoundedCornerShape(50))
                            .background(if (flow == f) Teal else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                            .clickableNoRipple { flow = f }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(f, style = MaterialTheme.typography.bodySmall, color = Ink)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("note (optional)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
            )
            Spacer(Modifier.height(16.dp))

            if (dialogError != null) {
                Text(dialogError, color = Coral, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(logDate, flow, note.ifBlank { null }) },
                    enabled = !isSubmitting,
                    modifier = Modifier.border(BorderWidth, Ink, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink),
                ) {
                    Text("save")
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(logDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        logDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("ok") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

private fun formatShortDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)).lowercase()
} catch (e: Exception) {
    isoDate
}
