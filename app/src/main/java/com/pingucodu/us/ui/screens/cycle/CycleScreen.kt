package com.pingucodu.us.ui.screens.cycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.CycleLogDto
import com.pingucodu.us.data.network.CycleObservationDto
import com.pingucodu.us.data.network.CycleStatusDto
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.NeoConfirmDialog
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PlaceholderGrey
import com.pingucodu.us.ui.theme.Purple
import com.pingucodu.us.ui.theme.SkeletonCycleHeaderCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.dashedBorder
import com.pingucodu.us.ui.theme.hardShadow
import com.pingucodu.us.ui.util.LocalNameMask
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FLOWS = listOf("spotting", "light", "medium", "heavy")
private val SYMPTOM_TAGS = listOf("cramps", "headache", "bloating", "fatigue", "backache", "nausea", "tender breasts", "acne")
private val MOOD_TAGS = listOf("happy", "sad", "anxious", "irritable", "calm", "sensitive", "naughty")
private val BEHAVIOR_TAGS = listOf("sweet", "moody", "clingy", "chill", "grumpy", "loving", "distant", "funny", "needy", "thoughtful")
private val ChipAccent = Pink
private val PartnerNoteFieldColor = Color(0xFFFFD3E6)
private val CardShape = RoundedCornerShape(18.dp)
private val DAY_OF_WEEK_LABELS = listOf("s", "m", "t", "w", "t", "f", "s")

@Composable
fun CycleScreen(modifier: Modifier = Modifier, viewModel: CycleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var logToDelete by remember { mutableStateOf<CycleLogDto?>(null) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = remember { LocalDate.now() }
    var editingObsDate by remember { mutableStateOf(today.toString()) }
    var observationToDelete by remember { mutableStateOf<CycleObservationDto?>(null) }

    val trackedUser = LocalNameMask.current.resolve(uiState.status?.trackedUser)

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.status != null,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (uiState.isLoading && uiState.status == null) {
                SkeletonCycleHeaderCard()
                return@Column
            }

            val stats = remember(uiState.logs) { computeCycleStats(uiState.logs) }
            CycleHeaderCard(status = uiState.status, canLog = uiState.canLog, avgCycleLength = stats.avgCycleLength)

            if (uiState.errorMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(uiState.errorMessage!!, color = Coral, style = MaterialTheme.typography.bodySmall)
            }

            if (uiState.canLog && uiState.status?.irregularityMessage != null) {
                Spacer(Modifier.height(12.dp))
                IrregularityBanner(uiState.status!!.irregularityMessage!!)
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
                ObservationCard(observation = uiState.status?.observation)

                if (uiState.logs.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text("LOG HISTORY", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.logs.sortedByDescending { it.logDate }.take(10).forEach { log ->
                            LogRow(
                                log = log,
                                onClick = {
                                    selectedDate = LocalDate.parse(log.logDate)
                                    viewModel.openAddDialog()
                                },
                                onLongPress = { logToDelete = log },
                            )
                        }
                    }
                }
            } else {
                HeadsUpView(
                    status = uiState.status,
                    trackedUser = trackedUser ?: "your partner",
                    observations = uiState.observations,
                    editingObsDate = editingObsDate,
                    onEditingObsDateChange = { editingObsDate = it },
                    isSubmittingObservation = uiState.isSubmittingObservation,
                    observationError = uiState.observationError,
                    onSubmitObservation = { tags, note ->
                        viewModel.addObservation(tags, note, editingObsDate)
                    },
                    onRequestDeleteObservation = { observationToDelete = it },
                )
            }
            Spacer(Modifier.height(210.dp))
        }
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
            date = selectedDate.toString(),
            existingLog = uiState.logs.firstOrNull { it.logDate == selectedDate.toString() },
            previousDayFlow = uiState.logs.firstOrNull { it.logDate == selectedDate.minusDays(1).toString() }?.flow,
            dialogError = uiState.dialogError,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissDialog,
            onSubmit = { date, flow, note, tags, partnerNote -> viewModel.addLog(date, flow, note, tags, partnerNote) },
        )
    }

    if (logToDelete != null) {
        val target = logToDelete!!
        NeoConfirmDialog(
            title = "delete this?",
            message = "the entry for ${formatShortDate(target.logDate)} goes away for good, ${if (target.flow != null) "flow, " else ""}tags, and notes included.",
            confirmLabel = "delete",
            badgeLabel = "no undo",
            accentColor = Pink,
            onConfirm = {
                viewModel.deleteLog(target.id)
                logToDelete = null
            },
            onDismiss = { logToDelete = null },
        )
    }

    if (observationToDelete != null) {
        val target = observationToDelete!!
        NeoConfirmDialog(
            title = "delete this?",
            message = "the log for ${formatShortDate(target.obsDate)} about ${trackedUser ?: "them"} goes away for good.",
            confirmLabel = "delete",
            badgeLabel = "no undo",
            accentColor = Pink,
            onConfirm = {
                viewModel.deleteObservation(target.obsDate)
                if (target.obsDate == editingObsDate) editingObsDate = today.toString()
                observationToDelete = null
            },
            onDismiss = { observationToDelete = null },
        )
    }
}

@Composable
private fun CycleHeaderCard(status: CycleStatusDto?, canLog: Boolean, avgCycleLength: Int?) {
    val label = if (canLog) "YOUR CYCLE" else "${(LocalNameMask.current.resolve(status?.trackedUser) ?: "").uppercase()} · CYCLE"
    val headline = if (status?.currentDay != null) {
        "day ${status.currentDay} · ${status.phase ?: status.statusLabel}"
    } else {
        "not tracked yet"
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
private fun IrregularityBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderWidth, Ink, RoundedCornerShape(14.dp))
            .background(YellowSoft, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Ink, modifier = Modifier.weight(1f))
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LegendSwatch(Teal, "logged flow")
            LegendSwatch(PinkTint, "logged mood", dot = true)
            LegendSwatch(Color.White, "predicted", dashed = true)
            LegendSwatch(YellowSoft, "fertile")
            LegendSwatch(Purple.copy(alpha = 0.3f), "pms")
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
        day.hasFlow -> Teal
        day.isPredictedPeriod -> Color.White
        day.isFertile -> YellowSoft
        day.isPmsWindow -> Purple.copy(alpha = 0.3f)
        else -> PinkTint
    }
    val interactionSource = remember { MutableInteractionSource() }
    val base = Modifier
        .fillMaxSize()
        .background(bg, shape)
        .then(
            if (day.isPredictedPeriod && !day.hasFlow) {
                Modifier.dashedBorder(2.dp, Ink, shape, dashLength = 3.dp, gapLength = 2.dp)
            } else {
                Modifier.border(if (day.isToday || selected) 3.dp else 2.dp, Ink.copy(alpha = if (day.isToday || selected) 1f else 0.35f), shape)
            }
        )
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)

    Box(modifier = base, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = MaterialTheme.typography.titleMedium.fontWeight))
            if (day.hasEntry) {
                Box(Modifier.size(3.dp).background(Ink, CircleShape))
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, dashed: Boolean = false, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(15.dp)
                .background(color, RoundedCornerShape(4.dp))
                .then(
                    if (dashed) {
                        Modifier.dashedBorder(1.5.dp, Ink, RoundedCornerShape(4.dp), dashLength = 2.dp, gapLength = 1.5.dp)
                    } else {
                        Modifier.border(1.5.dp, Ink, RoundedCornerShape(4.dp))
                    },
                ),
        ) {
            if (dot) Box(Modifier.size(3.dp).background(Ink, CircleShape))
        }
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
            log?.let { "${it.flow ?: "no period"}${it.note?.let { n -> " · $n" } ?: ""}" } ?: "nothing logged yet for this day",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
        if (log?.tags?.isNotEmpty() == true) {
            Spacer(Modifier.height(8.dp))
            TagChipRow(log.tags, chipColor = Color.White)
        }
        if (!log?.partnerNote.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("note to partner: ${log?.partnerNote}", style = MaterialTheme.typography.bodySmall, color = Ink)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogThisDay,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        ) {
            Text(if (log != null) "edit this day" else "log this day", style = MaterialTheme.typography.titleMedium)
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
            .background(PinkTint, CardShape)
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
            StatChip(value = daysToNext?.let { "${it}d" } ?: "-", caption = "TO NEXT", modifier = Modifier.weight(1f))
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
private fun HeadsUpView(
    status: CycleStatusDto?,
    trackedUser: String,
    observations: List<CycleObservationDto>,
    editingObsDate: String,
    onEditingObsDateChange: (String) -> Unit,
    isSubmittingObservation: Boolean,
    observationError: String?,
    onSubmitObservation: (tags: List<String>, note: String?) -> Unit,
    onRequestDeleteObservation: (CycleObservationDto) -> Unit,
) {
    val currentDay = status?.currentDay
    val phase = status?.phase
    val predictedNextDate = status?.predictedNextDate
    val daysAway = status?.daysUntilNextPeriod

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("YOUR HEADS-UP", style = MaterialTheme.typography.labelMedium)

        val statusLine = if (currentDay != null) {
            "$trackedUser is on day $currentDay of her cycle${phase?.let { " - $it phase" } ?: ""}."
        } else {
            "$trackedUser hasn't logged anything yet."
        }
        HeadsUpRow(statusLine, highlighted = true)

        if (predictedNextDate != null) {
            HeadsUpRow("next period expected ${formatShortDate(predictedNextDate)}${daysAway?.let { " ($it days away)" } ?: ""}.")
        }

        val tip = if (status?.onPeriod == true) {
            "she might want a heating pad and zero plans."
        } else if (status?.pmsWindowActive == true) {
            "she's in her PMS window - extra patience goes a long way."
        } else {
            null
        }
        if (tip != null) HeadsUpRow(tip)

        val latestEntry = status?.latestEntry
        HeadsUpRow(
            if (latestEntry != null && latestEntry.tags.isNotEmpty()) {
                "feeling as of ${formatShortDate(latestEntry.logDate)}: ${latestEntry.tags.joinToString(", ")}."
            } else {
                "nothing logged for how she's feeling yet."
            },
        )

        val partnerNote = latestEntry?.partnerNote
        if (!partnerNote.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
                    .background(Pink, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text("A NOTE FOR YOU · ${formatShortDate(latestEntry.logDate).uppercase()}", style = PinguCoduType.monoLabel)
                Spacer(Modifier.height(4.dp))
                Text(partnerNote, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            "$trackedUser's detailed logs and private notes stay private. you get the phase and the plan, nothing else.",
            style = MaterialTheme.typography.labelSmall,
        )
    }

    Spacer(Modifier.height(20.dp))
    ObservationInputCard(
        trackedUser = trackedUser,
        date = editingObsDate,
        existingObservation = observations.firstOrNull { it.obsDate == editingObsDate },
        isSubmitting = isSubmittingObservation,
        errorMessage = observationError,
        onSubmit = onSubmitObservation,
        onCancelEdit = { onEditingObsDateChange(LocalDate.now().toString()) },
    )

    if (observations.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text("LOG HISTORY", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            observations.sortedByDescending { it.obsDate }.take(10).forEach { obs ->
                ObservationRow(
                    observation = obs,
                    onClick = { onEditingObsDateChange(obs.obsDate) },
                    onLongPress = { onRequestDeleteObservation(obs) },
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(YellowSoft, CardShape)
            .padding(16.dp),
    ) {
        Text("HER PHASE, EXPLAINED", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            phase?.replaceFirstChar { it.uppercase() } ?: "not enough data yet",
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(phaseExplanation(phase), style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

/** A plain, medically-grounded summary of what's hormonally happening in each cycle phase. */
private fun phaseExplanation(phase: String?): String = when (phase) {
    "menstrual" -> "This is the period phase, when the uterus sheds the lining it built up during the previous cycle. This causes menstrual bleeding and usually lasts a few days. Estrogen and progesterone are at their lowest levels during this time. The uterus produces chemicals called prostaglandins that make it contract and help shed the lining, which can cause cramps. These hormonal changes can also contribute to tiredness, headaches, lower energy, mood changes, and feeling more sensitive or withdrawn. Rest, sleep, hydration, and gentle movement may help with some of these symptoms."
    "follicular" -> "This phase begins on the first day of the period and continues until ovulation. After the period starts, the brain signals the ovaries to develop several small follicles, each containing an immature egg. One follicle usually becomes dominant and prepares to release an egg. As the follicle develops, estrogen levels gradually increase. This rise in estrogen helps rebuild the uterine lining after the period and can be associated with improving energy, mood, concentration, and overall well-being. Some people may also notice changes in their skin, appetite, or sex drive during this phase."
    "ovulation" -> "Ovulation is the point in the cycle when an ovary releases a mature egg. It usually happens around the middle of the cycle, although the exact timing can vary considerably from person to person and from cycle to cycle. A rise in estrogen leads to a surge in luteinizing hormone (LH), which triggers the release of the egg. Around this time, cervical mucus often becomes clearer, thinner, and more slippery, helping sperm move more easily. Some people notice a temporary increase in energy or libido, and some may experience mild one-sided pelvic discomfort. Basal body temperature usually rises slightly after ovulation because progesterone increases."
    "luteal" -> "After ovulation, the body enters the luteal phase. The follicle that released the egg changes into a structure called the corpus luteum, which produces progesterone. Progesterone helps prepare and maintain the uterine lining in case a pregnancy occurs. Estrogen also remains present and contributes to these changes. Some people feel relatively stable during the first part of this phase, while others notice changes such as increased appetite, breast tenderness, bloating, tiredness, or changes in mood as the phase progresses. If pregnancy does not occur, the corpus luteum breaks down and progesterone and estrogen levels eventually fall, triggering the next period."
    "pms" -> "PMS, or premenstrual syndrome, refers to a group of physical and emotional symptoms that can occur in the days before a period. During this part of the cycle, estrogen and progesterone levels are falling as the body prepares for menstruation. These hormonal changes can affect the brain and other parts of the body, which may lead to symptoms such as mood changes, irritability, anxiety, tiredness, bloating, headaches, breast tenderness, food cravings, and changes in appetite or sleep. PMS varies widely between people, and even the same person may experience different symptoms from one cycle to another. Symptoms generally improve once the period begins."
    else -> "Once there is enough cycle history, this section will explain what is likely happening in the body during the current phase. It can describe the hormonal changes taking place, what is happening in the ovaries and uterus, and common physical or emotional changes that may occur during this part of the cycle."
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

/** Lets the partner (non-tracked user) log, review, and edit/delete their read on the tracked user's day. */
@Composable
private fun ObservationInputCard(
    trackedUser: String,
    date: String,
    existingObservation: CycleObservationDto?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (tags: List<String>, note: String?) -> Unit,
    onCancelEdit: () -> Unit,
) {
    val isToday = date == LocalDate.now().toString()

    var selectedTags by remember(date, existingObservation?.tags) {
        mutableStateOf(existingObservation?.tags?.toSet().orEmpty())
    }
    var note by remember(date, existingObservation?.note) {
        mutableStateOf(existingObservation?.note ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HOW WAS $trackedUser?", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                Text(if (isToday) "today" else formatShortDate(date), style = PinguCoduType.monoLabel, color = DescriptionGrey)
            }
            if (!isToday) {
                Text(
                    "back to today",
                    style = MaterialTheme.typography.labelSmall,
                    color = Coral,
                    modifier = Modifier.clickableNoRipple(onCancelEdit),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BEHAVIOR_TAGS.forEach { tag ->
                NeoChip(
                    label = tag,
                    selected = tag in selectedTags,
                    onClick = { selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        NeoField(
            value = note,
            onValueChange = { note = it },
            placeholder = "anything else? (optional)",
            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        if (errorMessage != null) {
            Text(errorMessage, color = Coral, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(RoundedCornerShape(14.dp), offsetX = 3.dp, offsetY = 3.dp)
                .border(BorderWidth, Ink, RoundedCornerShape(14.dp))
                .background(Ink, RoundedCornerShape(14.dp))
                .clickableNoRipple(enabled = selectedTags.isNotEmpty() && !isSubmitting) {
                    onSubmit(selectedTags.toList(), note.ifBlank { null })
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (existingObservation != null) "save changes" else "log it", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

/** Shows the tracked user her partner's logged observation, mirroring the note-to-partner feature in reverse. */
@Composable
private fun ObservationCard(observation: CycleObservationDto?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(16.dp),
    ) {
        Text("PARTNER'S TAKE", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (observation == null) {
            Text("nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(formatShortDate(observation.obsDate).uppercase(), style = PinguCoduType.monoLabel, color = DescriptionGrey)
            if (observation.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TagChipRow(observation.tags)
            }
            if (!observation.note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(observation.note, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}

@Composable
private fun TagChipRow(tags: List<String>, chipColor: Color = PinkTint) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            val shape = if (tag in SYMPTOM_TAGS) RoundedCornerShape(10.dp) else RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .border(1.5.dp, Ink, shape)
                    .background(chipColor, shape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = Ink)
            }
        }
    }
}

@Composable
private fun LogRow(log: CycleLogDto, onClick: () -> Unit, onLongPress: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(RoundedCornerShape(14.dp))
            .border(BorderWidth, Ink, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(log.flow, style = MaterialTheme.typography.labelMedium, color = Ink)
                }
            }
        }
        if (log.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TagChipRow(log.tags)
        }
    }
}

@Composable
private fun ObservationRow(observation: CycleObservationDto, onClick: () -> Unit, onLongPress: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(RoundedCornerShape(14.dp))
            .border(BorderWidth, Ink, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(formatShortDate(observation.obsDate), style = MaterialTheme.typography.titleMedium)
        if (!observation.note.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(observation.note, style = MaterialTheme.typography.bodySmall)
        }
        if (observation.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TagChipRow(observation.tags)
        }
    }
}

@Composable
private fun AddLogDialog(
    date: String,
    existingLog: CycleLogDto?,
    previousDayFlow: String?,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (logDate: String, flow: String?, note: String?, tags: List<String>, partnerNote: String?) -> Unit,
) {
    // New entries continue yesterday's period if there was one; otherwise default to a mood/symptom-only log.
    var flow by remember { mutableStateOf(if (existingLog != null) existingLog.flow else previousDayFlow) }
    var note by remember { mutableStateOf(existingLog?.note ?: "") }
    var partnerNote by remember { mutableStateOf(existingLog?.partnerNote ?: "") }
    var selectedTags by remember { mutableStateOf(existingLog?.tags?.toSet() ?: emptySet()) }

    val isEmpty = flow == null && selectedTags.isEmpty() && note.isBlank() && partnerNote.isBlank()
    var showEmptyError by remember { mutableStateOf(false) }

    fun submit() = onSubmit(date, flow, note.ifBlank { null }, selectedTags.toList(), partnerNote.ifBlank { null })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setDimAmount(0f)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        val sheetHeight = LocalConfiguration.current.screenHeightDp.dp - 70.dp
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.45f))
                    .clickableNoRipple(onDismiss),
            )

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(animationSpec = tween(240), initialOffsetY = { it }) + fadeIn(tween(240)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetHeight)
                        .border(4.dp, Ink, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(PinkTint, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (existingLog != null) "edit log" else "log a day",
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.weight(1f),
                        )
                        SheetCloseButton(onClick = onDismiss)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("LOGGING FOR", style = PinguCoduType.monoLabel, color = DescriptionGrey)
                            Spacer(Modifier.height(4.dp))
                            Text(formatFullDate(date), style = MaterialTheme.typography.headlineMedium, color = Ink)
                        }
                        Spacer(Modifier.width(12.dp))
                        DateBadge(date)
                    }
                    Spacer(Modifier.height(22.dp))

                    SectionLabel("flow")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeoChip(label = "no period", selected = flow == null, large = true, onClick = { flow = null })
                        FLOWS.forEach { f ->
                            NeoChip(label = f, selected = flow == f, large = true, onClick = { flow = f })
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    SectionLabel("symptoms")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SYMPTOM_TAGS.forEach { tag ->
                            NeoChip(
                                label = tag,
                                selected = tag in selectedTags,
                                large = true,
                                onClick = { selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    SectionLabel("mood")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MOOD_TAGS.forEach { tag ->
                            NeoChip(
                                label = tag,
                                selected = tag in selectedTags,
                                large = true,
                                onClick = { selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    SectionLabel("note - private, only you see this")
                    Spacer(Modifier.height(8.dp))
                    NeoField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "how are you feeling?",
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))

                    SectionLabel("note to partner - optional, ${LocalNameMask.current.resolve("codu")} will see this")
                    Spacer(Modifier.height(8.dp))
                    NeoField(
                        value = partnerNote,
                        onValueChange = { partnerNote = it },
                        placeholder = "leave something for ${LocalNameMask.current.resolve("codu")}",
                        backgroundColor = PartnerNoteFieldColor,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))

                    val error = dialogError ?: "pick a flow, a tag, or add a note".takeIf { showEmptyError && isEmpty }
                    if (error != null) {
                        Text(error, color = Coral, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hardShadow(RoundedCornerShape(16.dp), offsetX = 4.dp, offsetY = 4.dp)
                            .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
                            .background(Ink, RoundedCornerShape(16.dp))
                            .clickableNoRipple(enabled = !isSubmitting) {
                                if (isEmpty) showEmptyError = true else submit()
                            }
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (existingLog != null) "save changes" else "add log",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetCloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("×", style = MaterialTheme.typography.headlineMedium, color = Ink)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = PinguCoduType.monoLabel, color = Ink)
}

@Composable
private fun NeoChip(label: String, selected: Boolean, accentColor: Color = ChipAccent, large: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (large) 14.dp else 12.dp)
    val hPad = if (large) 16.dp else 11.dp
    val vPad = if (large) 12.dp else 7.dp
    val textStyle = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall
    val borderW = if (large) BorderWidth else 1.5.dp
    val shadowOffset = if (large) 3.dp else 2.dp
    Box(
        modifier = Modifier
            .let { if (selected) it.hardShadow(shape, offsetX = shadowOffset, offsetY = shadowOffset) else it }
            .border(borderW, Ink, shape)
            .background(if (selected) accentColor else Color.White, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = textStyle, color = Ink)
    }
}

@Composable
private fun NeoField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    backgroundColor: Color = Color.White,
    placeholderColor: Color = PlaceholderGrey,
    minLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(BorderWidth, Ink, shape)
            .background(backgroundColor, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle, color = placeholderColor)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = Ink),
            cursorBrush = SolidColor(Ink),
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
private fun Modifier.clickableNoRipple(enabled: Boolean, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
}

private fun formatShortDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)).lowercase()
} catch (e: Exception) {
    isoDate
}

private fun formatFullDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)).lowercase()
} catch (e: Exception) {
    isoDate
}

@Composable
private fun DateBadge(isoDate: String) {
    val parsed = runCatching { LocalDate.parse(isoDate) }.getOrNull()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(BorderWidth, Ink, shape)
            .background(Pink, shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            parsed?.month?.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)?.uppercase() ?: "-",
            style = PinguCoduType.monoLabel,
        )
        Text(parsed?.dayOfMonth?.toString() ?: "?", style = MaterialTheme.typography.headlineLarge)
    }
}
