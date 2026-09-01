package com.minion.scaffold.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The corner scale, exposed through `MaterialTheme.shapes`.
 *
 * Components pick a role (`MaterialTheme.shapes.medium`), never a radius. That is what makes
 * "round the corners a bit more" one edit instead of a search across every card in the app.
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Spacing steps, in dp.
 *
 * Material 3 has no spacing scale, so this fills the gap that would otherwise be filled by
 * `padding(13.dp)` scattered across features. Exposed as an object rather than a
 * `CompositionLocal` because spacing does not change with the theme.
 */
object AppSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp
}
