package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA

/**
 * A small filled circle, optionally inside a glow ring.
 *
 * Two jobs in this app: whether the server is running, and whether an editor tab has unsaved
 * changes. Both are the same shape doing the same thing — marking one state as live — so they are
 * one component rather than two that drift apart.
 *
 * The glow is the whole signal. A running server and a stopped one differ by the ring, not by the
 * dot's colour alone, because colour alone is the distinction a colourblind user is least likely
 * to catch and this is the app's single most important piece of status.
 *
 * @param glowing whether to draw the ring. Running: true. Stopped, or an unsaved-changes mark:
 *   false — a dirty tab is a fact, not an alarm.
 */
@Composable
fun StateDot(
    color: Color,
    modifier: Modifier = Modifier,
    glowing: Boolean = false,
) {
    Box(
        modifier = modifier.size(if (glowing) Dimens.stateDotGlowSize else Dimens.stateDotSize),
        contentAlignment = Alignment.Center,
    ) {
        if (glowing) {
            Box(
                modifier = Modifier
                    .size(Dimens.stateDotGlowSize)
                    // The same tint the pills use, so a glow and a badge of the same colour carry
                    // the same weight on screen.
                    .background(color.copy(alpha = TINT_CONTAINER_ALPHA), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(Dimens.stateDotSize)
                .background(color, CircleShape),
        )
    }
}

@Preview(name = "State dots")
@Composable
internal fun StateDotPreview() {
    AppTheme {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(color = MaterialTheme.colorScheme.primary, glowing = true)
            StateDot(color = MaterialTheme.colorScheme.onSurfaceVariant)
            StateDot(color = MaterialTheme.colorScheme.error, glowing = true)
        }
    }
}
