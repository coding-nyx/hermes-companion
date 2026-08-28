package com.hermes.companion.ui.chat

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.Message
import com.hermes.companion.ui.components.Chip
import com.hermes.companion.ui.nav.AskHermes
import com.hermes.companion.ui.theme.Figtree
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography

private val SUGGESTIONS = listOf(
    "Summarize my notifications",
    "What’s on this phone?",
    "Turn on do not disturb",
    "Dim brightness to 20",
)

@Composable
fun ChatScreen(
    route: ConversationRoute,
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(factory = ChatViewModel.factory()),
) {
    LaunchedEffect(route) {
        vm.bind(route)
        AskHermes.pending?.let { prompt ->
            AskHermes.pending = null
            vm.sendPrompt(prompt)
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = HermesColors.Muted)
            }
            Column(Modifier.weight(1f)) {
                Text("@${route.profileId}", style = HermesTypography.displayMedium.copy(fontSize = 20.sp), maxLines = 1)
                Text("${state.backendLabel} · ${route.gatewayId}", style = HermesType.kickerSubtle)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.messages.isEmpty() && !state.streaming) {
                item("empty") {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("Hermes is on the device.", style = HermesTypography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ask it to read the shade, change radios, or take the wheel. Sensitive actions wait for you.",
                            style = HermesTypography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        SUGGESTIONS.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { q ->
                                    Chip(q, onClick = { vm.sendPrompt(q) }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            items(state.messages, key = { it.id }) { msg ->
                MessageBlock(msg)
            }
            if (state.streaming && state.streamingText.isNotEmpty()) {
                item("stream") {
                    Text(state.streamingText, style = HermesTypography.bodyMedium.copy(color = HermesColors.Fg))
                }
            }
            if (state.streaming && state.streamingText.isEmpty()) {
                item("working") {
                    Text("HERMES IS WORKING", style = HermesType.kicker.copy(color = HermesColors.Muted))
                }
            }
        }
        LaunchedEffect(state.messages.size, state.streamingText) {
            val total = state.messages.size + if (state.streaming) 1 else 0
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
        Composer(
            draft = state.draft,
            enabled = !state.streaming,
            onChange = vm::updateDraft,
            onSend = vm::send,
        )
    }

    state.pendingApproval?.let { req ->
        ApprovalSheet(request = req, onDecision = vm::decide)
    }
}

@Composable
private fun MessageBlock(msg: Message) {
    when (msg) {
        is Message.User -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    msg.text,
                    style = HermesTypography.bodyMedium.copy(color = HermesColors.OnPrimary),
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp).copy(bottomEnd = androidx.compose.foundation.shape.CornerSize(4.dp)))
                        .background(HermesColors.Fg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        is Message.Assistant -> {
            Column(Modifier.fillMaxWidth().padding(end = 16.dp)) {
                if (msg.text.isNotBlank()) {
                    Text(msg.text, style = HermesTypography.bodyMedium.copy(color = HermesColors.Fg, lineHeight = 20.sp))
                }
                msg.toolRuns.forEach { ToolRunCard(it) }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(HermesColors.Surface)
            .border(1.dp, HermesColors.Border, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) {
                Text("Ask Hermes…", style = HermesTypography.bodyMedium.copy(color = HermesColors.Subtle))
            }
            BasicTextField(
                value = draft,
                onValueChange = onChange,
                enabled = enabled,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = Figtree,
                    fontSize = 14.sp,
                    color = HermesColors.Fg,
                ),
                cursorBrush = SolidColor(HermesColors.Fg),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (draft.isNotBlank() && enabled) HermesColors.Primary else HermesColors.Elevated)
                .clickableSafe(draft.isNotBlank() && enabled, onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Send,
                contentDescription = "Send",
                tint = if (draft.isNotBlank() && enabled) HermesColors.OnPrimary else HermesColors.Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun Modifier.clickableSafe(enabled: Boolean, onClick: () -> Unit) =
    if (enabled) this.clickable(onClick = onClick) else this
