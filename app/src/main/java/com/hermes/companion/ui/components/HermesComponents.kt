package com.hermes.companion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.Figtree
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography

enum class BadgeTone { Muted, Live, Warn, Danger, Solid }

@Composable
fun Caduceus(modifier: Modifier = Modifier, color: Color = HermesColors.Fg) {
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
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(HermesColors.Elevated)
            .border(1.dp, HermesColors.Border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Caduceus(Modifier.size(size * 0.55f))
    }
}

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
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(HermesColors.Surface)
            .border(1.dp, HermesColors.Border, RoundedCornerShape(radius)),
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = HermesType.kickerSubtle)
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun HermesButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        !enabled -> HermesColors.Elevated
        filled -> HermesColors.Primary
        else -> Color.Transparent
    }
    val fg = when {
        filled -> HermesColors.OnPrimary
        else -> HermesColors.Fg
    }
    Box(
        modifier
            .height(if (large) 48.dp else 44.dp)
            .clip(shape)
            .background(bg)
            .then(if (!filled) Modifier.border(1.dp, HermesColors.Border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HermesTypography.labelLarge.copy(color = fg.copy(alpha = if (enabled) 1f else 0.4f)))
    }
}

@Composable
fun HermesField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(HermesColors.Surface)
            .border(1.dp, HermesColors.Border, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = HermesTypography.bodyMedium.copy(color = HermesColors.Subtle))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                fontFamily = Figtree,
                fontSize = 14.sp,
                color = HermesColors.Fg,
            ),
            cursorBrush = SolidColor(HermesColors.Fg),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    detail: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (showDivider) Modifier.border(BorderStroke(0.5.dp, HermesColors.Border)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg))
            if (detail != null) {
                Text(detail, style = HermesTypography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HermesColors.OnPrimary,
                checkedTrackColor = HermesColors.Primary,
                uncheckedThumbColor = HermesColors.Muted,
                uncheckedTrackColor = HermesColors.Elevated,
                uncheckedBorderColor = HermesColors.Border,
            ),
        )
    }
}

@Composable
fun LevelRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg), modifier = Modifier.weight(1f))
            Text("$value", style = HermesType.mono.copy(color = HermesColors.Muted))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = HermesColors.Fg,
                activeTrackColor = HermesColors.Fg,
                inactiveTrackColor = HermesColors.Elevated,
            ),
        )
    }
}

@Composable
fun QuickTile(
    label: String,
    active: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    badge: Int? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(72.dp)
            .clip(shape)
            .background(if (active) HermesColors.Fg else HermesColors.Surface)
            .then(if (active) Modifier else Modifier.border(1.dp, HermesColors.Border, shape))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = label,
                tint = if (active) HermesColors.OnPrimary else HermesColors.Muted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                label,
                style = HermesType.tab.copy(color = if (active) HermesColors.OnPrimary else HermesColors.Muted),
            )
        }
        if (badge != null && badge > 0) {
            Text(
                if (badge > 9) "9+" else "$badge",
                style = HermesType.mono.copy(fontSize = 9.sp, color = HermesColors.OnPrimary),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(HermesColors.Danger)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun Chip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(HermesColors.Surface)
            .border(1.dp, HermesColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg))
    }
}

@Composable
fun LinkText(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = HermesTypography.bodySmall.copy(color = HermesColors.Muted),
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    )
}
