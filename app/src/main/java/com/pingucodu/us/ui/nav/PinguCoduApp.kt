package com.pingucodu.us.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pingucodu.us.ui.auth.AuthStatus
import com.pingucodu.us.ui.auth.AuthViewModel
import com.pingucodu.us.ui.screens.cycle.CycleScreen
import com.pingucodu.us.ui.screens.home.HomeScreen
import com.pingucodu.us.ui.screens.login.LoginScreen
import com.pingucodu.us.ui.screens.money.MoneyScreen
import com.pingucodu.us.ui.screens.stash.StashScreen

@Composable
fun PinguCoduApp(authViewModel: AuthViewModel = hiltViewModel()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val status = authViewModel.authStatus.collectAsState().value) {
            AuthStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            AuthStatus.LoggedOut -> LoginScreen()
            is AuthStatus.LoggedIn -> MainScreen(username = status.username)
        }
    }
}

@Composable
private fun MainScreen(username: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.Home.route) { HomeScreen() }
            composable(Tab.Money.route) { MoneyScreen() }
            composable(Tab.Cycle.route) { CycleScreen() }
            composable(Tab.Stash.route) { StashScreen() }
        }
    }
}
