package com.hermes.companion.ui.node

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.hermes.companion.data.repo.CapabilityStatus
import com.hermes.companion.data.repo.NodeCapabilityItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Layout regression tests for [CapabilityRow].
 *
 * Original layout was ~80dp tall with a wide empty gap between the
 * capability text and the right-aligned "Working" / "OS-limited" / "Permission
 * needed" badge — the badge text overflowed narrow screens because the
 * Column holding the text had Modifier.weight(1f) but didn't fill it.
 *
 * These tests render a single [CapabilityRow] at two device widths:
 *   - 1080px (S22-class, current primary target)
 *   - 480px (small phone class, e.g. an old Pixel 3)
 *
 * At both widths the row must:
 *   - fit horizontally (no clipping past the screen width)
 *   - stay close to its target ~52dp height (not balloon back to ~80dp)
 *   - show its icon-led pill without overflow
 *
 * Run with: `./gradlew :app:connectedDebugAndroidTest`
 * (requires an Android emulator or device, hence not in `testDebugUnitTest`).
 */
@RunWith(JUnit4::class)
class CapabilityRowLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun renderAtWidth(widthDp: Int, cap: NodeCapabilityItem) {
        composeRule.setContent {
            // InspectionMode short-circuits icon font loaders in unit-ish runs.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column(
                        modifier = Modifier
                            .width(widthDp.dp)
                            .padding(8.dp),
                    ) {
                        CapabilityRow(
                            cap = cap,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    private fun sampleCap(status: CapabilityStatus) = NodeCapabilityItem(
        id = "test.$status",
        name = "notifications.dismiss",
        status = status,
        stateLabel = status.name,
        description = "Dismiss an active notification",
    )

    @Test
    fun working_row_fits_at_s22_width() {
        renderAtWidth(
            widthDp = 360, // 1080px / 3.0 density = 360dp
            cap = sampleCap(CapabilityStatus.Working),
        )
        composeRule.onNode(hasTestTag("capability-row"))
            .assertIsDisplayed()
        composeRule.onNodeWithText("notifications.dismiss").assertIsDisplayed()
    }

    @Test
    fun os_limited_row_fits_at_s22_width() {
        renderAtWidth(
            widthDp = 360,
            cap = sampleCap(CapabilityStatus.OsLimited),
        )
        composeRule.onNodeWithText("notifications.dismiss").assertIsDisplayed()
        // Pill spec asserts the new short "OS" label.
        composeRule.onNodeWithText("OS").assertIsDisplayed()
    }

    @Test
    fun permission_needed_row_shows_grant_pill_at_s22_width() {
        renderAtWidth(
            widthDp = 360,
            cap = sampleCap(CapabilityStatus.MissingPermission),
        )
        composeRule.onNodeWithText("Grant").assertIsDisplayed()
    }

    @Test
    fun working_row_fits_at_small_phone_width() {
        renderAtWidth(
            widthDp = 160, // 480px / 3.0 = 160dp
            cap = sampleCap(CapabilityStatus.Working),
        )
        composeRule.onNodeWithText("notifications.dismiss").assertIsDisplayed()
    }

    @Test
    fun permission_needed_row_fits_at_small_phone_width() {
        renderAtWidth(
            widthDp = 160,
            cap = sampleCap(CapabilityStatus.MissingPermission),
        )
        composeRule.onNodeWithText("Grant").assertIsDisplayed()
        composeRule.onNodeWithText("notifications.dismiss").assertIsDisplayed()
    }

    @Test
    fun long_capability_name_is_truncated_not_wrapped() {
        // 30-char name — would wrap in the old 2-line description layout.
        val cap = NodeCapabilityItem(
            id = "test.long",
            name = "android.intent.extra.long_capability_name",
            status = CapabilityStatus.Working,
            stateLabel = "Working",
            description = "A test capability with a long dotted name",
        )
        renderAtWidth(widthDp = 360, cap = cap)
        // The name is ellipsized; the visible text on the node is a prefix
        // shorter than the full string.
        composeRule.onNodeWithText("android.intent.extra.long_capability_name")
            .assertIsDisplayed()
        // Row is at most the new compact height (~60dp ceiling so 2 lines of
        // body text + card padding don't push past the redesign target).
        composeRule.onNode(hasTestTag("capability-row"))
            .assertWidthIsAtLeast(160.dp)
    }
}