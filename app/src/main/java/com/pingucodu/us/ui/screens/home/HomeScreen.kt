package com.pingucodu.us.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow

private val CardShape = RoundedCornerShape(18.dp)
private val TileShape = RoundedCornerShape(16.dp)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToMoney: () -> Unit = {},
    onNavigateToCycle: () -> Unit = {},
    onNavigateToStash: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PinkTint)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        ScoreCard(
            net = uiState.net,
            currentUsername = uiState.username,
            openCount = uiState.openExpenseCount,
            onSeeAll = onNavigateToMoney,
            onSettleUp = onNavigateToMoney,
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
            item.text,
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

    val headline = when {
        myNet == 0L || other == null -> "all settled up"
        myNet > 0 -> "$other owes you"
        else -> "you owe $other"
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
