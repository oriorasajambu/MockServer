package com.minion.mockserver.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * The corner scale, exposed through `MaterialTheme.shapes`.
 *
 * Nocturne carries three radii — 4dp, 8dp and 14dp — and Material has five roles, so the roles
 * double up rather than inventing two more values. The pairing is chosen so the components that
 * actually use each role land on the radius the design gives them: cards and buttons take
 * `medium` at 8dp, dialogs and sheets take `extraLarge` at 14dp.
 *
 * Components pick a role (`MaterialTheme.shapes.medium`), never a radius. That is what makes
 * "round the corners a bit more" one edit instead of a search across every card in the app.
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.radiusSmall),
    small = RoundedCornerShape(Dimens.radiusMedium),
    medium = RoundedCornerShape(Dimens.radiusMedium),
    large = RoundedCornerShape(Dimens.radiusLarge),
    extraLarge = RoundedCornerShape(Dimens.radiusLarge),
)

/**
 * A fully rounded shape, for the bottom-nav selection pill and the method and status badges.
 *
 * Expressed as a percentage rather than a large dp value so it stays a stadium at any height —
 * a fixed 999.dp radius on a 26dp pill and on a 44dp row are the same shape, but a fixed 13.dp
 * is only correct for one of them.
 */
val PillShape = RoundedCornerShape(percent = 50)
