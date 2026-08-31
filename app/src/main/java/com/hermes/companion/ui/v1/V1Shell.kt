package com.hermes.companion.ui.v1

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Top-level Phase A shell.
 *
 * - Phone (compact width): single column. LeftRail is a left-edge
 *   ModalNavigationDrawer; ContextPanel is a right-edge overlay that
 *   the user edge-swipes in (or opens via the top bar peek button).
 * - Tablet (medium/expanded width): three persistent columns — LeftRail
 *   (320 dp) | ChatSurface (flex) | ContextPanel (320 dp).
 *
 * Modals (ProfileSwitcher, SettingsSheet, PairAsNodeFlow, NewThreadDialog)
 * are rendered as overlays on top of the shell so they always have the
 * same scrim and dismiss behaviour regardless of form factor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V1Shell(
    size: WindowSizeClass,
    vm: V1ShellViewModel = hiltViewModel(),
) {
    val expanded = size.widthSizeClass != WindowWidthSizeClass.Compact

    val leftOpen by vm.leftDrawerOpen.collectAsStateWithLifecycle()
    val contextOpen by vm.contextDrawerOpen.collectAsStateWithLifecycle()

    val leftState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Sync the left drawer state with our VM flag.
    LaunchedEffect(leftOpen) {
        if (leftOpen) leftState.open() else leftState.close()
    }
    LaunchedEffect(leftState.isOpen) {
        if (!leftState.isOpen && leftOpen) vm.toggleLeftDrawer()
    }

    val chatContent: @Composable () -> Unit = {
        V1ChatSurface(
            vm = vm,
            showHamburger = !expanded,
            showContextPeek = !expanded,
        )
    }

    if (expanded) {
        // Tablet: three persistent columns.
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(320.dp)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding(),
                ) {
                    V1LeftRail(
                        vm = vm,
                        isDrawerVariant = false,
                        showCloseButton = false,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) { chatContent() }
                Box(
                    Modifier
                        .width(320.dp)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding(),
                ) {
                    V1ContextPanel(
                        vm = vm,
                        isDrawerVariant = false,
                    )
                }
            }
        }
    } else {
        // Phone: chat in centre, left drawer, right context peek overlay.
        ModalNavigationDrawer(
            drawerState = leftState,
            drawerContent = {
                V1LeftRail(
                    vm = vm,
                    isDrawerVariant = true,
                    showCloseButton = true,
                    onClose = { vm.toggleLeftDrawer() },
                )
            },
            scrimColor = Color.Black.copy(alpha = 0.62f),
            modifier = Modifier.fillMaxSize(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) { chatContent() }

                    // Right-edge context panel: edge-swipe peek. Anchored to the
                    // right side with a thin handle strip. Tap the strip (or use
                    // the top-bar peek button on ChatSurface) to slide it open.
                    val interaction = remember { MutableInteractionSource() }
                    AnimatedVisibility(
                        visible = contextOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }),
                        exit = slideOutHorizontally(targetOffsetX = { it }),
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                    ) { vm.toggleContextDrawer() },
                            )
                            V1ContextPanel(
                                vm = vm,
                                isDrawerVariant = true,
                                onClose = { vm.toggleContextDrawer() },
                            )
                        }
                    }

                    // Always-on edge handle strip on the right edge so the user
                    // can discover the panel without a tutorial. Swiping it opens
                    // the panel; tapping opens/closes.
                    if (!contextOpen) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .width(8.dp)
                                .height(56.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                ) { vm.toggleContextDrawer() }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragX ->
                                        if (dragX < -20) vm.toggleContextDrawer()
                                    }
                                },
                        )
                    }
                }
            }
        }
    }

    // ── Modals ─────────────────────────────────────────────────────────
    val profileOpen by vm.profileSheetOpen.collectAsStateWithLifecycle()
    if (profileOpen) V1ProfileSwitcher(vm = vm, onDismiss = vm::closeProfileSheet)

    // ── Quick profile palette (long-press / ⌘K from the composer) ─────────
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    val allProfiles = remember(fleet) {
        fleet.gateways.flatMap { it.profiles.map { p -> p.profile } }
    }
    var quickPaletteVisible by remember { mutableStateOf(false) }
    LaunchedEffect(profileOpen) {
        // When the profile sheet opens, also surface the quick palette so
        // a single tap on the top-bar profile chip shows both the existing
        // switcher AND the Phase B compact palette for fast re-routing.
        if (profileOpen) quickPaletteVisible = true else quickPaletteVisible = false
    }
    V1BQuickProfilePalette(
        profiles = allProfiles,
        visible = quickPaletteVisible,
        onDismiss = { quickPaletteVisible = false },
        onPick = { /* TODO: switch active route to this profile */ },
    )

    val settingsOpen by vm.settingsSheetOpen.collectAsStateWithLifecycle()
    if (settingsOpen) V1SettingsSheet(
        vm = vm,
        onDismiss = vm::closeSettingsSheet,
        onOpenPairAsNode = {
            vm.closeSettingsSheet()
            vm.openPairAsNode()
        },
        onOpenOutbox = vm::closeSettingsSheet,
        onOpenDiscover = vm::closeSettingsSheet,
    )

    val pairOpen by vm.pairAsNodeOpen.collectAsStateWithLifecycle()
    if (pairOpen) V1PairAsNodeFlow(
        onDismiss = vm::closePairAsNode,
        onPaired = vm::closePairAsNode,
    )

    val newOpen by vm.newThreadOpen.collectAsStateWithLifecycle()
    if (newOpen) V1NewThreadDialog(
        vm = vm,
        onDismiss = vm::closeNewThread,
    )
}
