package com.hermes.companion.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    route: ConversationRoute,
    onBack: () -> Unit,
    vm: ChatViewModel = hiltViewModel(),
    showBack: Boolean = true,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    LaunchedEffect(route) { vm.bind(route) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = contentWindowInsets,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("@${route.profileId} · ${route.sessionId.takeLast(6)}",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.backendLabel} · ${route.gatewayId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.streaming) {
                        FilledTonalButton(
                            onClick = vm::stop,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Box(Modifier.size(4.dp))
                            Text("Stop")
                        }
                    }
                }
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
                if (state.streaming) {
                    item("running") {
                        Text(
                            "running…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            LaunchedEffect(state.messages.size, state.streaming) {
                val total = state.messages.size + if (state.streaming) 1 else 0
                if (total > 0) listState.animateScrollToItem(total - 1)
            }
            state.error?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Composer(
                draft = state.draft,
                targetLabel = "@${route.profileId}",
                onChange = vm::updateDraft,
                onSend = vm::send,
            )
        }
    }

    state.pendingApproval?.let { req ->
        ApprovalSheet(request = req, onDecision = vm::decide)
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val isUser = msg is Message.User
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (msg is Message.Assistant) {
                    msg.toolRuns.forEach { ToolRunCard(it) }
                    if (msg.text.isNotEmpty()) {
                        Text(
                            msg.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (msg is Message.User) {
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    targetLabel: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Send to $targetLabel") },
        )
        Box(Modifier.size(8.dp))
        FilledIconButton(onClick = onSend) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
