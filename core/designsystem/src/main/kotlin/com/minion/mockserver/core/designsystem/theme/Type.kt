package com.minion.mockserver.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Interface type: Inter, at 400/500/600.
 *
 * TODO: bundle Inter as `res/font` resources and swap this for a `FontFamily(...)` over them.
 * The design calls for bundled files rather than Google Fonts at runtime — a downloadable font
 * arrives after first frame, so every screen would paint once in the fallback metrics and reflow.
 * Until the files land this resolves to the platform sans, which is close enough in metrics that
 * the scale below is still the right scale, but it is not the typeface the design specifies.
 */
internal val InterFontFamily = FontFamily.Default

/**
 * Machine type: JetBrains Mono, at 400/500/700.
 *
 * TODO: bundle as `res/font` alongside Inter. See [InterFontFamily] — same reasoning, and the
 * fallback matters more here, because the platform monospace has different advance widths and the
 * header grid and JSON block are laid out on the assumption that a column of values aligns.
 */
internal val MonoFontFamily = FontFamily.Monospace

/**
 * The type scale, exposed through `MaterialTheme.typography`.
 *
 * Two families, split by origin: Inter carries the interface, and anything a *machine* produced —
 * paths, methods, status codes, JSON, addresses, ports, durations — is monospace. That split is
 * load-bearing rather than decorative. It is how `/api/v1/users/{id}` reads as data at a glance
 * instead of as a sentence, and it is why the mono styles below are not simply Inter at a
 * different size.
 *
 * Sizes are in `sp` and scale with the system font setting. The one place that can clip is the
 * 9sp method pill at the largest accessibility scale, which is acceptable because the pill is a
 * duplicate of information the path beside it already carries.
 */
internal val AppTypography = Typography(
    // Tab titles: Server, Routes, Traffic, Settings.
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.26).sp,
    ),
    // Dialog and sheet titles.
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 24.sp,
    ),
    // Detail-screen app bar titles.
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    // Collection names, settings section headers, profile names.
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // Field values, settings rows, dialog body.
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp,
    ),
    // Callout copy, profile descriptions.
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 20.sp,
    ),
    // Helper text under every field and slider.
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
    ),
    // Button labels.
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    // Field labels above inputs.
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
    ),
    // Bottom-nav labels, and the "THIS SESSION" eyebrows.
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * The monospace styles, which Material 3's [Typography] has no slot for.
 *
 * Rather than smuggle them into unused roles like `displayLarge` — where the name would tell the
 * next reader nothing and a future M3 component might pick them up by accident — they live here
 * and are read through [MockTheme.typography].
 */
@Immutable
data class MockTypography(
    /** Endpoint addresses and path inputs — the values a person retypes into a laptop. */
    val codeLarge: TextStyle,
    /** Route paths in lists, JSON bodies, header grids. */
    val codeMedium: TextStyle,
    /** Durations, timestamps, hit counts, the `×N` repeat badge. */
    val codeSmall: TextStyle,
    /** `GET`, `POST`, `DEL` and the status badges. */
    val methodPill: TextStyle,
)

internal val defaultMockTypography = MockTypography(
    codeLarge = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    codeMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 21.sp,
    ),
    codeSmall = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
    ),
    methodPill = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.63.sp,
    ),
)

internal val LocalMockTypography = staticCompositionLocalOf { defaultMockTypography }
