package com.minion.mockserver.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The Nocturne palette. The only file in the app where a hex literal is allowed to appear.
 *
 * These names describe the *colour*, not its job — `Accent500`, not `primary`. The mapping from
 * colour to role happens once, in `DarkColorScheme` and `darkMockColors` in `Theme.kt` and
 * `MockColors.kt`, so a rebrand changes the values here and nothing else.
 *
 * Both ramps were generated in OKLCH on one shared perceptual lightness scale, which is the
 * property that makes them interchangeable: `Accent700` and `Neutral700` carry the same visual
 * weight, so swapping one for the other changes hue without disturbing the layout's contrast.
 * That is why steps are picked by number rather than by eye, and why a value mixed between two
 * steps is almost always a mistake.
 *
 * The app is dark only, so the ramps are used inverted from the light-theme habit: the dark steps
 * (700-900) are fills, hovers and borders, 500 is the role's base, and the light steps (100-300)
 * are text sitting on those fills.
 */

/** Every screen background, and the app bars, which take no tonal lift above it. */
internal val Ground = Color(0xFF161826)

/** Menus, dialogs, sheets — the surfaces that float above [Ground]. */
internal val Surface = Color(0xFF232532)

/** Titles, paths, body copy. */
internal val TextPrimary = Color(0xFFE9E9ED)

// The accent ramp: blurple, OKLCH hue 289.2. Used as a line, a label and a glow — never as a
// flood. `Accent500` is the base the outlined primary button draws its border from.
internal val Accent100 = Color(0xFFF5F4FF)
internal val Accent200 = Color(0xFFE7E5FE)
internal val Accent300 = Color(0xFFD2CEFD)
internal val Accent400 = Color(0xFFB5ABFC)
internal val Accent500 = Color(0xFF968AE0)
internal val Accent600 = Color(0xFF796CBF)
internal val Accent700 = Color(0xFF5D5294)
internal val Accent800 = Color(0xFF423A6A)
internal val Accent900 = Color(0xFF2B2741)

// The neutral ramp: surfaces, borders and muted text. Chroma stays low here on purpose — the
// contrast in this design comes from the tonal steps, not from saturation.
internal val Neutral100 = Color(0xFFF3F5FE)
internal val Neutral200 = Color(0xFFE4E7F5)
internal val Neutral300 = Color(0xFFCFD3E5)
internal val Neutral400 = Color(0xFFB2B6CA)
internal val Neutral500 = Color(0xFF9397AB)
internal val Neutral600 = Color(0xFF75798C)
internal val Neutral700 = Color(0xFF595D6C)
internal val Neutral800 = Color(0xFF3F424D)
internal val Neutral900 = Color(0xFF292B31)

/**
 * The error tint, and the one value in the app with no design-system provenance.
 *
 * Nocturne is a mono system: it has an accent and a neutral ramp and no error role at all. This
 * is the Material 3 baseline's `Red80`, kept from the scaffold's own palette because an unmatched
 * request and a 5xx have to read as *wrong* and no ramp step says that. Its container and border
 * below were derived from it to match.
 *
 * This was open question 01 in the developer handoff, and the answer is that it stays. It is left
 * visibly the odd one out rather than disguised as a ramp step: the next person to look for where
 * this hue comes from should find this comment rather than assume Nocturne defines one.
 */
internal val Red80 = Color(0xFFF2B8B5)

/** The tint behind [Red80] — unmatched rows, 4xx/5xx badges, Stop and Delete. */
internal val ErrorContainer = Color(0xFF3A2226)

/** The border on an error-toned callout, a step up from [ErrorContainer]. */
internal val ErrorOutline = Color(0xFF6B3F44)

/**
 * Behind dialogs, menus and sheets.
 *
 * Darker than [Ground] rather than black: pure black is not in this system, and a scrim mixed
 * from the ground keeps the dimmed screen reading as the same surface rather than as a hole.
 * Applied at 72% — the alpha lives with the value because a scrim at full opacity is a bug.
 */
internal val Scrim = Color(0xB80B0C14)
