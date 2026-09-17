package com.pingucodu.us.ui.nav

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The 4 bottom-nav tabs, in display order. `shape` echoes the login screen's
 * pink-square/teal-circle/yellow-diamond logo motif into the nav icons - each
 * tab keeps a distinct outline shape rather than a Material icon.
 */
enum class Tab(val route: String, val label: String, val shape: Shape) {
    Home("home", "home", RoundedCornerShape(6.dp)),
    Money("money", "money", CircleShape),
    Cycle("cycle", "cycle", RoundedCornerShape(4.dp)),
    Stash("stash", "stash", CircleShape),
}

/** Routes reachable from within the main screen that aren't bottom-nav tabs. */
const val SETTINGS_ROUTE = "settings"
