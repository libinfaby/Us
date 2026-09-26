package com.pingucodu.us.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.NudgeDto
import com.pingucodu.us.data.network.SpecialDateDto
import com.pingucodu.us.ui.components.NeoBottomSheet
import com.pingucodu.us.ui.components.NeoChoiceChip
import com.pingucodu.us.ui.components.NeoField
import com.pingucodu.us.ui.components.SectionLabel
import com.pingucodu.us.ui.components.SubmitButton
import com.pingucodu.us.ui.components.clickableNoRipple
import com.pingucodu.us.ui.screens.dates.dateBadge
import com.pingucodu.us.ui.screens.dates.dateSubtitle
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Green
import com.pingucodu.us.ui.theme.Orange
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.SkeletonActivityCard
import com.pingucodu.us.ui.theme.SkeletonFeatureTeaserRow
import com.pingucodu.us.ui.theme.SkeletonRecurringCard
import com.pingucodu.us.ui.theme.SkeletonScoreCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow
import com.pingucodu.us.ui.util.LocalNameMask

private val CardShape = RoundedCornerShape(18.dp)
private val TileShape = RoundedCornerShape(16.dp)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToMoney: () -> Unit = {},
    onNavigateToCycle: () -> Unit = {},
    onNavigateToStash: () -> Unit = {},
    onNavigateToDates: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNudgePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshOnEntry()
    }

    val showSkeleton = uiState.isLoading && !uiState.hasLoadedOnce

    PullToRefreshBox(
        isRefreshing = uiState.isLoading && uiState.hasLoadedOnce,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize().background(PinkTint),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (showSkeleton) {
                SkeletonScoreCard()
                Spacer(Modifier.height(16.dp))
                SkeletonFeatureTeaserRow()
                Spacer(Modifier.height(20.dp))
                SkeletonActivityCard(rows = 3)
                Spacer(Modifier.height(20.dp))
                SkeletonRecurringCard(rows = 2)
                Spacer(Modifier.height(110.dp))
                return@Column
            }

            DatesCard(
                nextCountdown = uiState.nextCountdown,
                nextMilestone = uiState.nextMilestone,
                onClick = onNavigateToDates,
            )
            Spacer(Modifier.height(16.dp))

            ScoreCard(
                net = uiState.net,
                currentUsername = uiState.username,
                openCount = uiState.openExpenseCount,
                onSeeAll = onNavigateToMoney,
                onSettleUp = onNavigateToMoney,
            )
            Spacer(Modifier.height(16.dp))

            NudgeCard(
                status = uiState.nudgeStatus,
                latestNudge = uiState.latestNudge,
                latestNudgeTimeLabel = uiState.latestNudgeTimeLabel,
                onSend = { viewModel.sendNudge() },
                onLongPress = { showNudgePicker = true },
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeatureTeaserCard(
                    label = "CYCLE",
                    title = uiState.cycleStatus?.currentDay?.let { "day $it" } ?: "not tracked yet",
                    subtitle = uiState.cycleStatus?.takeIf { it.currentDay != null }?.statusLabel,
                    color = Teal,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = onNavigateToCycle,
                )
                FeatureTeaserCard(
                    label = "STASH",
                    title = "${uiState.stashSavedCount} saved",
                    subtitle = if (uiState.stashTodoCount > 0) "${uiState.stashTodoCount} to-dos pending" else null,
                    color = YellowSoft,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = onNavigateToStash,
                )
            }

            if (uiState.activityFeed.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hardShadow(CardShape)
                        .border(BorderWidth, Ink, CardShape)
                        .background(MaterialTheme.colorScheme.surface, CardShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("LATEST", style = MaterialTheme.typography.labelMedium)
                        Text("this week", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.55f))
                    }
                    uiState.activityFeed.forEachIndexed { index, item ->
                        ActivityRow(item)
                        if (index != uiState.activityFeed.lastIndex) DashedDivider()
                    }
                }
            }

            if (uiState.recurringExpenses.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hardShadow(CardShape)
                        .border(BorderWidth, Ink, CardShape)
                        .background(MaterialTheme.colorScheme.surface, CardShape)
                        .padding(14.dp),
                ) {
                    Text("ON REPEAT", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        uiState.recurringExpenses.forEach { expense ->
                            RecurringExpenseRow(expense, onClick = onNavigateToMoney)
                        }
                    }
                }
            }
            Spacer(Modifier.height(110.dp))
        }
    }

    if (showNudgePicker) {
        NudgePickerSheet(
            onDismiss = { showNudgePicker = false },
            onSend = { message ->
                viewModel.sendNudge(message)
                showNudgePicker = false
            },
        )
    }
}

@Composable
private fun DatesCard(nextCountdown: SpecialDateDto?, nextMilestone: SpecialDateDto?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .clickableNoRipple(onClick)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("OUR DATES", style = MaterialTheme.typography.labelMedium)
            Text("see all →", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.55f))
        }
        if (nextCountdown == null && nextMilestone == null) {
            Spacer(Modifier.height(8.dp))
            Text("add a countdown or a day to remember ⏳💕", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        listOfNotNull(nextCountdown, nextMilestone).forEach { date ->
            Spacer(Modifier.height(10.dp))
            MiniDateRow(date)
        }
    }
}

@Composable
private fun MiniDateRow(date: SpecialDateDto) {
    val (number, unit) = dateBadge(date)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .border(2.dp, Ink, RoundedCornerShape(10.dp))
                .background(if (date.kind == "milestone") Pink else Teal, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(number, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = PinguCoduType.monoLabel)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(date.emoji, date.title).joinToString(" "),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(dateSubtitle(date), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NudgeCard(
    status: NudgeStatus,
    latestNudge: NudgeDto?,
    latestNudgeTimeLabel: String,
    onSend: () -> Unit,
    onLongPress: () -> Unit,
) {
    val nameMask = LocalNameMask.current
    val buttonShape = RoundedCornerShape(14.dp)
    val (label, color) = when (status) {
        NudgeStatus.Idle -> "send a nudge 💌" to Orange
        NudgeStatus.Sending -> "sending…" to Orange
        NudgeStatus.Sent -> "sent 💌" to Green
        is NudgeStatus.Failed -> status.message to Coral
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(YellowSoft, CardShape)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("THINKING OF YOU", style = MaterialTheme.typography.labelMedium)
            Text("hold for more", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.55f))
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(buttonShape, offsetX = 3.dp, offsetY = 3.dp)
                .border(BorderWidth, Ink, buttonShape)
                .background(color, buttonShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = status != NudgeStatus.Sending,
                    onClick = onSend,
                    onLongClick = onLongPress,
                )
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.headlineSmall, color = Ink)
        }
        if (latestNudge != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${nameMask.resolve(latestNudge.sender)}: \"${latestNudge.message}\" · $latestNudgeTimeLabel",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

private val NUDGE_PRESETS = listOf(
    "miss you 🥺",
    "sending a hug 🤗",
    "hungry? 🍕",
    "😘😘😘",
    "coffee? ☕",
    "goodnight 🌙",
    "call me when free 📞",
    "proud of you 💪",
)

@Composable
private fun NudgePickerSheet(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    NeoBottomSheet(title = "send a nudge", onDismiss = onDismiss) {
        Text(
            "tap one to send it right away, or write your own.",
            style = MaterialTheme.typography.bodySmall,
            color = DescriptionGrey,
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NUDGE_PRESETS.forEach { preset ->
                NeoChoiceChip(label = preset, selected = false, onClick = { onSend(preset) })
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("your own words")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = custom,
            onValueChange = { custom = it.take(NUDGE_MAX_LENGTH) },
            placeholder = "say something sweet",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text("${custom.length}/$NUDGE_MAX_LENGTH", style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
        Spacer(Modifier.height(16.dp))
        SubmitButton(label = "send it 💌", enabled = custom.isNotBlank()) { onSend(custom.trim()) }
    }
}

private const val NUDGE_MAX_LENGTH = 80

@Composable
private fun RecurringExpenseRow(expense: ExpenseDto, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.5.dp, Ink, RoundedCornerShape(11.dp))
            .background(PinkTint, RoundedCornerShape(11.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(expense.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(formatCents(expense.amountCents), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .border(1.5.dp, Ink, RoundedCornerShape(50))
                .background(Pink, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(expense.cadence ?: "recurring", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityFeedItem) {
    val (badgeColor, code) = when (item.source) {
        ActivitySource.MONEY -> Pink to "₹"
        ActivitySource.CYCLE -> Teal to "cy"
        ActivitySource.STASH -> if (item.badgeCode == "td") Color.White to "td" else YellowSoft to item.badgeCode
    }
    val displayText = if (item.author != null) {
        "${LocalNameMask.current.resolve(item.author)} shared \"${item.text}\""
    } else {
        item.text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(badgeColor, RoundedCornerShape(8.dp))
                .border(2.dp, Ink, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(code, style = PinguCoduType.monoLabel)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            displayText,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(item.timeLabel, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ScoreCard(
    net: Map<String, Long>,
    currentUsername: String?,
    openCount: Int,
    onSeeAll: () -> Unit,
    onSettleUp: () -> Unit,
) {
    val myNet = currentUsername?.let { net[it] } ?: 0
    val other = net.keys.firstOrNull { it != currentUsername }
    val otherLabel = LocalNameMask.current.resolve(other)

    val headline = when {
        myNet == 0L || other == null -> "all settled up"
        myNet > 0 -> "$otherLabel owes you"
        else -> "you owe $otherLabel"
    }
    val amount = if (myNet == 0L || other == null) null else formatCents(kotlin.math.abs(myNet))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(Pink, CardShape)
            .padding(16.dp),
    ) {
        Text("THE SCORE", style = MaterialTheme.typography.labelMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(headline, style = MaterialTheme.typography.displayMedium, color = Ink)
        if (amount != null) {
            Text(amount, style = MaterialTheme.typography.displayLarge, color = Ink)
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSeeAll,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink),
                border = androidx.compose.foundation.BorderStroke(BorderWidth, Ink),
                modifier = Modifier
                    .weight(1f)
                    .hardShadow(RoundedCornerShape(12.dp), offsetX = 3.dp, offsetY = 3.dp),
            ) {
                Text(if (openCount == 1) "see 1 open" else "see all $openCount open", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onSettleUp,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) {
                Text("settle up", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FeatureTeaserCard(
    label: String,
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 126.dp)
            .hardShadow(TileShape)
            .border(BorderWidth, Ink, TileShape)
            .background(color, TileShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatCents(cents: Long): String = "₹%.2f".format(cents / 100.0)
