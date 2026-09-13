package com.pingucodu.us.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.ExpenseRequest
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToLong

private val CADENCES = listOf("weekly", "monthly")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormDialog(
    expense: ExpenseDto?,
    hangouts: List<HangoutDto>,
    currentUsername: String,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onCreateHangout: suspend (String) -> HangoutDto?,
    onSubmit: (ExpenseRequest) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(expense?.title ?: "") }
    var amountText by remember { mutableStateOf(expense?.let { (it.amountCents / 100.0).toString() } ?: "") }
    var note by remember { mutableStateOf(expense?.subtitle ?: "") }
    var place by remember { mutableStateOf(expense?.location ?: "") }
    var category by remember { mutableStateOf(expense?.category ?: "") }
    var hangoutId by remember { mutableStateOf(expense?.hangoutId) }
    var showNewHangoutInput by remember { mutableStateOf(false) }
    var newHangoutName by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf(expense?.paidBy ?: currentUsername) }
    var splitType by remember { mutableStateOf(expense?.splitType ?: "equal") }
    var customPingu by remember { mutableStateOf(expense?.split?.get("pingu")?.let { (it / 100.0).toString() } ?: "") }
    var customCodu by remember { mutableStateOf(expense?.split?.get("codu")?.let { (it / 100.0).toString() } ?: "") }
    var expenseDate by remember { mutableStateOf(expense?.expenseDate ?: LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isRecurring by remember { mutableStateOf(expense?.isRecurring ?: false) }
    var cadence by remember { mutableStateOf(expense?.cadence ?: "monthly") }

    val amountCents = amountText.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    val customPinguCents = customPingu.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    val customCoduCents = customCodu.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    val customSumMatches = splitType != "custom" ||
        (customPinguCents != null && customCoduCents != null && customPinguCents + customCoduCents == amountCents)

    val isValid = title.isNotBlank() &&
        amountCents != null && amountCents > 0 &&
        expenseDate.isNotBlank() &&
        customSumMatches

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.5.dp, Ink, RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(if (expense == null) "new expense" else "edit expense", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            LabeledField("title") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("what was it?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
                )
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                placeholder = { Text("0") },
                leadingIcon = { Text("₹", style = MaterialTheme.typography.titleMedium) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Ink,
                    focusedBorderColor = Ink,
                    unfocusedContainerColor = Pink,
                    focusedContainerColor = Pink,
                ),
            )
            Spacer(Modifier.height(14.dp))

            LabeledField("note") {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("note — what's this about?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
                )
            }
            Spacer(Modifier.height(14.dp))

            LabeledField("category (optional)") {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    placeholder = { Text("household, online, ...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
                )
            }
            Spacer(Modifier.height(14.dp))

            LabeledField("hangout") {
                if (showNewHangoutInput) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newHangoutName,
                            onValueChange = { newHangoutName = it },
                            placeholder = { Text("hangout name") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val name = newHangoutName.trim()
                            if (name.isNotEmpty()) {
                                scope.launch {
                                    val created = onCreateHangout(name)
                                    if (created != null) {
                                        hangoutId = created.id
                                        showNewHangoutInput = false
                                        newHangoutName = ""
                                    }
                                }
                            }
                        }) { Text("save") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val selectedName = hangouts.firstOrNull { it.id == hangoutId }?.name ?: "no hangout"
                        PillToggle(
                            label = selectedName,
                            selected = true,
                            modifier = Modifier.weight(1f),
                            onClick = { hangoutId = null },
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showNewHangoutInput = true }) { Text("+ new") }
                    }
                    if (hangouts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScrollRow(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            hangouts.forEach { h ->
                                PillToggle(label = h.name, selected = hangoutId == h.id, onClick = { hangoutId = h.id })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            LabeledField("paid by") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillToggle(
                        label = if (currentUsername == "pingu") "pingu (you)" else "pingu",
                        selected = paidBy == "pingu",
                        modifier = Modifier.weight(1f),
                        onClick = { paidBy = "pingu" },
                    )
                    PillToggle(
                        label = if (currentUsername == "codu") "codu (you)" else "codu",
                        selected = paidBy == "codu",
                        modifier = Modifier.weight(1f),
                        onClick = { paidBy = "codu" },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            LabeledField("split") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillToggle(
                            label = "50 / 50",
                            selected = splitType == "equal",
                            modifier = Modifier.weight(1f),
                            onClick = { splitType = "equal" },
                        )
                        PillToggle(
                            label = "custom",
                            selected = splitType == "custom",
                            modifier = Modifier.weight(1f),
                            onClick = { splitType = "custom" },
                        )
                    }
                    if (splitType == "custom") {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customPingu,
                                onValueChange = { customPingu = it },
                                placeholder = { Text("pingu ₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
                            )
                            OutlinedTextField(
                                value = customCodu,
                                onValueChange = { customCodu = it },
                                placeholder = { Text("codu ₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
                            )
                        }
                        if (!customSumMatches) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "splits must add up to the total amount",
                                color = Coral,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(label = "date", modifier = Modifier.weight(1f)) {
                    Box {
                        OutlinedTextField(
                            value = expenseDate,
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
                }
                LabeledField(label = "place (optional)", modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = place,
                        onValueChange = { place = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isRecurring,
                    onCheckedChange = { isRecurring = it },
                    colors = CheckboxDefaults.colors(checkedColor = Pink, checkmarkColor = Ink),
                )
                Text("repeats every", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                if (isRecurring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CADENCES.forEach { c ->
                            PillToggle(label = c, selected = cadence == c, onClick = { cadence = c })
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            if (dialogError != null) {
                Text(dialogError, color = Coral, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val request = ExpenseRequest(
                            title = title.trim(),
                            amountCents = amountCents,
                            expenseDate = expenseDate,
                            subtitle = note.ifBlank { null },
                            location = place.ifBlank { null },
                            hangoutId = hangoutId,
                            category = category.ifBlank { null },
                            paidBy = paidBy,
                            splitType = splitType,
                            split = if (splitType == "custom" && customPinguCents != null && customCoduCents != null) {
                                mapOf("pingu" to customPinguCents, "codu" to customCoduCents)
                            } else {
                                null
                            },
                            isRecurring = isRecurring,
                            cadence = if (isRecurring) cadence else null,
                        )
                        onSubmit(request)
                    },
                    enabled = isValid && !isSubmitting,
                    modifier = Modifier.border(2.5.dp, Ink, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Ink),
                ) {
                    Text(if (expense == null) "add" else "save")
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(expenseDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        expenseDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
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
private fun LabeledField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun PillToggle(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(if (selected) Pink else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
private fun Modifier.horizontalScrollRow(): Modifier = this.horizontalScroll(rememberScrollState())
