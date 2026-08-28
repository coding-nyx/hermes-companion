package com.hermes.companion.node

import kotlinx.serialization.Serializable

@Serializable
data class ScreenNode(
    val id: Int,
    val text: String = "",
    val desc: String = "",
    val cls: String = "",
    val pkg: String = "",
    val clickable: Boolean = false,
    val focused: Boolean = false,
    val editable: Boolean = false,
    val l: Int = 0,
    val t: Int = 0,
    val r: Int = 0,
    val b: Int = 0,
) {
    val cx: Int get() = (l + r) / 2
    val cy: Int get() = (t + b) / 2
    val label: String get() = text.ifBlank { desc }
    val area: Int get() = (r - l).coerceAtLeast(0) * (b - t).coerceAtLeast(0)
}

@Serializable
data class ScreenTree(
    val pkg: String,
    val title: String,
    val nodes: List<ScreenNode>,
)

object ScreenQuery {
    fun findByText(nodes: List<ScreenNode>, query: String): ScreenNode? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        return nodes.firstOrNull { it.label.lowercase() == q }
            ?: nodes.firstOrNull { it.clickable && it.label.lowercase().contains(q) }
            ?: nodes.firstOrNull { it.editable && it.label.lowercase().contains(q) }
            ?: nodes.firstOrNull { it.label.lowercase().contains(q) }
    }

    fun findAt(nodes: List<ScreenNode>, x: Int, y: Int): ScreenNode? {
        return nodes
            .filter { x in it.l until it.r && y in it.t until it.b }
            .minByOrNull { it.area.coerceAtLeast(1) }
    }
}
