package com.pingucodu.us.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The flat, offset "sticker" shadow used throughout the design prototype
 * (CSS `box-shadow: 5px 5px 0px 0px #111` - no blur, no spread, just a solid
 * duplicate of the shape nudged down-right). Draw it before any background/
 * border modifiers so the component's own fill covers the shadow everywhere
 * except the peeking offset edge.
 */
fun Modifier.hardShadow(
    shape: Shape,
    color: Color = Ink,
    offsetX: Dp = 5.dp,
    offsetY: Dp = 5.dp,
): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(offsetX.toPx(), offsetY.toPx()) {
        drawOutline(outline, color = color)
    }
}

val QuickChipSpacing = Arrangement.spacedBy(8.dp)

/** A dashed outline for a shape, used for "predicted" calendar days where CSS just does `border-style: dashed`. */
fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    shape: Shape,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Generic -> outline.path
    }
    val strokeWidthPx = width.toPx()
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
        ),
    )
}

/** The `border-top: 2px dashed rgba(0,0,0,.18)` row separator used between list rows in the design. */
@Composable
fun DashedDivider(
    modifier: Modifier = Modifier,
    color: Color = Ink.copy(alpha = 0.18f),
    dashLength: Dp = 10.dp,
    gapLength: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
                )
            }
    )
}

/** A pill toggle: pink track when on, black thumb, matching the "nudges" switches in the design. */
@Composable
fun NeoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 28.dp)
            .background(if (checked) Pink else Color.White, CircleShape)
            .border(2.dp, Ink, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(Ink, CircleShape))
    }
}

/** Rounded-square avatar shape used for the header avatar and activity-row source badges. */
val AvatarShape = RoundedCornerShape(12.dp)

/**
 * The neo-brutalist confirmation dialog: a colored "heads up" banner with an exclamation
 * badge and a short tag (e.g. "no undo"), then a bold question, a description, and a
 * keep-it/confirm button pair - replaces the plain Material AlertDialog for actions worth
 * pausing on.
 */
@Composable
fun NeoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "keep it",
    badgeLabel: String = "no undo",
    accentColor: Color = Pink,
) {
    val cardShape = RoundedCornerShape(28.dp)
    val buttonShape = RoundedCornerShape(16.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .hardShadow(cardShape, offsetX = 5.dp, offsetY = 5.dp)
                .border(4.dp, Ink, cardShape)
                .background(PinkTint, cardShape)
                .clip(cardShape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(BorderWidth, Ink, RoundedCornerShape(14.dp))
                        .background(PinkTint, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", style = MaterialTheme.typography.headlineMedium, color = Ink)
                }
                Text(badgeLabel.uppercase(), style = PinguCoduType.monoLabel, color = Ink)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).background(Ink))

            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.displayMedium, color = Ink)
                Spacer(Modifier.height(14.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, color = DescriptionGrey)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeoDialogButton(
                        label = dismissLabel,
                        background = Color.White,
                        shadow = false,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    NeoDialogButton(
                        label = confirmLabel,
                        background = accentColor,
                        shadow = true,
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun NeoDialogButton(
    label: String,
    background: Color,
    shadow: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .let { if (shadow) it.hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp) else it }
            .border(BorderWidth, Ink, shape)
            .background(background, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}
