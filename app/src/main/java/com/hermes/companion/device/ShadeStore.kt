package com.hermes.companion.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ShadeItem(
    val id: String,
    val app: String,
    val title: String,
    val body: String,
    val at: Long,
    val unread: Boolean = true,
    val forwarded: Boolean = false,
    val queued: Boolean = false,
)

object ShadeStore {
    private val samples = listOf(
        Triple("Messages", "Maya Chen", "Running 10 late — grab a table if you get there first?"),
        Triple("Calendar", "Design review in 25 min", "Studio North · Room 4. Bring the companion deck."),
        Triple("Slack", "hermes-ops", "Nightly evals finished. 2 tools failed rate-limit on android_screenshot."),
        Triple("Mail", "Boarding pass ready", "SFO → SEA · Saturday 7:10 AM · Gate assigned at 6:20."),
        Triple("GitHub", "nousresearch/hermes-agent", "CI passed on #1842 · mobile companion session resume."),
        Triple("Aether Bank", "Card used · $48.20", "Blue Bottle Coffee · Mission. If this wasn’t you, freeze the card."),
        Triple("Maps", "Leave in 12 minutes", "Usual route to Studio North is 18 min with light traffic."),
        Triple("Clock", "Alarm in 6 hours", "Weekday 6:30 AM is still on."),
        Triple("System", "Security update ready", "Play system update 15.2 · 48 MB. Restart when idle."),
        Triple("Play Store", "Messages 14.2", "Update ready · RCS fixes and quieter notifications."),
    )

    private val _items = MutableStateFlow(seed())
    val items: StateFlow<List<ShadeItem>> = _items.asStateFlow()

    var listenerOn: Boolean = true
    var forwardToHermes: Boolean = true

    private fun seed(): List<ShadeItem> {
        val now = System.currentTimeMillis()
        return samples.take(4).mapIndexed { i, t ->
            ShadeItem(
                id = UUID.randomUUID().toString(),
                app = t.first,
                title = t.second,
                body = t.third,
                at = now - (i + 1) * 60_000L * (7 + i * 4),
                unread = i != 3,
            )
        }
    }

    fun add(item: ShadeItem) {
        _items.update { listOf(item) + it }
    }

    fun inject() {
        val t = samples.random()
        add(
            ShadeItem(
                id = UUID.randomUUID().toString(),
                app = t.first,
                title = t.second,
                body = t.third,
                at = System.currentTimeMillis(),
                queued = forwardToHermes,
            ),
        )
    }

    fun post(app: String, title: String, body: String) {
        if (!listenerOn) return
        add(
            ShadeItem(
                id = UUID.randomUUID().toString(),
                app = app,
                title = title,
                body = body,
                at = System.currentTimeMillis(),
                queued = forwardToHermes,
            ),
        )
    }

    fun dismiss(id: String) {
        _items.update { it.filterNot { n -> n.id == id } }
    }

    fun markForwarded(ids: List<String>) {
        _items.update { list ->
            list.map { if (it.id in ids) it.copy(forwarded = true, queued = false, unread = false) else it }
        }
    }
}
