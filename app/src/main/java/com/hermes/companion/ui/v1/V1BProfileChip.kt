package com.hermes.companion.ui.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono

/**
 * Phase B · spec 5 — profile identity chip.
 *
 * Visual contract (from the handoff mock):
 *   - 24-dp circle with the profile's 2-letter monogram
 *   - accent color from [ProfilePalette.accentForProfile]
 *   - mono handle beside the avatar
 *
 * The richer "tile" layout (with turn counts) used by the
 * HandoffChain Cast card is in [V1BHandoffChainStrip] +
 * [V1BHandoffTile]. The composable here is the single-profile
 * pill chip.
 */
@Composable
fun V1BProfileChip(
    profile: AgentProfile,
    accentColor: Color = ProfilePalette.accentForProfile(profile.profileId),
    selected: Boolean = false,
    showHandle: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val monogram = profile.profileId.take(2).lowercase()
    val handle = profile.handle.display.trimStart('@')
    val borderColor = if (selected) accentColor else accentColor.copy(alpha = 0.45f)
    val bg = accentColor.copy(alpha = if (selected) 0.18f else 0.10f)
    val shape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                monogram,
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                ),
            )
        }
        if (showHandle) {
            Text(
                "@$handle",
                style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = accentColor),
            )
        }
    }
}

/**
 * Minimal data model for inline callers that don't have a full
 * AgentProfile handy (e.g. the QuickProfilePalette mock which uses
 * a raw `(name, sub)` pair).
 */
data class V1BProfileChipModel(
    val handle: String,
    val sub: String? = null,
)

@Composable
fun V1BProfileChipCompact(
    model: V1BProfileChipModel,
    accentColor: Color = ProfilePalette.accentForProfile(model.handle),
    selected: Boolean = false,
    showHandle: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    V1BProfileChip(
        profile = AgentProfile(
            gatewayId = "local",
            profileId = model.handle,
            displayName = model.handle,
            handle = com.hermes.companion.domain.ProfileHandle(
                profileId = model.handle,
                display = "@${model.handle}",
            ),
        ),
        accentColor = accentColor,
        selected = selected,
        showHandle = showHandle,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Internal · rich "tile" with monogram + handle + turn counts.
 * Used by [V1BHandoffChainStrip] for the ContextPanel Cast card.
 */
@Composable
internal fun V1BHandoffTile(
    profile: AgentProfile,
    accent: Color,
    turns: Int,
    onTap: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(72.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .let { if (onTap != null) it.clickable(onClick = onTap) else it }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.profileId.take(2).lowercase(),
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                ),
            )
        }
        Text(
            "@${profile.profileId}",
            style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = accent),
        )
        Text(
            if (turns == 1) "1 turn" else "$turns turns",
            style = TextStyle(fontFamily = PlexMono, fontSize = 9.sp, color = HermesColors.Muted),
        )
    }
}
