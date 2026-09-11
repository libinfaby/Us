package com.pingucodu.us.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pingucodu.us.ui.screens.cycle.CycleScreen
import com.pingucodu.us.ui.screens.home.HomeScreen
import com.pingucodu.us.ui.screens.login.LoginScreen
import com.pingucodu.us.ui.screens.money.MoneyScreen
import com.pingucodu.us.ui.screens.stash.StashScreen

@Composable
fun PinguCoduApp() {
    val navController = rememberNavController()
    var loggedInAs by remember { mutableStateOf<String?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LOGIN_ROUTE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(LOGIN_ROUTE) {
                LoginScreen(onLoggedIn = { username ->
                    loggedInAs = username
                    navController.navigate(Tab.Home.route) {
                        popUpTo(LOGIN_ROUTE) { inclusive = true }
                    }
                })
            }
            composable(Tab.Home.route) { HomeScreen() }
            composable(Tab.Money.route) { MoneyScreen() }
            composable(Tab.Cycle.route) { CycleScreen() }
            composable(Tab.Stash.route) { StashScreen() }
        }
    }
}
