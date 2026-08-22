package com.hermes.companion.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.ui.agents.AgentsScreen
import com.hermes.companion.ui.chat.ChatScreen
import com.hermes.companion.ui.nav.Route
import com.hermes.companion.ui.node.NodeScreen
import com.hermes.companion.ui.settings.SettingsScreen

private data class TabItem(val route: Route, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun Shell(size: WindowSizeClass) {
    val nav = rememberNavController()
    val tabs = listOf(
        TabItem(Route.Agents, "Agents", Icons.Filled.Hub),
        TabItem(Route.Activity, "Activity", Icons.Filled.Notifications),
        TabItem(Route.Node, "Node", Icons.Filled.PhoneAndroid),
        TabItem(Route.Settings, "Settings", Icons.Filled.Settings),
    )
    val expanded = size.widthSizeClass != WindowWidthSizeClass.Compact

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val onTab = tabs.any { it.route.path == current } || current == null

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = current == tab.route.path,
                        onClick = {
                            nav.navigate(tab.route.path) {
                                popUpTo(Route.Agents.path) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                NavGraph(nav, padding = PaddingValues(0.dp))
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (onTab) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route.path,
                                onClick = {
                                    nav.navigate(tab.route.path) {
                                        popUpTo(Route.Agents.path) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavGraph(nav, padding)
        }
    }
}

@Composable
private fun NavGraph(nav: androidx.navigation.NavHostController, padding: PaddingValues) {
    NavHost(
        navController = nav,
        startDestination = Route.Agents.path,
        modifier = Modifier.padding(padding),
    ) {
        composable(Route.Agents.path) {
            AgentsScreen(onOpenChat = { route ->
                nav.navigate("chat/${route.gatewayId}/${route.profileId}/${route.sessionId}")
            })
        }
        composable(Route.Activity.path) {
            Placeholder("Activity lane — runs/events/jobs land here.")
        }
        composable(Route.Node.path) {
            NodeScreen()
        }
        composable(Route.Settings.path) {
            SettingsScreen()
        }
        composable(
            Route.Chat.Companion.pattern,
            arguments = listOf(
                navArgument("gatewayId") { type = NavType.StringType },
                navArgument("profileId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType },
            ),
        ) { entry ->
            val route = ConversationRoute(
                gatewayId = entry.arguments!!.getString("gatewayId")!!,
                profileId = entry.arguments!!.getString("profileId")!!,
                sessionId = entry.arguments!!.getString("sessionId")!!,
            )
            ChatScreen(route = route, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
