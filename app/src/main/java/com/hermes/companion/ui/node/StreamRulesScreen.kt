package com.hermes.companion.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.StreamRuleItem
import com.hermes.companion.ui.components.HermesCard

private val MODES = listOf("StreamFull", "Summarise", "CountOnly", "Ignore")
private fun label(m: String) = when (m) {
    "StreamFull" -> "Stream full"
    "Summarise" -> "Summarise"
    "CountOnly" -> "Count only"
    "Ignore" -> "Ignore"
    else -> m
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamRulesScreen(
    onBack: () -> Unit,
    vm: NodeViewModel = hiltViewModel(),
) {
    val rules by vm.streamRules.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What this phone tells Hermes", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "On-device redaction runs before anything leaves. Sensitive categories (OTP, banking, " +
                    "health) are forced to count-only — a per-source rule can lower them, never raise them. " +
                    "Ignore means the event never leaves, not even as a count.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            if (rules.isEmpty()) {
                Text(
                    "No sources seen yet. Grant notification access and rules will appear per app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rules, key = { it.source }) { rule -> RuleRow(rule) { next -> vm.setStreamRule(rule.source, next) } }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: StreamRuleItem, onSet: (String) -> Unit) {
    HermesCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                rule.source,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SuggestionChip(
                onClick = {
                    val next = MODES[(MODES.indexOf(rule.mode).coerceAtLeast(0) + 1) % MODES.size]
                    onSet(next)
                },
                label = { Text(label(rule.mode), maxLines = 1, softWrap = false) },
            )
        }
    }
}
