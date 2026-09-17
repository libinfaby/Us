package com.pingucodu.us.ui.screens.stash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CardShape = RoundedCornerShape(14.dp)
private val SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
fun StashScreen(modifier: Modifier = Modifier, viewModel: StashViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var itemToDelete by remember { mutableStateOf<StashItemDto?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(4.dp))
            StatusFilterPills(selected = uiState.statusFilter, onSelect = viewModel::setStatusFilter)
            Spacer(Modifier.height(10.dp))
            TypeFilterRow(selected = uiState.typeFilter, onSelect = viewModel::setTypeFilter)
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
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Ink)
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("nothing here yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            StashItemCard(
                                item = item,
                                onToggle = { viewModel.toggleItem(item.id) },
                                onEdit = { viewModel.openEditDialog(item) },
                                onDelete = { itemToDelete = item },
                            )
                        }
                    }
                }
            }
        }

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

    if (uiState.showAddDialog) {
        StashItemFormDialog(
            item = uiState.editingItem,
            dialogError = uiState.dialogError,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissDialog,
            onSubmit = viewModel::submitItem,
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("delete item?") },
            text = { Text("delete \"${itemToDelete?.title}\"? this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(itemToDelete!!.id)
                    itemToDelete = null
                }) { Text("delete", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("cancel") } },
        )
    }
}

@Composable
private fun StatusFilterPills(selected: StashStatusFilter, onSelect: (StashStatusFilter) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StashStatusFilter.entries.forEach { filter ->
            Pill(label = filter.label, selected = filter == selected, onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun TypeFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(label = "all", selected = selected == null, onClick = { onSelect(null) })
        STASH_TYPES.forEach { type ->
            Pill(label = type, selected = selected == type, onClick = { onSelect(type) })
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(if (selected) YellowSoft else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

private fun typeBadgeColor(type: String): Color = when (type) {
    "movie", "todo" -> Pink
    "place" -> Teal
    "link" -> YellowSoft
    else -> Color.White
}

private fun relativeTime(sqliteDateTime: String): String = try {
    val then = LocalDateTime.parse(sqliteDateTime, SQLITE_DATETIME).toInstant(ZoneOffset.UTC)
    val minutes = Duration.between(then, Instant.now()).toMinutes()
    when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
} catch (e: Exception) {
    ""
}

@Composable
private fun StashItemCard(
    item: StashItemDto,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(CardShape)
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .border(2.dp, Ink, RoundedCornerShape(7.dp))
                    .background(typeBadgeColor(item.type), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
                Text(item.type, style = PinguCoduType.monoLabel)
            }
            Text("${item.author} · ${relativeTime(item.createdAt)}", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        if (!item.body.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(item.body, style = MaterialTheme.typography.bodySmall)
        }
        if (item.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .border(1.dp, Ink, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("#$tag", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        DashedDivider()
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedPillButton(label = if (item.status == "saved") "saved" else "done", onClick = onToggle)
            OutlinedPillButton(label = "edit", onClick = onEdit)
            OutlinedPillButton(label = "delete", onClick = onDelete)
        }
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

@Composable
private fun StashItemFormDialog(
    item: StashItemDto?,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (StashItemRequest) -> Unit,
) {
    var type by remember { mutableStateOf(item?.type ?: STASH_TYPES.first()) }
    var title by remember { mutableStateOf(item?.title ?: "") }
    var body by remember { mutableStateOf(item?.body ?: "") }
    var tagsText by remember { mutableStateOf(item?.tags?.joinToString(", ") ?: "") }

    val isValid = title.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(RoundedCornerShape(20.dp))
                .border(BorderWidth, Ink, RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(if (item == null) "add to stash" else "edit item", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("type", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                STASH_TYPES.forEach { t ->
                    Pill(label = t, selected = type == t, onClick = { type = t })
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("title", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
            )
            Spacer(Modifier.height(16.dp))

            Text("note (optional)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
            )
            Spacer(Modifier.height(16.dp))

            Text("tags, comma separated (optional)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
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
                    onClick = {
                        val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onSubmit(StashItemRequest(type = type, title = title.trim(), body = body.ifBlank { null }, tags = tags))
                    },
                    enabled = isValid && !isSubmitting,
                    modifier = Modifier.border(BorderWidth, Ink, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YellowSoft, contentColor = Ink),
                ) {
                    Text(if (item == null) "save" else "update")
                }
            }
        }
    }
}
