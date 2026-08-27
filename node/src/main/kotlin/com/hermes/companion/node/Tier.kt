package com.hermes.companion.node

import android.content.Context
import com.hermes.companion.node.elevated.RootDetector
import com.hermes.companion.node.elevated.ShizukuGateway
import com.hermes.companion.node.service.HermesAccessibilityService

/** The four trust tiers from full-node-mode.md, low -> high. */
enum class NodeTier { Standard, Accessibility, Shizuku, Root }

fun detectNodeTier(context: Context): NodeTier = when {
    RootDetector.isRootGranted() -> NodeTier.Root
    ShizukuGateway.isGranted() -> NodeTier.Shizuku
    HermesAccessibilityService.isEnabled(context) -> NodeTier.Accessibility
    else -> NodeTier.Standard
}

fun activeTiers(context: Context): Set<NodeTier> = buildSet {
    add(NodeTier.Standard)
    if (HermesAccessibilityService.isEnabled(context)) add(NodeTier.Accessibility)
    if (ShizukuGateway.isGranted()) add(NodeTier.Shizuku)
    if (RootDetector.isRootGranted()) add(NodeTier.Root)
}

/** Call once from app startup (main thread). Safe when Shizuku is absent. */
fun installElevatedTier() = ShizukuGateway.install()
