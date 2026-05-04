package com.kian.khup.output.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kian.khup.output.ui.analytics.AnalyticsScreen
import com.kian.khup.output.ui.dashboard.DashboardScreen
import com.kian.khup.output.ui.messages.MessagesScreen
import com.kian.khup.output.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "首页", Icons.Outlined.Notifications),
    Messages("messages", "消息", Icons.Outlined.Inbox),
    Analytics("analytics", "用机", Icons.Outlined.Analytics),
    Settings("settings", "设置", Icons.Outlined.Settings),
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Dashboard.route) { DashboardScreen() }
            composable(Tab.Messages.route)  { MessagesScreen() }
            composable(Tab.Analytics.route) { AnalyticsScreen() }
            composable(Tab.Settings.route)  { SettingsScreen() }
        }
    }
}
