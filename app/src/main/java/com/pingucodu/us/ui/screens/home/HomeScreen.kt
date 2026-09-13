package com.pingucodu.us.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.Yellow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToMoney: () -> Unit = {},
    onNavigateToCycle: () -> Unit = {},
    onNavigateToStash: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val username = uiState.username ?: ""

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Pink, CircleShape)
                    .border(BorderWidth, Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(username.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("hey $username", style = MaterialTheme.typography.titleLarge)
                Text(todayLabel(), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.logout() }) { Text("exit") }
        }
        Spacer(Modifier.height(20.dp))

        Text("THE SCORE", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        ScoreCard(
            net = uiState.net,
            currentUsername = uiState.username,
            openCount = uiState.openExpenseCount,
            onSeeAll = onNavigateToMoney,
            onSettleUp = onNavigateToMoney,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTeaserCard(
                label = "CYCLE",
                title = uiState.cycleStatus?.currentDay?.let { "day $it" } ?: "not tracked yet",
                subtitle = uiState.cycleStatus?.takeIf { it.currentDay != null }?.statusLabel,
                color = Teal,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToCycle,
            )
            FeatureTeaserCard(
                label = "STASH",
                title = "not tracked yet",
                color = Yellow,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToStash,
            )
        }
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

    val text = when {
        myNet == 0L || other == null -> "all settled up"
        myNet > 0 -> "$other owes you ${formatCents(myNet)}"
        else -> "you owe $other ${formatCents(-myNet)}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
            .background(Pink, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSeeAll,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                border = BorderStroke(BorderWidth, Ink),
            ) {
                Text(if (openCount == 1) "see 1 open" else "see all $openCount open")
            }
            Button(
                onClick = onSettleUp,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Pink),
            ) {
                Text("settle up")
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
            .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
            .background(color, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatCents(cents: Long): String = "₹%.2f".format(cents / 100.0)

private fun todayLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)).lowercase()
