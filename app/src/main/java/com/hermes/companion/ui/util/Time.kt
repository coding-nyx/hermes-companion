package com.hermes.companion.ui.util

fun relativeTime(at: Long): String {
    val s = ((System.currentTimeMillis() - at) / 1000).coerceAtLeast(0)
    return when {
        s < 45 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        else -> "${s / 86400}d"
    }
}
