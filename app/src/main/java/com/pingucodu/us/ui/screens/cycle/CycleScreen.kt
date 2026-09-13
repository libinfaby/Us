package com.pingucodu.us.ui.screens.cycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.CycleLogDto
import com.pingucodu.us.data.network.CycleStatusDto
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Teal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FLOWS = listOf("spotting", "light", "medium", "heavy")

@Composable
fun CycleScreen(modifier: Modifier = Modifier, viewModel: CycleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var logToDelete by remember { mutableStateOf<CycleLogDto?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (uiState.canLog) {
                FloatingActionButton(
                    onClick = { viewModel.openAddDialog() },
                    containerColor = Teal,
                    contentColor = Ink,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "log a day")
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            StatusCard(status = uiState.status)

            if (!uiState.canLog && uiState.status != null) {
                Text(
                    "only ${uiState.status?.trackedUser} can log entries here",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (uiState.errorMessage != null) {
                Text(
                    uiState.errorMessage!!,
                    color = Coral,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            when {
                uiState.isLoading && uiState.logs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Ink)
                    }
                }
                uiState.logs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("no logs yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.logs, key = { it.id }) { log ->
                            LogRow(
                                log = log,
                                onLongPress = { if (uiState.canLog) logToDelete = log },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        AddLogDialog(
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
private fun StatusCard(status: CycleStatusDto?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
            .background(Teal, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text("CYCLE", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        if (status?.currentDay != null) {
            Text("day ${status.currentDay}", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.height(2.dp))
            val nextText = status.predictedNextDate?.let { "${status.statusLabel} · next ${formatShortDate(it)}" }
                ?: status.statusLabel
            Text(nextText, style = MaterialTheme.typography.bodyMedium, color = Ink)
        } else {
            Text("not tracked yet", style = MaterialTheme.typography.titleLarge, color = Ink)
        }
    }
}

@Composable
private fun LogRow(log: CycleLogDto, onLongPress: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (logDate: String, flow: String, note: String?) -> Unit,
) {
    var logDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var flow by remember { mutableStateOf(FLOWS[1]) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
