package com.pingucodu.us.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pingucodu.us.ui.auth.AuthStatus
import com.pingucodu.us.ui.auth.AuthViewModel
import com.pingucodu.us.ui.screens.cycle.CycleScreen
import com.pingucodu.us.ui.screens.dates.DatesScreen
import com.pingucodu.us.ui.screens.home.HomeScreen
import com.pingucodu.us.ui.screens.login.LoginScreen
import com.pingucodu.us.ui.screens.money.MoneyScreen
import com.pingucodu.us.ui.screens.settings.SettingsScreen
import com.pingucodu.us.ui.screens.stash.StashScreen
import com.pingucodu.us.ui.theme.AvatarShape
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.hardShadow
import com.pingucodu.us.ui.util.LocalNameMask
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PinguCoduApp(
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    pendingShareUrl: String? = null,
    onPendingShareUrlConsumed: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    Surface(modifier = Modifier.fillMaxSize(), color = PinkTint) {
        when (val status = authViewModel.authStatus.collectAsState().value) {
            AuthStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ink)
            }
            AuthStatus.LoggedOut -> LoginScreen()
            is AuthStatus.LoggedIn -> MainScreen(
                username = status.username,
                onLogout = authViewModel::logout,
                pendingRoute = pendingRoute,
                onPendingRouteConsumed = onPendingRouteConsumed,
                pendingShareUrl = pendingShareUrl,
                onPendingShareUrlConsumed = onPendingShareUrlConsumed,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    username: String,
    onLogout: () -> Unit,
    pendingRoute: String?,
    onPendingRouteConsumed: () -> Unit,
    pendingShareUrl: String?,
    onPendingShareUrlConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    // One-shot hand-off into Stash: consumed by the screen once it has acted on it.
    var stashSharedUrl by remember { mutableStateOf<String?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = Tab.entries.any { it.route == currentRoute }

    fun navigateToTab(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A tapped notification hands us a route (e.g. "money") via MainActivity's intent extras;
    // jump there once and clear it so recomposition/back-nav doesn't re-trigger the jump.
    LaunchedEffect(pendingRoute) {
        val tab = Tab.entries.firstOrNull { it.route == pendingRoute }
        when {
            tab != null -> navigateToTab(tab)
            pendingRoute == DATES_ROUTE -> navController.navigate(DATES_ROUTE) { launchSingleTop = true }
            else -> return@LaunchedEffect
        }
        onPendingRouteConsumed()
    }

    // A link shared into the app from IMDb/Letterboxd/Maps: open Stash's add sheet with it.
    LaunchedEffect(pendingShareUrl) {
        if (pendingShareUrl != null) {
            stashSharedUrl = pendingShareUrl
            navigateToTab(Tab.Stash)
            onPendingShareUrlConsumed()
        }
    }

    Scaffold(
        containerColor = PinkTint,
        topBar = {
            if (isTabRoute) {
                AppHeader(
                    username = username,
                    onSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onLogout = onLogout,
                )
            }
        },
        // No bottomBar slot: PillNavBar is layered on top of the content below instead, as a
        // true floating overlay the content can scroll behind - a Scaffold bottomBar would
        // reserve its own opaque strip and dock the content above it instead.
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Tab.Home.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Tab.Home.route) {
                    HomeScreen(
                        onNavigateToMoney = { navigateToTab(Tab.Money) },
                        onNavigateToCycle = { navigateToTab(Tab.Cycle) },
                        onNavigateToStash = { navigateToTab(Tab.Stash) },
                        onNavigateToDates = { navController.navigate(DATES_ROUTE) { launchSingleTop = true } },
                    )
                }
                composable(Tab.Money.route) { MoneyScreen() }
                composable(Tab.Cycle.route) { CycleScreen() }
                composable(Tab.Stash.route) {
                    StashScreen(sharedUrl = stashSharedUrl, onSharedUrlConsumed = { stashSharedUrl = null })
                }
                composable(SETTINGS_ROUTE) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(DATES_ROUTE) {
                    DatesScreen(onBack = { navController.popBackStack() })
                }
            }
            if (isTabRoute) {
                PillNavBar(
                    currentRoute = currentRoute,
                    onSelect = ::navigateToTab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun AppHeader(username: String, onSettings: () -> Unit, onLogout: () -> Unit) {
    val displayName = LocalNameMask.current.resolve(username) ?: username
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PinkTint)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .hardShadow(AvatarShape, offsetX = 3.dp, offsetY = 3.dp)
                .background(Pink, AvatarShape)
                .border(BorderWidth, Ink, AvatarShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(displayName.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("hey $displayName", style = MaterialTheme.typography.titleLarge)
            Text(todayLabel(), style = MaterialTheme.typography.bodySmall)
        }
        val settingsInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(interactionSource = settingsInteractionSource, indication = null, onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "settings", tint = Ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(50))
                .border(2.5.dp, Ink, RoundedCornerShape(50))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onLogout)
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Text("exit", style = PinguCoduType.monoLabel)
        }
    }
}

@Composable
private fun PillNavBar(currentRoute: String?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .hardShadow(RoundedCornerShape(22.dp))
            .border(BorderWidth, Ink, RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            val interactionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(interactionSource = interactionSource, indication = null) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(if (selected) Pink else Color.Transparent, tab.shape)
                        .border(2.dp, Ink, tab.shape),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.copy(alpha = if (selected) 1f else 0.45f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun todayLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)).lowercase()
