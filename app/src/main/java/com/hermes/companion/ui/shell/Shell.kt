package com.hermes.companion.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.hermes.companion.ui.chat.ChatHome
import com.hermes.companion.ui.chat.ChatScreen
import com.hermes.companion.ui.components.HermesMark
import com.hermes.companion.ui.diagnostics.DiagnosticsScreen
import com.hermes.companion.ui.discover.DiscoverScreen
import com.hermes.companion.ui.nav.Route
import com.hermes.companion.ui.node.NodeGrantsScreen
import com.hermes.companion.ui.node.NodeScreen
import com.hermes.companion.ui.node.StreamRulesScreen
import com.hermes.companion.ui.outbox.OutboxScreen
import com.hermes.companion.ui.settings.AppearanceScreen
import com.hermes.companion.ui.settings.SettingsScreen
import com.hermes.companion.ui.setup.NodeSetupScreen
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.InstrumentSerif

private data class TabItem(val route: Route, val label: String, val icon: ImageVector)

@Composable
fun Shell(size: WindowSizeClass) {
    val nav = rememberNavController()
    val tabs = listOf(
        TabItem(Route.ChatTab, "Chat", Icons.AutoMirrored.Filled.Chat),
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
        nav.navigate(Route.ChatTab.path) {
            popUpTo(Route.ChatTab.path) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goTab(path: String) {
        nav.navigate(path) {
            popUpTo(Route.ChatTab.path) { inclusive = false }
            launchSingleTop = true
        }
    }

    val scheme = MaterialTheme.colorScheme

    if (expanded) {
        Row(Modifier.fillMaxSize().background(scheme.background)) {
            NavigationRail(
                containerColor = scheme.background,
                contentColor = scheme.onBackground,
            ) {
                Spacer(Modifier.height(12.dp))
                HermesMark(28.dp)
                Spacer(Modifier.height(16.dp))
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = current == tab.route.path,
                        onClick = { goTab(tab.route.path) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = HermesType.tab) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = scheme.onBackground,
                            selectedTextColor = scheme.onBackground,
                            unselectedIconColor = scheme.onSurfaceVariant,
                            unselectedTextColor = scheme.onSurfaceVariant,
                            indicatorColor = scheme.surfaceContainerHigh,
                        ),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                if (onTab) BrandHeader(lastRoute) { switcherOpen = true }
                NavGraph(nav, padding = PaddingValues(0.dp), onOpenChat = onOpenChat, lastRoute = lastRoute, onOpenSwitcher = { switcherOpen = true })
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
                .statusBarsPadding(),
        ) {
            if (onTab) BrandHeader(lastRoute) { switcherOpen = true }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                NavGraph(nav, PaddingValues(0.dp), onOpenChat = onOpenChat, lastRoute = lastRoute, onOpenSwitcher = { switcherOpen = true })
            }
            if (onTab) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.background.copy(alpha = 0.95f))
                        .border(androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant))
                        .navigationBarsPadding(),
                ) {
                    Row(Modifier.fillMaxWidth().height(64.dp)) {
                        tabs.forEach { tab ->
                            val active = current == tab.route.path ||
                                (tab.route == Route.ChatTab && current?.startsWith("chat/") == true)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { goTab(tab.route.path) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (active) scheme.onBackground else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    tab.label,
                                    style = HermesType.tab.copy(
                                        color = if (active) scheme.onBackground else scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
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
private fun BrandHeader(route: ConversationRoute?, onOpenSwitcher: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HermesMark(32.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Hermes", style = MaterialTheme.typography.titleMedium.copy(fontFamily = InstrumentSerif, color = scheme.onBackground))
                Text("COMPANION", style = HermesType.kicker)
            }
        }
        Spacer(Modifier.height(10.dp))
        RouteCapsule(route, onClick = onOpenSwitcher)
    }
}

@Composable
private fun NavGraph(
    nav: androidx.navigation.NavHostController,
    padding: PaddingValues,
    onOpenChat: (ConversationRoute) -> Unit,
    lastRoute: ConversationRoute?,
    onOpenSwitcher: () -> Unit,
) {
    NavHost(
        navController = nav,
        startDestination = Route.ChatTab.path,
        modifier = Modifier.padding(padding),
    ) {
        composable(Route.ChatTab.path) {
            ChatHome(route = lastRoute, onOpenSwitcher = onOpenSwitcher)
        }
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
