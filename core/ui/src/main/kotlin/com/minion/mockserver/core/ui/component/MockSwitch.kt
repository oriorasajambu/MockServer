package com.minion.mockserver.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.PillShape

/** How large a [MockSwitch] draws. */
enum class MockSwitchSize {
    /** 40x24, inside a 48dp touch box. Collection headers and settings rows. */
    Standard,

    /**
     * 30x18. The route preview line inside a collection card, where the standard size would
     * dominate a 44dp row carrying four other things.
     */
    Small,
}

/**
 * The app's toggle.
 *
 * Material's own `Switch` is a fixed 52x32 with an internal thumb animation that cannot be
 * resized, and this design needs two sizes — so this is drawn rather than themed. It is a track,
 * a thumb, and one animated offset.
 *
 * The touch target is the outer box, not the track. A 30x18 switch is well under the 48dp
 * minimum, so [MockSwitchSize.Standard] pads out to a full target while [MockSwitchSize.Small]
 * pads to the height of the row it sits in — that row is itself the tappable thing, and a 48dp
 * box inside a 44dp row would either clip or push the row taller than the design.
 *
 * @param onCheckedChange null renders the switch as a read-only indicator: no ripple, no
 *   semantics, no touch target. That is the state a route preview inside a disabled collection
 *   is in — the value still has to be visible, but it is not the thing you toggle.
 */
@Composable
fun MockSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    size: MockSwitchSize = MockSwitchSize.Standard,
    enabled: Boolean = true,
) {
    val standard = size == MockSwitchSize.Standard
    val trackWidth = if (standard) Dimens.switchWidth else Dimens.switchSmallWidth
    val trackHeight = if (standard) Dimens.switchHeight else Dimens.switchSmallHeight
    val inset = if (standard) Dimens.switchThumbInset else Dimens.switchSmallThumbInset
    val thumbSize = trackHeight - inset * 2

    val scheme = MaterialTheme.colorScheme
    // On: the accent at its base step, which is the one place in this design a saturated fill is
    // allowed — it is a 40dp control, not an area. Off: the border step, so an off switch reads
    // as an outline rather than as a second colour.
    val trackColour by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.outlineVariant,
        label = "switchTrack",
    )
    val thumbColour = if (checked) scheme.onPrimary else scheme.onSurfaceVariant
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - inset else inset,
        label = "switchThumb",
    )

    val interactive = onCheckedChange != null && enabled
    val touchBox = if (standard) Dimens.touchTarget else Dimens.routePreviewRowHeight

    Box(
        modifier = modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .sizeIn(minWidth = touchBox, minHeight = touchBox)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                } else {
                    Modifier
                },
            )
            // Disabled drops to 45% rather than swapping in grey values: the switch keeps its
            // meaning, it just stops asking to be pressed.
            .alpha(if (interactive || onCheckedChange == null) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = trackWidth, height = trackHeight)
                .background(trackColour, PillShape),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset, y = inset)
                    .size(thumbSize)
                    .background(thumbColour, CircleShape),
            )
        }
    }
}

/** Nocturne drops disabled controls to 45% opacity rather than recolouring them. */
internal const val DISABLED_ALPHA = 0.45f

@Preview(name = "Switches")
@Composable
internal fun MockSwitchPreview() {
    AppTheme {
        Row {
            MockSwitch(checked = true, onCheckedChange = {})
            MockSwitch(checked = false, onCheckedChange = {})
            MockSwitch(checked = true, onCheckedChange = {}, enabled = false)
            MockSwitch(checked = true, onCheckedChange = {}, size = MockSwitchSize.Small)
            MockSwitch(checked = false, onCheckedChange = null, size = MockSwitchSize.Small)
        }
    }
}
