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
 * The scale is 4dp-based. Nocturne's own scale is 0.7× density and lands on fractional pixels
 * — 2.8, 5.6, 11.2 — which do not belong in Compose: dp are laid out on a device pixel grid, and
 * a 2.8dp gap rounds differently at every screen density, so a row that reads as even on one
 * phone reads as ragged on another. The design is normalised to the nearest 4dp step at or below
 * each of Nocturne's, which preserves the density it was after while staying on the grid.
 */
object Dimens {

    // ── Spacing ──────────────────────────────────────────────────────────────────────────────

    /** Hairline separators and focus rings. */
    val spaceQuarter = 2.dp

    /** Label to input, icon to text, badge gaps. */
    val spaceHalf = 4.dp

    /** Between buttons in a row, chip gaps, card gaps. */
    val spaceSmall = 8.dp

    /** List-row vertical padding, and card inner padding. */
    val spaceMedium = 12.dp

    /** The screen edge gutter, the gap between form fields, and dialog padding. */
    val space = 16.dp

    /** Between sections of a scrolling form, and above a divider group. */
    val spaceLarge = 24.dp

    /** Title block to the first content block. */
    val spaceXLarge = 32.dp

    // ── Radii ────────────────────────────────────────────────────────────────────────────────
    // Consumed by `AppShapes`; reach for `MaterialTheme.shapes` in a component rather than these.

    /** Chips, badges and other small tinted fills. */
    val radiusSmall = 4.dp

    /** Cards, buttons, inputs — the default. */
    val radiusMedium = 8.dp

    /** Dialogs, bottom sheets, and the largest surfaces. */
    val radiusLarge = 14.dp

    // ── Touch targets and controls ───────────────────────────────────────────────────────────

    /** Minimum touch target. Anything tappable is at least this on both axes. */
    val touchTarget = 48.dp

    /** The standard toggle. Sits inside a [touchTarget] box, which is what is actually tappable. */
    val switchWidth = 40.dp
    val switchHeight = 24.dp

    /** The toggle inside a card's route preview, where a full-size one would dominate the row. */
    val switchSmallWidth = 30.dp
    val switchSmallHeight = 18.dp

    /** The tinted circle carrying a dialog or sheet header's icon. */
    val dialogIconSize = 34.dp

    /** The accent pill behind the selected bottom-nav item. */
    val navPillWidth = 52.dp
    val navPillHeight = 26.dp

    /** The start/stop button on the server screen — the app's one genuinely large control. */
    val primaryActionHeight = 64.dp

    // ── Row metrics ──────────────────────────────────────────────────────────────────────────

    /** Height of a traffic-log row. Fixed, so the list scrolls without measuring every row. */
    val trafficRowHeight = 64.dp

    /**
     * Height of a route preview line inside a collection card. The densest thing in the app: one
     * line carrying a method, a path, a count, a status and a switch.
     */
    val routePreviewRowHeight = 44.dp

    /**
     * The leading rail on route and traffic rows, encoding active / wildcard / off.
     *
     * Thin on purpose. It is the fastest element in a long list to scan precisely because it
     * carries no text and sits at a fixed x — widening it would make it a block of colour, which
     * is the flood this design does not do.
     */
    val railWidth = 2.dp

    /** Width of the coloured method badge, so paths in a list all start at the same x. */
    val methodBadgeWidth = 56.dp

    // ── Surfaces ─────────────────────────────────────────────────────────────────────────────

    /** Elevation of the address block, the one raised surface on the server screen. */
    val elevationCard = 2.dp

    /** Side of the full-screen QR code. */
    val qrCodeSize = 280.dp

    /** Minimum height of the JSON body editor, so an empty body is still an obvious target. */
    val editorMinHeight = 160.dp

    // ── The one auto-sizing text ─────────────────────────────────────────────────────────────

    /**
     * The address on the server screen, which is read across a desk — and typed into a laptop by
     * hand, which is why it auto-sizes between these two rather than wrapping.
     *
     * A wrapped URL breaks after the colon and orphans the port on its own line, which is the one
     * place it must not break.
     */
    val heroAddressMaxTextSize = 20.sp
    val heroAddressMinTextSize = 13.sp
}
