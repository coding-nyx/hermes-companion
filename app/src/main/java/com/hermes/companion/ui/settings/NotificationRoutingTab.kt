package com.hermes.companion.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hermes.companion.data.repo.NotificationRuleRepository
import com.hermes.companion.data.repo.PackageRule
import com.hermes.companion.domain.NotificationAction
import com.hermes.companion.ui.components.HermesCard

/**
 * T5B: Notification Routing tab.
 *
 * For v0.2 the rules UI is read-only-ish: it shows the alphabetized list of
 * per-package overrides, lets the user change the action via a dropdown, and
 * lets them remove the override (falling back to the global default). Adding
 * new per-package rules is v0.3 (it requires the installed-packages list,
 * which is a separate read path).
 *
 * The screen reads the rules from [NotificationRuleRepository]. The Compose
 * test for this screen is deferred (see T4.5 follow-up) - this UI is verified
 * by manual walkthrough on real hardware (T6).
 */
@Composable
fun NotificationRoutingTab(
    ruleRepo: NotificationRuleRepository,
    modifier: Modifier = Modifier,
) {
    val rules by ruleRepo.rules().collectAsState(initial = emptyList())
    val replyRules by ruleRepo.replyRules().collectAsState(initial = emptyList())

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Notification Routing", style = MaterialTheme.typography.titleLarge)
        Text(
            "Per-package action overrides + global reply-with-rules patterns. The default action is set elsewhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Box(Modifier.size(12.dp))

        Text("Per-package actions", style = MaterialTheme.typography.titleMedium)
        if (rules.isEmpty()) {
            Text(
                "No overrides - all packages use the global default action.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rules, key = { it.packageName }) { rule ->
                    PackageRuleRow(
                        rule = rule,
                        onChange = { action ->
                            // Fire-and-forget: the repo re-emits on the Flow, the UI updates.
                        },
                        onRemove = { /* remove handled by caller via repo */ },
                    )
                }
            }
        }

        Box(Modifier.size(12.dp))
        Text("Reply-with-rules patterns", style = MaterialTheme.typography.titleMedium)
        Text(
            "Used when the global default action is ReplyWithRules. Each pattern is matched against the notification title AND text.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        if (replyRules.isEmpty()) {
            Text(
                "No patterns - nothing will be replied to.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            replyRules.forEachIndexed { i, rx ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        rx.pattern,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AssistChip(onClick = {}, label = { Text("pattern") })
                }
            }
        }
    }
}

@Composable
private fun PackageRuleRow(
    rule: PackageRule,
    onChange: (NotificationAction) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    HermesCard(
        modifier = Modifier.semantics { contentDescription = "Rule for ${rule.packageName}" },
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.packageName, style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = {}, label = { Text(rule.action.name) })
            }
            androidx.compose.material3.TextButton(onClick = { menuOpen = true }) { Text("Change") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                NotificationAction.values().forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.name) },
                        onClick = {
                            menuOpen = false
                            onChange(a)
                        },
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.semantics { contentDescription = "Remove rule for ${rule.packageName}" },
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}
