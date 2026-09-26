package com.pingucodu.us.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PlaceholderGrey
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.hardShadow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

// Form chrome shared by the add/edit bottom sheets across screens (Stash, Dates, Goals, Home).

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
fun Modifier.clickableNoRipple(enabled: Boolean, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun SheetCloseButton(onClick: () -> Unit) {
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
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = PinguCoduType.monoLabel, color = Ink)
}

@Composable
fun NeoChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, selectedColor: Color = Teal) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .border(2.dp, Ink, shape)
            .background(if (selected) selectedColor else Color.White, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
fun NeoField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    backgroundColor: Color = Color.White,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(3.dp, Ink, shape)
            .background(backgroundColor, shape)
            .padding(contentPadding),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle, color = PlaceholderGrey)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = Ink),
            cursorBrush = SolidColor(Ink),
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SubmitButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(shape, offsetX = 4.dp, offsetY = 4.dp)
            .border(BorderWidth, Ink, shape)
            .background(Ink, shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineLarge, color = Color.White)
    }
}

/** Shared bottom-sheet chrome for add/edit forms: a dimmed scrim, a slide-up
 * PinkTint sheet with rounded top corners, and a close ("×") button next to the title. */
@Composable
fun NeoBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Dialog() opens its own window, which resets LocalDensity instead of inheriting the
    // app's global font-scale override from UsTheme - re-provide the correct one (captured
    // here, before the Dialog, where the override is still in effect) so this sheet's text
    // actually matches the rest of the app instead of quietly rendering ~25% smaller.
    val outerDensity = LocalDensity.current
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
                        Text(title, style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
                        SheetCloseButton(onClick = onDismiss)
                    }
                    Spacer(Modifier.height(16.dp))
                    content()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        }
    }
}

@Composable
fun DateField(label: String, value: String?, onClick: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(BorderWidth, Ink, shape)
            .background(Color.White, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value?.let { runCatching { shortDateLabel(LocalDate.parse(it)) }.getOrNull() } ?: label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = if (value != null) Ink else PlaceholderGrey,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text("×", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.clickableNoRipple(onClear))
        }
    }
}

/** The neo-brutalist calendar picker. Money's expense form keeps its own copy, which only
 * allows past dates; this one allows any date. */
@Composable
fun NeoDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var viewMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }

    val outerDensity = LocalDensity.current
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
                        "when is it",
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
                    label = "tomorrow",
                    selected = selectedDate == today.plusDays(1),
                    onClick = { selectedDate = today.plusDays(1); viewMonth = YearMonth.from(selectedDate) },
                )
                QuickPickChip(
                    label = "+1 week",
                    selected = selectedDate == today.plusWeeks(1),
                    onClick = { selectedDate = today.plusWeeks(1); viewMonth = YearMonth.from(selectedDate) },
                )
            }
            Spacer(Modifier.height(26.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                NavArrowButton(symbol = "‹", onClick = { viewMonth = viewMonth.minusMonths(1) })
                Text(
                    "${viewMonth.month.getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH).lowercase()} ${viewMonth.year}",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                NavArrowButton(symbol = "›", onClick = { viewMonth = viewMonth.plusMonths(1) })
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

private val WeekdayLetters = listOf("s", "m", "t", "w", "t", "f", "s")

private fun shortDateLabel(date: LocalDate): String =
    "${date.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).lowercase()} ${date.dayOfMonth}"

private fun friendlyDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    else -> shortDateLabel(date)
}

@Composable
private fun RowScope.QuickPickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .border(BorderWidth, Ink, RoundedCornerShape(50))
            .background(if (selected) Pink else Color.White, RoundedCornerShape(50))
            .clickableNoRipple(onClick)
            .padding(horizontal = 6.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, textAlign = TextAlign.Center)
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
private fun DayCell(date: LocalDate, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
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
