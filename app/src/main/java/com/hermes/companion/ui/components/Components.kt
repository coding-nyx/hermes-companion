package com.hermes.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesMono
import com.hermes.companion.ui.theme.HermesType

/**
 * The shared design-system primitives. Every screen composes these so spacing,
 * radius, elevation, mono metadata and status semantics are identical.
 */

@Composable
fun Caduceus(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onBackground) {
    Canvas(modifier) {
        val s = size.minDimension / 32f
        val stroke = Stroke(width = 1.5f * s, cap = StrokeCap.Round)
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(16f * s, 4f * s),
            end = androidx.compose.ui.geometry.Offset(16f * s, 26f * s),
            strokeWidth = 1.6f * s,
            cap = StrokeCap.Round,
        )
        drawCircle(color = color, radius = 1.8f * s, center = androidx.compose.ui.geometry.Offset(16f * s, 4.6f * s))
        val p1 = Path().apply {
            moveTo(10f * s, 8.5f * s)
            cubicTo(13.2f * s, 6.1f * s, 18.8f * s, 6.1f * s, 22f * s, 8.5f * s)
        }
        drawPath(p1, color = color, style = stroke)
        val p2 = Path().apply {
            moveTo(9f * s, 11.5f * s)
            cubicTo(13f * s, 8.5f * s, 19f * s, 14.5f * s, 23f * s, 11.5f * s)
        }
        drawPath(p2, color = color, style = stroke)
        val p3 = Path().apply {
            moveTo(9f * s, 16.5f * s)
            cubicTo(13f * s, 13.5f * s, 19f * s, 19.5f * s, 23f * s, 16.5f * s)
        }
        drawPath(p3, color = color, style = stroke)
        val p4 = Path().apply {
            moveTo(12.5f * s, 26.5f * s)
            lineTo(16f * s, 23.5f * s)
            lineTo(19.5f * s, 26.5f * s)
        }
        drawPath(p4, color = color, style = Stroke(width = 1.4f * s, cap = StrokeCap.Round))
    }
}

@Composable
fun HermesMark(size: Dp = 32.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Caduceus(Modifier.size(size * 0.55f), color = scheme.onBackground)
    }
}

enum class BadgeTone { Muted, Live, Warn, Danger, Solid }

@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        BadgeTone.Muted -> HermesColors.Elevated to HermesColors.Muted
        BadgeTone.Live -> HermesColors.Ok.copy(alpha = 0.15f) to HermesColors.Ok
        BadgeTone.Warn -> HermesColors.Warn.copy(alpha = 0.15f) to HermesColors.Warn
        BadgeTone.Danger -> HermesColors.Danger.copy(alpha = 0.15f) to HermesColors.Danger
        BadgeTone.Solid -> HermesColors.Fg to HermesColors.OnPrimary
    }
    Text(
        text = text.uppercase(),
        style = HermesType.kicker.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, color = fg),
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

@Composable
fun HermesCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = HermesType.kicker,
        modifier = modifier,
    )
}

/** Metadata (routes, seq, digests, ids) always in mono. */
@Composable
fun MetaText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = HermesMono),
        color = color,
        modifier = modifier,
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun TierChip(label: String, color: Color) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HermesMark(40.dp)
        Spacer(Modifier.size(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
