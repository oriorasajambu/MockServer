package com.minion.mockserver.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Nocturne, mapped onto Material 3's roles.
 *
 * One scheme, no light counterpart and no dynamic colour. That is a design decision, not an
 * omission: a second scheme is a second set of contrast ratios to keep honest on every change,
 * and dynamic colour would discard this palette entirely — the accent is the product's own
 * blurple, and a wallpaper-derived green in its place would say something the app does not mean.
 *
 * Two mappings here are worth reading twice, because they invert the Material habit:
 * - `primary` is a **border and a label colour**, never a filled button background. Nocturne
 *   outlines its primary action. A `Button` that fills itself with `primary` is off-design; use
 *   `primaryContainer`, which is a dark accent step, for the rare filled case.
 * - `surface` is the same value as `background`, so app bars take no tonal lift. Elevation in
 *   this system is a hairline plus ambient darkness, not a lighter surface.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Accent500,
    onPrimary = Ground,
    primaryContainer = Accent900,
    onPrimaryContainer = Accent200,

    // Nocturne is mono: it chose no second accent. Rather than invent one, the secondary and
    // tertiary roles resolve to neutrals so that any Material component reaching for them by
    // default lands in the palette instead of on the M3 baseline purple.
    secondary = Neutral400,
    onSecondary = Ground,
    secondaryContainer = Neutral900,
    onSecondaryContainer = Neutral200,
    tertiary = Neutral400,
    onTertiary = Ground,
    tertiaryContainer = Neutral900,
    onTertiaryContainer = Neutral200,

    background = Ground,
    onBackground = TextPrimary,
    surface = Ground,
    onSurface = TextPrimary,
    surfaceVariant = Neutral900,
    onSurfaceVariant = Neutral500,

    // The floating surfaces. `surfaceContainerHigh` is the one the design names for dialogs,
    // menus and sheets; the neighbours are filled in so a component that picks a different step
    // does not fall back to a baseline value.
    surfaceContainerLowest = Ground,
    surfaceContainerLow = Ground,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = Neutral900,

    // Elevation in M3 is drawn by blending `surfaceTint` over the surface. Pinning it to the
    // ground makes that overlay a no-op, which is what keeps a raised card reading as an edge
    // rather than as a paler rectangle.
    surfaceTint = Ground,

    outline = Neutral700,
    outlineVariant = Neutral800,

    error = Red80,
    onError = Ground,
    errorContainer = ErrorContainer,
    onErrorContainer = Red80,

    scrim = Scrim,
)

/**
 * The single theme wrapper. Everything the app draws sits inside exactly one of these, applied
 * once in `MainActivity`.
 *
 * Alongside the Material scheme it provides the two things Material has no room for — the domain
 * colours and the monospace styles — so that a feature reads all three from one place.
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMockColors provides darkMockColors,
        LocalMockTypography provides defaultMockTypography,
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * The extension half of the theme: everything Material 3 has no role for.
 *
 * Named to sit beside `MaterialTheme` at a call site — `MockTheme.colors.unmatched` next to
 * `MaterialTheme.colorScheme.error` — so it is obvious which values are the design system's and
 * which are this app's own. Call sites read this object; the `CompositionLocal`s behind it are
 * internal on purpose.
 */
object MockTheme {

    /** Domain colours: methods, outcomes, JSON syntax, row rails. */
    val colors: MockColors
        @Composable @ReadOnlyComposable get() = LocalMockColors.current

    /** The monospace styles. */
    val typography: MockTypography
        @Composable @ReadOnlyComposable get() = LocalMockTypography.current
}
