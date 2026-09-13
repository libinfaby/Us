package com.pingucodu.us.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

/** The 4 bottom-nav tabs, in display order. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "home", Icons.Filled.Home),
    Money("money", "money", Icons.Filled.AttachMoney),
    Cycle("cycle", "cycle", Icons.Filled.CalendarMonth),
    Stash("stash", "stash", Icons.Filled.Bookmark),
}

/** Routes reachable from within the main screen that aren't bottom-nav tabs. */
const val CHANGE_PIN_ROUTE = "change_pin"
