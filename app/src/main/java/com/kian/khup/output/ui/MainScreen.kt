package com.kian.khup.output.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kian.khup.output.ui.ai.AiChatScreen
import com.kian.khup.output.ui.dailyplan.DailyPlanScreen
import com.kian.khup.output.ui.history.HistoryScreen
import com.kian.khup.output.ui.messages.NotificationsScreen
import com.kian.khup.output.ui.settings.SettingsScreen
import com.kian.khup.output.ui.today.TodayScreen
import com.kian.khup.output.ui.usage.AppUsageScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "今日", Icons.Outlined.Today),
    History("history", "历史", Icons.Outlined.History),
    Ai("ai", "AI", Icons.Outlined.AutoAwesome),
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
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Today.route) {
                TodayScreen(
                    onNavigateToSettings = { navController.navigate(Tab.Settings.route) },
                    onNavigateToHistory = { navController.navigate(Tab.History.route) },
                    onNavigateToNotifications = { navController.navigate("notifications") },
                    onNavigateToDailyPlan = { navController.navigate("daily_plan") },
                    onNavigateToAppUsage = { navController.navigate("app_usage") },
                    onNavigateToAi = { navigateToAiWithBridge(navController) },
                )
            }
            composable(Tab.History.route) {
                HistoryScreen(
                    onNavigateToAi = { navigateToAiWithBridge(navController) },
                )
            }
            composable(Tab.Ai.route) { AiChatScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable("notifications") {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable("daily_plan") {
                DailyPlanScreen(onBack = { navController.popBackStack() })
            }
            composable("app_usage") {
                AppUsageScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * 切换到 AI tab 并强制 [com.kian.khup.output.ui.ai.AiChatViewModel] 重新 init，
 * 以便消费 [com.kian.khup.core.ai.AiContextBridge] 里的预填上下文 / 待打开会话。
 * 这是 §6 / §9.4 路由的关键点 —— restoreState 必须为 false。
 */
private fun navigateToAiWithBridge(navController: NavHostController) {
    navController.navigate(Tab.Ai.route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = false
    }
}
