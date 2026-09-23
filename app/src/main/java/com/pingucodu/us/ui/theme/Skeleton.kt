package com.pingucodu.us.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton placeholders that mirror each real card's own layout: same outer shape, size,
 * padding, and the position/size of each label so a card "resolves" into place without
 * jumping, instead of a single generic shimmering blob. Every screen's loading state should
 * use the matching `Skeleton*Card` below rather than a bare spinner or [SkeletonBar].
 *
 * Resting fill for a shimmer line - a shade deeper than [PinkTint] so it reads against both
 * PinkTint and white/surface card backgrounds. The moving highlight is white.
 */
val SkeletonBarColor = Color(0xFFFFC9DF)

@Composable
private fun rememberShimmerTranslate(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -400f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    return translate
}

/** A single shimmering line/chip - the building block for every skeleton card's content. */
@Composable
fun SkeletonBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val translate = rememberShimmerTranslate()
    Box(
        modifier = modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val brush = Brush.linearGradient(
                colors = listOf(SkeletonBarColor, Color.White.copy(alpha = 0.95f), SkeletonBarColor),
                start = Offset(translate - size.width, 0f),
                end = Offset(translate, size.height),
            )
            onDrawBehind {
                drawOutline(outline, color = SkeletonBarColor)
                drawOutline(outline, brush = brush)
            }
        },
    )
}

/** The static neo-brutalist card chrome (hard shadow, ink border, light-pink fill) every skeleton card sits in. */
@Composable
private fun SkeletonFrame(
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .border(BorderWidth, Ink, shape)
            .background(PinkTint, shape),
        content = content,
    )
}

// ---------- Home ----------

private val HomeCardShape = RoundedCornerShape(18.dp)
private val HomeTileShape = RoundedCornerShape(16.dp)

/** Mirrors Home's ScoreCard: label, headline, amount, then a two-button row. */
@Composable
fun SkeletonScoreCard(modifier: Modifier = Modifier) {
    SkeletonFrame(modifier = modifier.fillMaxWidth(), shape = HomeCardShape) {
        Box(Modifier.padding(16.dp)) {
            Column {
                SkeletonBar(Modifier.width(90.dp).height(10.dp))
                Box(Modifier.height(10.dp))
                SkeletonBar(Modifier.width(170.dp).height(20.dp))
                Box(Modifier.height(8.dp))
                SkeletonBar(Modifier.width(130.dp).height(26.dp))
                Box(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBar(Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp))
                    SkeletonBar(Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp))
                }
            }
        }
    }
}

/** Mirrors Home's two FeatureTeaserCards (CYCLE/STASH): label, title, subtitle. */
@Composable
fun SkeletonFeatureTeaserRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(126.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonFeatureTeaserTile(Modifier.weight(1f).fillMaxHeight())
        SkeletonFeatureTeaserTile(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SkeletonFeatureTeaserTile(modifier: Modifier) {
    SkeletonFrame(modifier = modifier, shape = HomeTileShape) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SkeletonBar(Modifier.width(56.dp).height(10.dp))
            SkeletonBar(Modifier.width(70.dp).height(18.dp))
            SkeletonBar(Modifier.width(90.dp).height(11.dp))
        }
    }
}

/** Mirrors Home's "LATEST" activity feed card: header row, then [rows] badge+text rows. */
@Composable
fun SkeletonActivityCard(modifier: Modifier = Modifier, rows: Int = 3) {
    SkeletonFrame(modifier = modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SkeletonBar(Modifier.width(50.dp).height(10.dp))
                SkeletonBar(Modifier.width(60.dp).height(10.dp))
            }
            repeat(rows) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBar(Modifier.size(34.dp), shape = RoundedCornerShape(8.dp))
                    Box(Modifier.width(10.dp))
                    SkeletonBar(Modifier.weight(1f).height(14.dp))
                    Box(Modifier.width(8.dp))
                    SkeletonBar(Modifier.width(26.dp).height(10.dp))
                }
                if (index != rows - 1) DashedDivider(color = Ink.copy(alpha = 0.1f))
            }
        }
    }
}

/** Mirrors Home's "ON REPEAT" recurring-expenses card: label, then [rows] title+amount+badge rows. */
@Composable
fun SkeletonRecurringCard(modifier: Modifier = Modifier, rows: Int = 2) {
    SkeletonFrame(modifier = modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp)) {
            SkeletonBar(Modifier.width(80.dp).height(10.dp))
            Box(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(rows) {
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBar(Modifier.weight(1f).height(14.dp))
                        Box(Modifier.width(8.dp))
                        SkeletonBar(Modifier.width(50.dp).height(14.dp))
                        Box(Modifier.width(8.dp))
                        SkeletonBar(Modifier.width(64.dp).height(20.dp), shape = RoundedCornerShape(50))
                    }
                }
            }
        }
    }
}

// ---------- Money ----------

private val ExpenseCardShape = RoundedCornerShape(18.dp)

/** Mirrors MoneyScreen's ExpenseCard: stripe, title/amount row, tag chips, split+action row. */
@Composable
fun SkeletonExpenseCard(modifier: Modifier = Modifier) {
    SkeletonFrame(modifier = modifier.fillMaxWidth(), shape = ExpenseCardShape) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(SkeletonBarColor))
        Box(Modifier.fillMaxWidth().height(BorderWidth).background(Ink))
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    SkeletonBar(Modifier.width(130.dp).height(18.dp))
                    Box(Modifier.height(6.dp))
                    SkeletonBar(Modifier.width(90.dp).height(11.dp))
                }
                SkeletonBar(Modifier.width(64.dp).height(22.dp))
            }
            Box(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBar(Modifier.width(60.dp).height(22.dp), shape = RoundedCornerShape(6.dp))
                SkeletonBar(Modifier.width(72.dp).height(22.dp), shape = RoundedCornerShape(6.dp))
                SkeletonBar(Modifier.width(50.dp).height(22.dp), shape = RoundedCornerShape(6.dp))
            }
            DashedDivider(modifier = Modifier.padding(top = 12.dp, bottom = 11.dp), color = Ink.copy(alpha = 0.1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SkeletonBar(Modifier.weight(1f).height(12.dp))
                SkeletonBar(Modifier.width(70.dp).height(28.dp), shape = RoundedCornerShape(50))
                SkeletonBar(Modifier.width(48.dp).height(28.dp), shape = RoundedCornerShape(50))
            }
        }
    }
}

// ---------- Cycle ----------

private val CycleHeaderShape = RoundedCornerShape(18.dp)

/** Mirrors CycleScreen's CycleHeaderCard: label, headline, subtitle. */
@Composable
fun SkeletonCycleHeaderCard(modifier: Modifier = Modifier) {
    SkeletonFrame(modifier = modifier.fillMaxWidth(), shape = CycleHeaderShape) {
        Column(Modifier.padding(20.dp)) {
            SkeletonBar(Modifier.width(100.dp).height(10.dp))
            Box(Modifier.height(8.dp))
            SkeletonBar(Modifier.width(160.dp).height(20.dp))
            Box(Modifier.height(6.dp))
            SkeletonBar(Modifier.width(190.dp).height(13.dp))
        }
    }
}

// ---------- Stash ----------

private val StashItemShape = RoundedCornerShape(14.dp)
private val HangoutCardShape = RoundedCornerShape(18.dp)

/** Mirrors StashScreen's StashItemCard: type badge + author row, title, tags, action row. */
@Composable
fun SkeletonStashItemCard(modifier: Modifier = Modifier) {
    SkeletonFrame(modifier = modifier.fillMaxWidth(), shape = StashItemShape) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SkeletonBar(Modifier.width(44.dp).height(20.dp), shape = RoundedCornerShape(7.dp))
                SkeletonBar(Modifier.width(80.dp).height(10.dp))
            }
            Box(Modifier.height(8.dp))
            SkeletonBar(Modifier.width(150.dp).height(16.dp))
            Box(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBar(Modifier.width(46.dp).height(20.dp), shape = RoundedCornerShape(50))
                SkeletonBar(Modifier.width(58.dp).height(20.dp), shape = RoundedCornerShape(50))
            }
            Box(Modifier.height(10.dp))
            DashedDivider(color = Ink.copy(alpha = 0.1f))
            Box(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                SkeletonBar(Modifier.width(64.dp).height(26.dp), shape = RoundedCornerShape(50))
                SkeletonBar(Modifier.width(48.dp).height(26.dp), shape = RoundedCornerShape(50))
                SkeletonBar(Modifier.width(56.dp).height(26.dp), shape = RoundedCornerShape(50))
            }
        }
    }
}

/** Mirrors StashScreen's HangoutCard: colored header band with title/badge, then a memory line. */
@Composable
fun SkeletonHangoutCard(modifier: Modifier = Modifier) {
    SkeletonFrame(modifier = modifier.fillMaxWidth(), shape = HangoutCardShape) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBar(Modifier.weight(1f).height(20.dp))
                Box(Modifier.width(8.dp))
                SkeletonBar(Modifier.width(56.dp).height(18.dp), shape = RoundedCornerShape(50))
                Box(Modifier.width(6.dp))
                SkeletonBar(Modifier.width(40.dp).height(26.dp), shape = RoundedCornerShape(50))
            }
            Box(Modifier.height(6.dp))
            SkeletonBar(Modifier.width(120.dp).height(11.dp))
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Ink))
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        ) {
            SkeletonBar(Modifier.fillMaxWidth().height(12.dp))
        }
    }
}

/** Generic single shimmering card - kept for any list row not covered by a dedicated variant above. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    contentHeight: Dp = 72.dp,
) {
    SkeletonFrame(modifier = modifier.fillMaxWidth().height(contentHeight), shape = shape) {}
}
