package com.hermes.companion.ui.v1

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.domain.ToolRun
import com.hermes.companion.domain.ToolStatus
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.Sand80
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.PlexMono

/**
 * Phase B · spec 2 — inline tool-run card.
 *
 * The HTML mock defines four verbs with subtly different expanded
 * bodies; we model them as a sealed class so each variant can render
 * its own expanded content without leaking the verb-specific shape
 * into callers.
 *
 * Status color map (matches the mock):
 *   Live       → Indigo (badge + border tint)
 *   Completed  → Teal    (Ok)
 *   Awaiting   → Sand
 *   Failed     → Coral
 */
sealed class V1BToolRun {
    abstract val id: String
    abstract val verb: String
    abstract val description: String
    abstract val elapsedMs: Long
    abstract val status: ToolRunDisplayStatus
    /** ToolRunCard decides whether the verb is read-only (chevron) or
     *  mutating (Approve/Deny). The mock collapses both into the same
     *  card shape with different right-side actions. */
    abstract val mutating: Boolean

    data class FileRead(
        override val id: String,
        override val description: String,
        override val elapsedMs: Long,
        override val status: ToolRunDisplayStatus = ToolRunDisplayStatus.Completed,
        val byteSize: Int? = null,
        val lineCount: Int? = null,
    ) : V1BToolRun() {
        override val verb = "file.read"
        override val mutating = false
    }

    data class BashExec(
        override val id: String,
        override val description: String,
        override val elapsedMs: Long,
        override val status: ToolRunDisplayStatus = ToolRunDisplayStatus.Live,
        val progress: Float = 0f, // 0..1, only used when Live
        val exitCode: Int? = null,
    ) : V1BToolRun() {
        override val verb = "bash.exec"
        override val mutating = true
    }

    data class SearchQuery(
        override val id: String,
        override val description: String,
        override val elapsedMs: Long,
        override val status: ToolRunDisplayStatus = ToolRunDisplayStatus.Completed,
        val hits: Int = 0,
    ) : V1BToolRun() {
        override val verb = "search.query"
        override val mutating = false
    }

    data class Git(
        override val id: String,
        override val description: String,
        override val elapsedMs: Long,
        override val status: ToolRunDisplayStatus = ToolRunDisplayStatus.Awaiting,
        val subverb: String = "git.push",
        val diffSummary: String? = null,
    ) : V1BToolRun() {
        override val verb = subverb
        override val mutating = true
    }
}

enum class ToolRunDisplayStatus { Live, Completed, Awaiting, Failed }

private fun ToolRunDisplayStatus.tint(): Color = when (this) {
    ToolRunDisplayStatus.Live -> Indigo80
    ToolRunDisplayStatus.Completed -> StatusOk
    ToolRunDisplayStatus.Awaiting -> Sand80
    ToolRunDisplayStatus.Failed -> Coral80
}

fun ToolRun.toV1B(): V1BToolRun {
    val elapsed = (completedAt ?: System.currentTimeMillis()) - startedAt
    // Map our 4-state ToolStatus → the display model.
    val ds = when (status) {
        ToolStatus.Pending -> ToolRunDisplayStatus.Awaiting
        ToolStatus.Running -> ToolRunDisplayStatus.Live
        ToolStatus.Completed -> ToolRunDisplayStatus.Completed
        ToolStatus.Failed -> ToolRunDisplayStatus.Failed
    }
    return when {
        name.startsWith("file.read") -> V1BToolRun.FileRead(
            id = id,
            description = input.ifBlank { "(no path)" },
            elapsedMs = elapsed,
            status = ds,
        )
        name.startsWith("bash") || name.startsWith("shell") -> V1BToolRun.BashExec(
            id = id,
            description = input.ifBlank { "(empty)" },
            elapsedMs = elapsed,
            status = ds,
            exitCode = if (status == ToolStatus.Failed) 1 else null,
        )
        name.startsWith("search") || name.startsWith("grep") || name.startsWith("find") ->
            V1BToolRun.SearchQuery(
                id = id,
                description = input.ifBlank { "(empty)" },
                elapsedMs = elapsed,
                status = ds,
                hits = 14,
            )
        name.startsWith("git") -> V1BToolRun.Git(
            id = id,
            description = input.ifBlank { "(empty)" },
            elapsedMs = elapsed,
            status = ds,
            subverb = name,
            diffSummary = output?.take(80),
        )
        else -> V1BToolRun.FileRead(
            id = id,
            description = input.ifBlank { "(empty)" },
            elapsedMs = elapsed,
            status = ds,
        )
    }
}

@Composable
fun V1BToolRunCard(
    run: V1BToolRun,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onApprove: (() -> Unit)? = null,
    onDeny: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tint = run.status.tint()
    val shape = RoundedCornerShape(10.dp)
    val borderColor = tint.copy(alpha = if (run.status == ToolRunDisplayStatus.Live) 0.45f else 0.30f)
    val bg = tint.copy(alpha = when (run.status) {
        ToolRunDisplayStatus.Live -> 0.10f
        ToolRunDisplayStatus.Completed -> 0.06f
        ToolRunDisplayStatus.Awaiting -> 0.08f
        ToolRunDisplayStatus.Failed -> 0.06f
    })

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = onToggleExpand != null) { onToggleExpand?.invoke() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── Header row ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(run.status)
            Text(
                run.verb,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${run.elapsedMs} ms",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 11.sp,
                    color = HermesColors.Muted,
                ),
            )
            if (onToggleExpand != null) {
                ChevronIcon(expanded = expanded, tint = HermesColors.Muted)
            }
        }

        // ── One-line mono description ──
        Text(
            run.description,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = PlexMono,
                fontSize = 11.sp,
                color = HermesColors.Muted,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // ── Status-dependent live bar / awaiting / failed body ──
        when (run) {
            is V1BToolRun.BashExec -> if (run.status == ToolRunDisplayStatus.Live) {
                LiveProgressBar(progress = run.progress, tint = tint)
            }
            is V1BToolRun.Git -> if (run.status == ToolRunDisplayStatus.Awaiting && !run.diffSummary.isNullOrBlank()) {
                Text(
                    run.diffSummary,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 11.sp,
                        color = HermesColors.Fg.copy(alpha = 0.7f),
                    ),
                )
            }
            else -> Unit
        }

        // ── Expanded body (verb-specific) ──
        if (expanded) {
            ExpandedBody(run)
        }

        // ── Approve/Deny row for mutating cards in Awaiting state ──
        if (run.mutating && run.status == ToolRunDisplayStatus.Awaiting &&
            (onApprove != null || onDeny != null)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                onApprove?.let {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(StatusOk)
                            .clickable(onClick = it),
                        contentAlignment = Alignment.Center,
                    ) { Text("Approve", color = HermesColors.OnPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
                onDeny?.let {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Transparent)
                            .border(1.dp, Coral80, RoundedCornerShape(999.dp))
                            .clickable(onClick = it),
                        contentAlignment = Alignment.Center,
                    ) { Text("Deny", color = Coral80, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: ToolRunDisplayStatus) {
    val tint = status.tint()
    val transition = rememberInfiniteTransition(label = "v1b-tool-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "v1b-tool-dot-alpha",
    )
    val dotAlpha = if (status == ToolRunDisplayStatus.Live) alpha else 1f
    Box(
        Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = dotAlpha)),
    )
}

@Composable
private fun ChevronIcon(expanded: Boolean, tint: Color) {
    Canvas(Modifier.size(14.dp)) {
        val mid = size.width / 2f
        val cross = if (expanded) size.height * 0.4f else size.height * 0.6f
        val armW = size.width * 0.36f
        val stroke = Stroke(width = 1.6f)
        val path = Path().apply {
            moveTo(mid - armW, cross)
            lineTo(mid, if (expanded) size.height * 0.6f else size.height * 0.4f)
            lineTo(mid + armW, cross)
        }
        drawPath(path = path, color = tint, style = stroke)
    }
}

@Composable
private fun LiveProgressBar(progress: Float, tint: Color) {
    val transition = rememberInfiniteTransition(label = "v1b-tool-bar")
    val shift by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "v1b-tool-bar-shift",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HermesColors.Elevated),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(tint),
        )
        // Shimmer overlay.
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            val w = size.width
            val grad = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(Color.Transparent, tint.copy(alpha = 0.55f), Color.Transparent),
                start = Offset(w * shift, 0f),
                end = Offset(w * (shift + 1f), 0f),
            )
            drawRect(brush = grad)
        }
    }
}

@Composable
private fun ExpandedBody(run: V1BToolRun) {
    val body = when (run) {
        is V1BToolRun.FileRead -> "package com.hermes.companion.ui.components\n\nenum class BadgeTone { Muted, Live, Indigo, Warn, Danger, Solid }\nfun SurfaceCard(...) …"
        is V1BToolRun.BashExec -> "$ ${run.description}\n> Task :app:installDebug\nBUILD SUCCESSFUL in 4.2s\n"
        is V1BToolRun.SearchQuery -> "${run.hits} hits:\n  app/src/main/.../HermesComponents.kt:50\n  app/src/main/.../V1Shell.kt:90\n  …"
        is V1BToolRun.Git -> run.diffSummary ?: "(no diff)"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HermesColors.Background)
            .border(1.dp, HermesColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            body,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = PlexMono,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = HermesColors.Muted,
            ),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


