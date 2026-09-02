package com.minion.mockserver.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.minion.mockserver.core.designsystem.R

/**
 * Interface type: Inter, at the three weights the scale below actually asks for.
 *
 * Bundled as `res/font` rather than fetched through Google Fonts at runtime, which is the design's
 * explicit call and the right one: a downloadable font arrives after first frame, so every screen
 * would paint once in the fallback metrics and then reflow.
 *
 * Only 400/500/600 ship. A weight that is not here is *synthesised* by the platform — smeared
 * outlines rather than the real cut — so if a call site ever needs Bold, add the file instead of
 * letting `FontWeight.Bold` resolve to a faked SemiBold.
 *
 * These are the 18pt optical size. Inter ships three; 18pt is cut for text at interface sizes,
 * which is everything here — the largest style in the app is a 26sp tab title.
 */
internal val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/**
 * Machine type: JetBrains Mono, at 400 and 700.
 *
 * Bundling matters more here than for [InterFontFamily]. The header grid and the JSON block are
 * laid out on the assumption that a column of values lines up, and that only holds if the advance
 * widths are the ones the design was drawn against — the platform monospace's are not.
 */
internal val MonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

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
