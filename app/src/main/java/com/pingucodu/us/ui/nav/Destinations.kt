package com.pingucodu.us.ui.nav

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The square tab shape turned 45 degrees, rounded corners and all. Its side is shrunk so the
 * rounded tips land about at the icon's edges, keeping it the same visual size as its neighbours.
 */
private object RotatedSquareShape : Shape {
    private val corner = 5.dp

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = minOf(size.width, size.height) * 0.82f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = with(density) { corner.toPx() }
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f),
                    cornerRadius = CornerRadius(radius),
                ),
            )
            transform(
                Matrix().apply {
                    translate(cx, cy)
                    rotateZ(45f)
                    translate(-cx, -cy)
                },
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * The bottom-nav tabs, in display order. `shape` echoes the login screen's
 * pink-square/teal-circle/yellow-diamond logo motif into the nav icons - outline
 * shapes rather than Material icons.
 */
enum class Tab(val route: String, val label: String, val shape: Shape) {
    Home("home", "home", RoundedCornerShape(6.dp)),
    Money("money", "money", RoundedCornerShape(6.dp)),
    Cycle("cycle", "cycle", CircleShape),
    Hangouts("hangouts", "hangouts", RotatedSquareShape),
    Stash("stash", "stash", CircleShape),
}

/** Routes reachable from within the main screen that aren't bottom-nav tabs. */
const val SETTINGS_ROUTE = "settings"
const val DATES_ROUTE = "dates"

/** Push-notification route (see backend `data.route`) that opens Money on its goals section. */
const val GOALS_PUSH_ROUTE = "goals"
