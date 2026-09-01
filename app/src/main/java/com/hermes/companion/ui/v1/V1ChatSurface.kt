package com.hermes.companion.ui.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.Message
import com.hermes.companion.domain.ToolRun
import com.hermes.companion.domain.ToolStatus
import com.hermes.companion.ui.components.HermesMark
import com.hermes.companion.ui.theme.DisplayLarge
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn

/**
 * The centre column: chat transcript + composer.
 *
 * Two visual modes:
 *  - empty (no active thread): Instrument Serif greeting + suggested tiles
 *  - active: transcript bubble list + always-present composer at the bottom
 *
 * Top bar carries:
 *  - left: hamburger (phone only) or back chevron (chat-thread mode)
 *  - centre: route capsule `gw › @profile › thread` with status dot
 *  - right: profile chip + activity-inbox indicator (always shown)
 */
@Composable
fun V1ChatSurface(
    vm: V1ShellViewModel,
    showHamburger: Boolean,
    @Suppress("UNUSED_PARAMETER") showContextPeek: Boolean,
    onHamburgerTap: () -> Unit = vm::toggleLeftDrawer,
) {
    val activeRoute by vm.activeRoute.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val inboxCount by vm.inboxCount.collectAsStateWithLifecycle()
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    // Hoisted composer state (was local `var draft by remember` inside the
    // composable — moved to the VM for config-change survival).
    val draft by vm.draft.collectAsStateWithLifecycle()
    val isRecording by vm.isRecording.collectAsStateWithLifecycle()

    var actionSheetVisible by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val bgWorkCollapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48 }
    }

    val allProfiles: List<AgentProfile> = remember(fleet) {
        fleet.gateways.flatMap { it.profiles.map { p -> p.profile } }
    }
    val activeProfile: AgentProfile? = remember(allProfiles, activeRoute) {
        allProfiles.firstOrNull { it.profileId == activeRoute?.profileId }
    }
    val backgroundWorkState = remember(conversation) { deriveBackgroundWorkState(conversation.messages, conversation.streaming) }
    val awaitingToolRun = remember(conversation) {
        conversation.messages
            .lastOrNull { it is Message.Assistant }
            ?.let { (it as Message.Assistant).toolRuns }
            ?.firstOrNull { it.status == ToolStatus.Pending }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ── Top bar ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showHamburger) {
                IconButton(
                    onClick = onHamburgerTap,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open threads")
                }
            } else if (activeRoute != null) {
                IconButton(
                    onClick = vm::clearThread,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Back to home")
                }
            }

            // Route capsule
            RouteCapsule(
                route = activeRoute,
                profiles = allProfiles,
                modifier = Modifier.weight(1f),
            )

            // Activity inbox indicator
            InboxButton(
                count = inboxCount,
                onClick = { vm.openProfileSheet() },
            )
        }

        // ── Background-work bar (collapses to pill on scroll) ─────
        if (backgroundWorkState != null && !bgWorkCollapsed) {
            V1BBackgroundWorkBar(
                state = backgroundWorkState,
                collapsed = false,
                onTap = { /* TODO: open work-detail sheet */ },
            )
        }
        if (backgroundWorkState != null && bgWorkCollapsed) {
            V1BBackgroundWorkPill(
                state = backgroundWorkState,
                onTap = { /* TODO: open work-detail sheet */ },
            )
        }

        // ── Body: empty or transcript ─────────────────────────────
        if (activeRoute == null) {
            EmptyHero(
                modifier = Modifier.weight(1f),
                onPickPrompt = vm::onPickPrompt,
            )
        } else {
            Transcript(
                messages = conversation.messages,
                streaming = conversation.streaming,
                awaitingToolRun = awaitingToolRun,
                activeProfileHandle = activeProfile?.handle?.display?.trimStart('@')
                    ?: activeRoute?.profileId.orEmpty(),
                listState = listState,
                onUserBubbleLongPress = { actionSheetVisible = true },
                modifier = Modifier.weight(1f),
            )
        }

        // ── Composer (Phase B morph-states version) ───────────────
        if (isRecording) {
            // Bug #8: render the overlay while recording so the press-and-
            // hold mic gesture has a visible effect. Bug #9: pressing Send
            // without an active thread now opens the new-thread dialog via
            // vm.submitDraft (handled inside V1BComposer.send callback).
            V1BVoiceRecordingOverlay(
                onStop = { vm.stopRecording() },
                onCancel = { vm.stopRecording() },
                modifier = Modifier.navigationBarsPadding(),
            )
        } else {
            V1BComposer(
                draft = draft,
                onChange = vm::updateDraft,
                onSend = { vm.submitDraft(draft) },
                onMicDown = { vm.startRecording() },
                onMicUp = { vm.stopRecording() },
                onMicCancel = { vm.stopRecording() },
                onAttachTap = { vm.openNewThread() },
                onProfilePalette = { vm.openProfileSheet() },
                isStreaming = conversation.streaming,
                isRecording = false,
                targetLabel = activeRoute?.let { "@${it.profileId}" } ?: "@ash",
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }

    // ── Message action sheet (long-press on user bubble) ─────────
    V1BMessageActionSheet(
        visible = actionSheetVisible,
        onDismiss = { actionSheetVisible = false },
        onAction = { /* TODO: wire to repo (edit/rerun/copy/branch/regen/delete) */ },
    )
}

/**
 * Decide if (and what kind of) background work is happening on the
 * active conversation, so the [V1BBackgroundWorkBar] above the route
 * capsule can render the right tint + label.
 */
private fun deriveBackgroundWorkState(
    messages: List<Message>,
    streaming: Boolean,
): V1BBackgroundWorkState? {
    val lastAssistant = messages.lastOrNull { it is Message.Assistant } as? Message.Assistant
        ?: return null
    val pendingRun = lastAssistant.toolRuns.firstOrNull { it.status == ToolStatus.Pending }
    if (pendingRun != null) {
        return V1BBackgroundWorkState(
            kind = V1BBackgroundWorkKind.Awaiting,
            label = "awaiting decision · ${pendingRun.name}",
            elapsedMs = (pendingRun.completedAt ?: System.currentTimeMillis()) - pendingRun.startedAt,
            awaitingVerb = pendingRun.name,
        )
    }
    val liveRun = lastAssistant.toolRuns.firstOrNull { it.status == ToolStatus.Running }
    if (liveRun != null || streaming) {
        return V1BBackgroundWorkState(
            kind = V1BBackgroundWorkKind.CallingTools,
            label = liveRun?.let { "${it.name}…" } ?: "drafting reply…",
            elapsedMs = System.currentTimeMillis() -
                (liveRun?.startedAt ?: lastAssistant.createdAt),
            queueSummary = if (lastAssistant.toolRuns.isNotEmpty())
                "${lastAssistant.toolRuns.size} tool${if (lastAssistant.toolRuns.size == 1) "" else "s"} queued"
            else null,
        )
    }
    return null
}


@Composable
private fun RouteCapsule(
    route: ConversationRoute?,
    profiles: List<AgentProfile>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, HermesColors.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(StatusOk),
        )
        if (route == null) {
            Text(
                "Pick a route",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                "gw-home",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                "›",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
            Text(
                "@${route.profileId}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PlexMono,
                    fontSize = 12.sp,
                    color = HermesColors.Primary,
                ),
                maxLines = 1,
            )
            Text(
                "›",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
            Text(
                route.sessionId.takeLast(6),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // V1BHandoffChainStrip (compact) shows the multi-profile handoff
            // chain in the tail of the capsule when more than one profile is
            // known — same strip used in the context panel, just compressed.
            if (profiles.size > 1) {
                Box(Modifier.widthIn(min = 1.dp, max = 120.dp)) {
                    V1BHandoffChainStrip(
                        profiles = profiles,
                        compact = true,
                    )
                }
            }
            Text("⌄", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun InboxButton(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Inbox,
            contentDescription = "Activity inbox",
            tint = MaterialTheme.colorScheme.onSurface,
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(HermesColors.Primary)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = HermesColors.OnPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EmptyHero(modifier: Modifier = Modifier, onPickPrompt: (String) -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HermesMark(size = 72.dp)
        Box(Modifier.height(18.dp))
        // Time-of-day greeting (was hardcoded "Good morning, Nyx."). Uses
        // Java time-of-day buckets so the greeting tracks actual local time.
        val hour = remember { java.time.LocalTime.now().hour }
        val greeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hey"
        }
        Text(
            "$greeting, Nyx.",
            style = DisplayLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
        )
        Box(Modifier.height(8.dp))
        Text(
            "You routed here from gw-home · @ash. Start a thread, drop in a file, or hand me a task.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box(Modifier.height(22.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            SuggestionTile("Triage my morning inbox", Modifier.weight(1f), onPickPrompt)
            SuggestionTile("Draft standup", Modifier.weight(1f), onPickPrompt)
        }
        Box(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            SuggestionTile("Review PR #482", Modifier.weight(1f), onPickPrompt)
            SuggestionTile("Plan my trip", Modifier.weight(1f), onPickPrompt)
        }
    }
}

@Composable
private fun SuggestionTile(label: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .clickable { onClick(label) }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Transcript(
    messages: List<Message>,
    streaming: Boolean,
    awaitingToolRun: ToolRun?,
    activeProfileHandle: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onUserBubbleLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { msg ->
            MessageBubble(
                msg = msg,
                awaitingToolRun = if (msg is Message.Assistant && awaitingToolRun != null &&
                    msg.toolRuns.any { it.id == awaitingToolRun.id }
                ) awaitingToolRun else null,
                activeProfileHandle = activeProfileHandle,
                onUserLongPress = onUserBubbleLongPress,
            )
        }
        if (streaming) {
            item("streaming") {
                Row {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HermesColors.Primary),
                    )
                    Box(Modifier.size(8.dp))
                    Text(
                        "streaming…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: Message,
    awaitingToolRun: ToolRun?,
    activeProfileHandle: String,
    onUserLongPress: () -> Unit,
) {
    val isUser = msg is Message.User
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                )
                .background(
                    if (isUser) HermesColors.Primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .border(
                    width = if (isUser) 0.dp else 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(12.dp)
                .let {
                    if (isUser) it.pointerInput(msg.id) {
                        detectTapGestures(onLongPress = { onUserLongPress() })
                    } else it
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (msg) {
                is Message.User -> Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HermesColors.OnPrimary,
                )
                is Message.Assistant -> {
                    msg.toolRuns.forEach { run ->
                        V1BToolRunCard(run = run.toV1B())
                        // Inline approval card when this specific run is awaiting
                        // a user decision (status Pending → display Awaiting).
                        if (awaitingToolRun != null && awaitingToolRun.id == run.id) {
                            V1BApprovalCard(
                                run = run.toV1B(),
                                profileHandle = activeProfileHandle,
                                elapsedMs = (run.completedAt ?: System.currentTimeMillis()) - run.startedAt,
                                riskText = run.input.take(120).ifBlank { "This tool mutates state." },
                                reversible = run.name.startsWith("bash") || run.name.startsWith("git"),
                                reversibleHint = if (run.name.startsWith("git")) "git revert available"
                                                 else "stdout/stderr already captured",
                                decision = V1BApprovalOutcome.Pending,
                                onDecision = { /* TODO: forward to vm */ },
                                onLockInChange = { /* TODO: persist lock-in */ },
                            )
                        }
                    }
                    if (msg.text.isNotEmpty()) {
                        Text(
                            msg.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}


