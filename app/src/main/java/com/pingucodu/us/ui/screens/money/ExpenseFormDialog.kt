package com.pingucodu.us.ui.screens.money

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.ExpenseRequest
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.PlaceholderGrey
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.FontScaleLevel
import com.pingucodu.us.ui.theme.dashedBorder
import com.pingucodu.us.ui.theme.hardShadow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToLong

private val CADENCES = listOf("weekly", "monthly")
private val FieldShape = RoundedCornerShape(16.dp)
private val AmountPlaceholderColor = Color(0xFF75244B)
private val WeekdayLetters = listOf("s", "m", "t", "w", "t", "f", "s")

private fun formatCents(cents: Long): String = "₹%.2f".format(cents / 100.0)

private fun shortDateLabel(date: LocalDate): String =
    "${date.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).lowercase()} ${date.dayOfMonth}"

private fun friendlyDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    else -> shortDateLabel(date)
}

// This dialog's text sizes were hand-tuned to look right at the app's "large" font-scale
// tier. Re-anchor the ambient scale so "large" maps to 1x here (matching that tuning, then
// nudged down slightly per feedback) instead of stacking the app's 1.15x large multiplier
// on top of sizes that already assumed it.
private val DialogScaleAnchor = FontScaleLevel.LARGE.scale * 1.12f

@Composable
private fun rememberDialogDensity(): Density {
    val ambient = LocalDensity.current
    return remember(ambient) {
        Density(density = ambient.density, fontScale = ambient.fontScale / DialogScaleAnchor)
    }
}

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
    val otherUsername = if (currentUsername == "pingu") "codu" else "pingu"
    var customUserAmount by remember {
        mutableStateOf(expense?.split?.get(currentUsername)?.let { (it / 100.0).toString() } ?: "")
    }
    var expenseDate by remember { mutableStateOf(expense?.expenseDate ?: LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isRecurring by remember { mutableStateOf(expense?.isRecurring ?: false) }
    var cadence by remember { mutableStateOf(expense?.cadence ?: "monthly") }

    val amountCents = amountText.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    val customUserCents = customUserAmount.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    val customOtherCents = if (amountCents != null && customUserCents != null) {
        (amountCents - customUserCents).coerceAtLeast(0)
    } else {
        null
    }
    val customSumMatches = splitType != "custom" ||
        (customUserCents != null && amountCents != null && customUserCents in 0..amountCents)

    val isValid = title.isNotBlank() &&
        amountCents != null && amountCents > 0 &&
        expenseDate.isNotBlank() &&
        customSumMatches

    fun submit() {
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
            split = if (splitType == "custom" && customUserCents != null && customOtherCents != null) {
                mapOf(currentUsername to customUserCents, otherUsername to customOtherCents)
            } else {
                null
            },
            isRecurring = isRecurring,
            cadence = if (isRecurring) cadence else null,
        )
        onSubmit(request)
    }

    val outerDensity = rememberDialogDensity()
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

        CompositionLocalProvider(LocalDensity provides outerDensity) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.45f))
                    .clickableNoRipple(onDismiss),
            )

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(240),
                    initialOffsetY = { it },
                ) + fadeIn(tween(240)),
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
                            if (expense == null) "new expense" else "edit expense",
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.weight(1f),
                        )
                        SheetCloseButton(onClick = onDismiss)
                    }
                    Spacer(Modifier.height(22.dp))

                    NeoField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "what was it?",
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))

                    NeoField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        placeholder = "0",
                        placeholderColor = AmountPlaceholderColor,
                        backgroundColor = Pink,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp, color = Ink),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        leading = { Text("₹", style = MaterialTheme.typography.displayMedium) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))

                    NeoField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "note — what's this about?",
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))

                    SectionLabel("hangout")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        var hangoutMenuExpanded by remember { mutableStateOf(false) }
                        val selectedName = hangouts.firstOrNull { it.id == hangoutId }?.name ?: "no hangout"
                        Box(modifier = Modifier.weight(1f)) {
                            val dropdownShape = RoundedCornerShape(14.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hardShadow(dropdownShape, offsetX = 3.dp, offsetY = 3.dp)
                                    .border(BorderWidth, Ink, dropdownShape)
                                    .background(Color.White, dropdownShape)
                                    .clickableNoRipple { hangoutMenuExpanded = true }
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    selectedName,
                                    style = MaterialTheme.typography.titleMedium.copy(color = Ink),
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                ChevronArrow(pointingUp = hangoutMenuExpanded)
                            }
                            DropdownMenu(expanded = hangoutMenuExpanded, onDismissRequest = { hangoutMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("no hangout", style = MaterialTheme.typography.titleMedium) },
                                    onClick = { hangoutId = null; hangoutMenuExpanded = false },
                                )
                                hangouts.forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(h.name, style = MaterialTheme.typography.titleMedium) },
                                        onClick = { hangoutId = h.id; hangoutMenuExpanded = false },
                                    )
                                }
                            }
                        }
                        NeoPickButton(
                            label = if (showNewHangoutInput) "close" else "+ new",
                            selected = showNewHangoutInput,
                            accentColor = Teal,
                            onClick = {
                                showNewHangoutInput = !showNewHangoutInput
                                newHangoutName = ""
                            },
                        )
                    }
                    if (showNewHangoutInput) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeoField(
                                value = newHangoutName,
                                onValueChange = { newHangoutName = it },
                                placeholder = "e.g. munnar trip, sunday meet",
                                textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            NeoPickButton(
                                label = "create",
                                selected = true,
                                accentColor = Ink,
                                textColor = Color.White,
                                onClick = {
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
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    SectionLabel("paid by")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeoPickButton(
                            label = if (currentUsername == "pingu") "pingu (you)" else "pingu",
                            selected = paidBy == "pingu",
                            modifier = Modifier.weight(1f),
                            onClick = { paidBy = "pingu" },
                        )
                        NeoPickButton(
                            label = if (currentUsername == "codu") "codu (you)" else "codu",
                            selected = paidBy == "codu",
                            modifier = Modifier.weight(1f),
                            onClick = { paidBy = "codu" },
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    SectionLabel("split")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeoPickButton(
                            label = "50 / 50",
                            selected = splitType == "equal",
                            accentColor = Teal,
                            modifier = Modifier.weight(1f),
                            onClick = { splitType = "equal" },
                        )
                        NeoPickButton(
                            label = "custom",
                            selected = splitType == "custom",
                            accentColor = Teal,
                            modifier = Modifier.weight(1f),
                            onClick = { splitType = "custom" },
                        )
                    }
                    if (splitType == "custom") {
                        Spacer(Modifier.height(10.dp))
                        val customCardShape = RoundedCornerShape(14.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hardShadow(customCardShape, offsetX = 3.dp, offsetY = 3.dp)
                                .border(BorderWidth, Ink, customCardShape)
                                .background(Color.White, customCardShape)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "$currentUsername pays",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                NeoField(
                                    value = customUserAmount,
                                    onValueChange = { customUserAmount = it },
                                    placeholder = "0",
                                    placeholderColor = AmountPlaceholderColor,
                                    backgroundColor = PinkTint,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = PinguCoduType.amount.copy(color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    modifier = Modifier.width(120.dp),
                                    shadow = false,
                                    borderWidth = 2.dp,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "$otherUsername pays",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .dashedBorder(2.dp, Ink, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Text(
                                        formatCents(customOtherCents ?: 0),
                                        style = PinguCoduType.amount.copy(color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box {
                            NeoField(
                                value = expenseDate,
                                onValueChange = {},
                                enabled = false,
                                textStyle = PinguCoduType.amount.copy(color = Ink),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                modifier = Modifier.width(168.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickableNoRipple { showDatePicker = true },
                            )
                        }
                        NeoField(
                            value = place,
                            onValueChange = { place = it },
                            placeholder = "place (optional)",
                            textStyle = PinguCoduType.amount.copy(color = Ink),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hardShadow(FieldShape, offsetX = 3.dp, offsetY = 3.dp)
                            .border(BorderWidth, Ink, FieldShape)
                            .background(if (isRecurring) Pink else Color.White, FieldShape)
                            .clickableNoRipple { isRecurring = !isRecurring }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .border(2.5.dp, Ink, RoundedCornerShape(6.dp))
                                .background(Color.White, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isRecurring) {
                                Box(Modifier.size(14.dp).background(Ink, CircleShape))
                            }
                        }
                        Text("repeats every", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (isRecurring) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CADENCES.forEach { c ->
                                    NeoPickButton(label = c, selected = cadence == c, accentColor = Teal, onClick = { cadence = c })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))

                    if (dialogError != null) {
                        Text(dialogError, color = Coral, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hardShadow(RoundedCornerShape(16.dp), offsetX = 4.dp, offsetY = 4.dp)
                            .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
                            .background(Ink, RoundedCornerShape(16.dp))
                            .clickableNoRipple(enabled = isValid && !isSubmitting) { submit() }
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (expense == null) "add expense" else "save changes",
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

    if (showDatePicker) {
        NeoDatePickerDialog(
            initialDate = runCatching { LocalDate.parse(expenseDate) }.getOrDefault(LocalDate.now()),
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                expenseDate = picked.toString()
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = PinguCoduType.monoLabel.copy(fontSize = 13.sp),
        color = Ink,
        modifier = modifier,
    )
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
private fun NeoPickButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = Pink,
    textColor: Color = Ink,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .let { if (selected) it.hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp) else it }
            .border(BorderWidth, Ink, shape)
            .background(if (selected) accentColor else Color.White, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) textColor else Ink)
    }
}

@Composable
private fun NeoField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    backgroundColor: Color = Color.White,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    enabled: Boolean = true,
    minLines: Int = 1,
    shadow: Boolean = true,
    borderWidth: Dp = BorderWidth,
    placeholderColor: Color = PlaceholderGrey,
    leading: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .let { if (shadow) it.hardShadow(FieldShape, offsetX = 3.dp, offsetY = 3.dp) else it }
            .border(borderWidth, Ink, FieldShape)
            .background(backgroundColor, FieldShape)
            .padding(contentPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle, color = placeholderColor)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(Ink),
                    keyboardOptions = keyboardOptions,
                    minLines = minLines,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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

@Composable
private fun NeoDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var viewMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }

    val outerDensity = rememberDialogDensity()
    Dialog(onDismissRequest = onDismiss) {
      CompositionLocalProvider(LocalDensity provides outerDensity) {
        val cardShape = RoundedCornerShape(30.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(cardShape, offsetX = 5.dp, offsetY = 5.dp)
                .border(4.dp, Ink, cardShape)
                .background(PinkTint, cardShape)
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "when was it",
                        style = PinguCoduType.monoLabel.copy(fontSize = 13.sp),
                        color = Ink.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(friendlyDateLabel(selectedDate, today), style = MaterialTheme.typography.displayLarge)
                }
                Column(
                    modifier = Modifier
                        .hardShadow(RoundedCornerShape(18.dp), offsetX = 3.dp, offsetY = 3.dp)
                        .border(BorderWidth, Ink, RoundedCornerShape(18.dp))
                        .background(Pink, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        selectedDate.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(),
                        style = PinguCoduType.monoLabel.copy(fontSize = 12.sp),
                    )
                    Text(selectedDate.dayOfMonth.toString(), style = MaterialTheme.typography.headlineLarge)
                }
            }
            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickPickChip(
                    label = "today",
                    selected = selectedDate == today,
                    onClick = { selectedDate = today; viewMonth = YearMonth.from(today) },
                )
                QuickPickChip(
                    label = "yesterday",
                    selected = selectedDate == today.minusDays(1),
                    onClick = { selectedDate = today.minusDays(1); viewMonth = YearMonth.from(selectedDate) },
                )
                QuickPickChip(
                    label = "2 days ago",
                    selected = selectedDate == today.minusDays(2),
                    onClick = { selectedDate = today.minusDays(2); viewMonth = YearMonth.from(selectedDate) },
                )
            }
            Spacer(Modifier.height(26.dp))

            val currentMonth = YearMonth.from(today)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavArrowButton(symbol = "‹", onClick = { viewMonth = viewMonth.minusMonths(1) })
                Text(
                    "${viewMonth.month.getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH).lowercase()} ${viewMonth.year}",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                NavArrowButton(
                    symbol = "›",
                    enabled = viewMonth.isBefore(currentMonth),
                    onClick = { viewMonth = viewMonth.plusMonths(1) },
                )
            }
            Spacer(Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                WeekdayLetters.forEach { letter ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(letter, style = PinguCoduType.monoLabel.copy(fontSize = 13.sp), color = Ink.copy(alpha = 0.4f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val firstOfMonth = viewMonth.atDay(1)
            val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
            val daysInMonth = viewMonth.lengthOfMonth()
            val cells: List<LocalDate?> = buildList {
                repeat(leadingBlanks) { add(null) }
                for (day in 1..daysInMonth) add(viewMonth.atDay(day))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).aspectRatio(1f)) {
                                if (date != null) {
                                    DayCell(
                                        date = date,
                                        selected = date == selectedDate,
                                        enabled = !date.isAfter(today),
                                        onClick = { selectedDate = date },
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .clickableNoRipple(onDismiss)
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("cancel", style = MaterialTheme.typography.titleLarge)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
                        .background(Ink, RoundedCornerShape(16.dp))
                        .clickableNoRipple { onConfirm(selectedDate) }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "use ${shortDateLabel(selectedDate)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun RowScope.QuickPickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .border(BorderWidth, Ink, RoundedCornerShape(50))
            .background(if (selected) Pink else Color.White, RoundedCornerShape(50))
            .clickableNoRipple(onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun NavArrowButton(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .border(BorderWidth, if (enabled) Ink else Ink.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = if (enabled) Ink else Ink.copy(alpha = 0.3f))
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .let { if (selected) it.hardShadow(shape, offsetX = 2.dp, offsetY = 2.dp) else it }
            .border(if (enabled) BorderWidth else 1.5.dp, if (enabled) Ink else Ink.copy(alpha = 0.25f), shape)
            .background(if (selected) Pink else Color.White.copy(alpha = if (enabled) 1f else 0.5f), shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Ink else Ink.copy(alpha = 0.3f),
        )
    }
}

/** A broad, hand-drawn-looking V (or ^ when [pointingUp]) - a dropdown chevron, not a thin Material icon. */
@Composable
private fun ChevronArrow(pointingUp: Boolean, modifier: Modifier = Modifier, color: Color = Ink) {
    Canvas(modifier = modifier.size(width = 13.dp, height = 8.dp)) {
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            if (pointingUp) {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height)
                lineTo(size.width, 0f)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
