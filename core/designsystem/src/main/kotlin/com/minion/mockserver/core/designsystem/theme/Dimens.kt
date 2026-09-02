package com.minion.mockserver.core.designsystem.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every spacing and sizing value the app uses.
 *
 * The only file besides `Color.kt` where a raw literal is allowed. A `16.dp` written inline in a
 * screen is invisible to a design change and reappears as `12.dp` two screens later; naming them
 * here is what makes "tighten the list rows" a one-line edit instead of a search.
 *
 * The scale is 4dp-based, which is what Material's own spacing assumes.
 */
object Dimens {

    /** Hairline separators and focus rings. */
    val spaceQuarter = 2.dp

    /** Between a label and the thing it labels. */
    val spaceHalf = 4.dp

    /** Between chips in a row, and inside a badge. */
    val spaceSmall = 8.dp

    /** Between related controls. */
    val spaceMedium = 12.dp

    /** The standard screen gutter, and the gap between unrelated blocks. */
    val space = 16.dp

    /** Between sections of a scrolling form. */
    val spaceLarge = 24.dp

    /** Above the first element of a screen with a hero, and below the last. */
    val spaceXLarge = 32.dp

    /** Minimum touch target. Anything tappable is at least this on both axes. */
    val touchTarget = 48.dp

    /** The start/stop button on the server screen — the app's one genuinely large control. */
    val primaryActionHeight = 64.dp

    /** Height of a traffic-log row. Fixed, so the list scrolls without measuring every row. */
    val trafficRowHeight = 64.dp

    /** Side of the full-screen QR code. */
    val qrCodeSize = 280.dp

    /** Minimum height of the JSON body editor, so an empty body is still an obvious target. */
    val editorMinHeight = 160.dp

    /** Width of the coloured method badge, so paths in a list all start at the same x. */
    val methodBadgeWidth = 56.dp

    /** Thickness of the accent stripe on a chaos or unmatched row. */
    val accentStripe = 3.dp

    /** Corner radius for cards and chips that are not from the Material shapes. */
    val cornerSmall = 6.dp

    /** Elevation of the address block, the one raised surface on the server screen. */
    val elevationCard = 2.dp

    /** Monospace body text: addresses, JSON, cURL. */
    val monospaceTextSize = 13.sp

    /** The address on the server screen, which is read across a desk. */
    val heroAddressTextSize = 22.sp
}
