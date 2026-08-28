package com.hermes.companion.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermes.companion.device.PhoneControl
import com.hermes.companion.device.ShadeStore
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.node.CompanionLink
import com.hermes.companion.ui.agents.AgentsScreen
import com.hermes.companion.ui.chat.ChatScreen
import com.hermes.companion.ui.components.BadgeTone
import com.hermes.companion.ui.components.HermesMark
import com.hermes.companion.ui.components.StatusBadge
import com.hermes.companion.ui.home.HomeScreen
import com.hermes.companion.ui.nav.AskHermes
import com.hermes.companion.ui.nav.Route
import com.hermes.companion.ui.node.NodeScreen
import com.hermes.companion.ui.settings.SettingsScreen
import com.hermes.companion.ui.shade.ShadeScreen
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

@Composable
fun Shell(onUnpair: () -> Unit) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val phone by PhoneControl.state.collectAsState()
    val shade by ShadeStore.items.collectAsState()
    val unread = shade.count { it.unread }
    val linkUp = CompanionLink.isUp()
    var clock by remember { mutableStateOf(formatClock()) }

    LaunchedEffect(Unit) {
        PhoneControl.refresh(ctx)
        while (true) {
            clock = formatClock()
            delay(15_000)
        }
    }

    val tabs = listOf(
        Tab(Route.Home, "Home", Icons.Outlined.Home),
        Tab(Route.Hermes, "Hermes", Icons.Outlined.ChatBubbleOutline),
        Tab(Route.Shade, "Shade", Icons.Outlined.Notifications),
        Tab(Route.Device, "Device", Icons.Outlined.Smartphone),
        Tab(Route.More, "More", Icons.Outlined.MoreHoriz),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    fun goTab(path: String) {
        nav.navigate(path) {
            popUpTo(Route.Home.path) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun openAsk(prompt: String) {
        AskHermes.pending = prompt
        goTab(Route.Hermes.path)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HermesColors.Background)
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(clock, style = HermesType.mono.copy(color = HermesColors.Subtle, fontSize = androidx.compose.ui.unit.sp(10)), modifier = Modifier.weight(1f))
                Text(
                    "${if (phone.wifi) "Wi-Fi" else if (phone.airplane) "Airplane" else "Radio off"} · ${phone.battery}%",
                    style = HermesType.mono.copy(color = HermesColors.Subtle, fontSize = androidx.compose.ui.unit.sp(10)),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HermesMark(32.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Hermes", style = HermesTypography.titleMedium.copy(fontFamily = com.hermes.companion.ui.theme.InstrumentSerif))
                    Text("COMPANION", style = HermesType.kicker)
                }
                StatusBadge(
                    if (linkUp) "plugin" else "reconnecting",
                    if (linkUp) BadgeTone.Live else BadgeTone.Warn,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            NavHost(navController = nav, startDestination = Route.Home.path) {
                composable(Route.Home.path) {
                    HomeScreen(
                        onOpenShade = { goTab(Route.Shade.path) },
                        onOpenDevice = { goTab(Route.Device.path) },
                        onAsk = { openAsk(it) },
                    )
                }
                composable(Route.Hermes.path) {
                    AgentsScreen(onOpenChat = { route ->
                        nav.navigate("chat/${route.gatewayId}/${route.profileId}/${route.sessionId}")
                    })
                }
                composable(Route.Shade.path) {
                    ShadeScreen(onAsk = { openAsk(it) })
                }
                composable(Route.Device.path) { NodeScreen() }
                composable(Route.More.path) { SettingsScreen(onUnpair = onUnpair) }
                composable(
                    Route.Chat.pattern,
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

        Column(
            Modifier
                .fillMaxWidth()
                .background(HermesColors.Background.copy(alpha = 0.95f))
                .border(androidx.compose.foundation.BorderStroke(1.dp, HermesColors.Border))
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().height(64.dp)) {
                tabs.forEach { tab ->
                    val active = current == tab.route.path || (tab.route == Route.Hermes && current?.startsWith("chat/") == true)
                    val badge = if (tab.route == Route.Shade) unread else 0
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { goTab(tab.route.path) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (active) HermesColors.Fg else HermesColors.Subtle,
                                modifier = Modifier.size(18.dp),
                            )
                            if (badge > 0) {
                                Text(
                                    if (badge > 9) "9+" else "$badge",
                                    style = HermesType.mono.copy(fontSize = androidx.compose.ui.unit.sp(9), color = HermesColors.OnPrimary),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(start = 10.dp)
                                        .background(HermesColors.Danger, androidx.compose.foundation.shape.CircleShape)
                                        .padding(horizontal = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label,
                            style = HermesType.tab.copy(color = if (active) HermesColors.Fg else HermesColors.Subtle),
                        )
                    }
                }
            }
        }
    }
}

private fun formatClock(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()).lowercase(Locale.getDefault())
