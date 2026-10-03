package com.pingucodu.us.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.NavBackStackEntry
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
import com.pingucodu.us.ui.screens.money.MoneySection
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
    // One-shot hand-offs into a tab: consumed by the destination screen once it has acted on them.
    var moneySection by remember { mutableStateOf<MoneySection?>(null) }
    var stashSharedUrl by remember { mutableStateOf<String?>(null) }
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
            pendingRoute == GOALS_PUSH_ROUTE -> {
                moneySection = MoneySection.GOALS
                navigateToTab(Tab.Money)
            }
            else -> return@LaunchedEffect
        }
        onPendingRouteConsumed()
    }

    // A link shared into the app from IMDb or Maps: open Stash's add sheet with it.
    LaunchedEffect(pendingShareUrl) {
        if (pendingShareUrl != null) {
            stashSharedUrl = pendingShareUrl
            navigateToTab(Tab.Stash)
            onPendingShareUrlConsumed()
        }
    }

    // The header and nav bar live inside each tab's destination (see TabChrome) rather than in
    // Scaffold slots, so they slide along with the tab during a back gesture from Settings/Dates
    // instead of popping in only once the gesture completes.
    @Composable
    fun TabFrame(tab: Tab, content: @Composable () -> Unit) {
        TabChrome(
            tab = tab,
            username = username,
            onSettings = { navController.navigate(SETTINGS_ROUTE) },
            onLogout = onLogout,
            onSelectTab = ::navigateToTab,
            content = content,
        )
    }

    Scaffold(containerColor = PinkTint) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Tab.Home.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { navEnter() },
                exitTransition = { navExit() },
                popEnterTransition = { navPopEnter() },
                popExitTransition = { navPopExit() },
                // The back gesture scrubs the same animation as a tapped back button, instead of
                // Navigation's default shrink-to-70% predictive-back effect.
                predictivePopEnterTransition = { navPopEnter() },
                predictivePopExitTransition = { navPopExit() },
            ) {
                composable(Tab.Home.route) {
                    TabFrame(Tab.Home) {
                    HomeScreen(
                        onNavigateToMoney = { navigateToTab(Tab.Money) },
                        onNavigateToCycle = { navigateToTab(Tab.Cycle) },
                        onNavigateToStash = { navigateToTab(Tab.Stash) },
                        onNavigateToDates = { navController.navigate(DATES_ROUTE) { launchSingleTop = true } },
                        onNavigateToGoals = {
                            moneySection = MoneySection.GOALS
                            navigateToTab(Tab.Money)
                        },
                    )
                    }
                }
                composable(Tab.Money.route) {
                    TabFrame(Tab.Money) {
                        MoneyScreen(initialSection = moneySection, onInitialSectionConsumed = { moneySection = null })
                    }
                }
                composable(Tab.Cycle.route) { TabFrame(Tab.Cycle) { CycleScreen() } }
                composable(Tab.Stash.route) {
                    TabFrame(Tab.Stash) {
                        StashScreen(sharedUrl = stashSharedUrl, onSharedUrlConsumed = { stashSharedUrl = null })
                    }
                }
                // Pushed screens paint their own background so the tab underneath doesn't show
                // through while they slide.
                composable(SETTINGS_ROUTE) {
                    SettingsScreen(onBack = { navController.popBackStack() }, modifier = Modifier.background(PinkTint))
                }
                composable(DATES_ROUTE) {
                    DatesScreen(onBack = { navController.popBackStack() }, modifier = Modifier.background(PinkTint))
                }
            }
        }
    }
}

// Settings and Dates are pushed on top of a tab, so they slide in from the right and back out the
// same way; switching between tabs is a quick crossfade. Kept short - every frame of a transition
// draws both screens.
private const val NAV_FADE_MS = 180
private const val NAV_SLIDE_MS = 280

private val PUSHED_ROUTES = setOf(SETTINGS_ROUTE, DATES_ROUTE)

private val NavBackStackEntry.isPushed: Boolean get() = destination.route in PUSHED_ROUTES

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnter(): EnterTransition =
    if (targetState.isPushed) {
        slideInHorizontally(tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
    } else {
        fadeIn(tween(NAV_FADE_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navExit(): ExitTransition =
    if (targetState.isPushed) {
        slideOutHorizontally(tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing)) { -it / 4 }
    } else {
        fadeOut(tween(NAV_FADE_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnter(): EnterTransition =
    if (initialState.isPushed) {
        slideInHorizontally(tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing)) { -it / 4 }
    } else {
        fadeIn(tween(NAV_FADE_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExit(): ExitTransition =
    if (initialState.isPushed) {
        slideOutHorizontally(tween(NAV_SLIDE_MS, easing = FastOutSlowInEasing)) { it }
    } else {
        fadeOut(tween(NAV_FADE_MS))
    }

/**
 * A tab's chrome: the header above its content, and PillNavBar layered on top as a floating
 * overlay the content can scroll behind (a docked bottom bar would reserve its own opaque strip).
 */
@Composable
private fun TabChrome(
    tab: Tab,
    username: String,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onSelectTab: (Tab) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(username = username, onSettings = onSettings, onLogout = onLogout)
            Box(Modifier.weight(1f)) { content() }
        }
        PillNavBar(
            currentRoute = tab.route,
            onSelect = onSelectTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AppHeader(username: String, onSettings: () -> Unit, onLogout: () -> Unit) {
    val displayName = LocalNameMask.current.resolve(username) ?: username
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PinkTint)
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
