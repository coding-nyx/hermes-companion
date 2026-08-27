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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.ui.activity.ActivityScreen
import com.hermes.companion.ui.agents.AgentsScreen
import com.hermes.companion.ui.chat.ChatScreen
import com.hermes.companion.ui.nav.Route
import com.hermes.companion.ui.node.NodeGrantsScreen
import com.hermes.companion.ui.node.NodeScreen
import com.hermes.companion.ui.node.StreamRulesScreen
import com.hermes.companion.ui.setup.NodeSetupScreen
import com.hermes.companion.ui.outbox.OutboxScreen
import com.hermes.companion.ui.diagnostics.DiagnosticsScreen
import com.hermes.companion.ui.discover.DiscoverScreen
import com.hermes.companion.ui.settings.AppearanceScreen
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

    var switcherOpen by remember { mutableStateOf(false) }
    var lastRoute by remember { mutableStateOf<ConversationRoute?>(null) }
    val onOpenChat: (ConversationRoute) -> Unit = { r ->
        lastRoute = r
        nav.navigate("chat/${r.gatewayId}/${r.profileId}/${r.sessionId}")
    }

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
            Column(Modifier.weight(1f)) {
                if (onTab) RouteCapsule(lastRoute) { switcherOpen = true }
                NavGraph(nav, padding = PaddingValues(0.dp), onOpenChat = onOpenChat)
            }
        }
    } else {
        Scaffold(
            topBar = { if (onTab) RouteCapsule(lastRoute) { switcherOpen = true } },
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
            NavGraph(nav, padding, onOpenChat = onOpenChat)
        }
    }

    if (switcherOpen) {
        FleetSwitcher(
            onDismiss = { switcherOpen = false },
            onOpenChat = { r -> onOpenChat(r); switcherOpen = false },
        )
    }
}

@Composable
private fun NavGraph(
    nav: androidx.navigation.NavHostController,
    padding: PaddingValues,
    onOpenChat: (ConversationRoute) -> Unit,
) {
    NavHost(
        navController = nav,
        startDestination = Route.Agents.path,
        modifier = Modifier.padding(padding),
    ) {
        composable(Route.Agents.path) {
            AgentsScreen(onOpenChat = onOpenChat)
        }
        composable(Route.Activity.path) {
            ActivityScreen()
        }
        composable(Route.Node.path) {
            NodeScreen(
                onOpenSetup = { nav.navigate(Route.NodeSetup.path) },
                onOpenGrants = { nav.navigate(Route.NodeGrants.path) },
                onOpenStreamRules = { nav.navigate(Route.StreamRules.path) },
            )
        }
        composable(Route.NodeSetup.path) {
            NodeSetupScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.NodeGrants.path) {
            NodeGrantsScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.StreamRules.path) {
            StreamRulesScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                onOpenOutbox = { nav.navigate(Route.Outbox.path) },
                onOpenDiscover = { nav.navigate(Route.Discover.path) },
                onOpenDiagnostics = { nav.navigate(Route.Diagnostics.path) },
                onOpenAppearance = { nav.navigate(Route.Appearance.path) },
            )
        }
        composable(Route.Discover.path) {
            DiscoverScreen(onBack = { nav.popBackStack() }, onAdded = { nav.popBackStack() })
        }
        composable(Route.Diagnostics.path) {
            DiagnosticsScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.Appearance.path) {
            AppearanceScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.Outbox.path) {
            OutboxScreen(onBack = { nav.popBackStack() })
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
