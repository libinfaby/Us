package com.pingucodu.us.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
