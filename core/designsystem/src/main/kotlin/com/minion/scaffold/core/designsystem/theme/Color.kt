package com.minion.scaffold.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw palette. The only file in the app where a hex literal is allowed to appear.
 *
 * These names describe the *colour*, not its job — `Purple40`, not `primary`. The mapping from
 * colour to role happens once, in [LightColorScheme] and [DarkColorScheme] in `Theme.kt`, so a
 * rebrand changes the values here and nothing else.
 *
 * This is the Material 3 baseline palette, deliberately neutral: it is a starting point to
 * replace with the product's own tokens, not a design.
 */
internal val Purple80 = Color(0xFFD0BCFF)
internal val PurpleGrey80 = Color(0xFFCCC2DC)
internal val Pink80 = Color(0xFFEFB8C8)

internal val Purple40 = Color(0xFF6650A4)
internal val PurpleGrey40 = Color(0xFF625B71)
internal val Pink40 = Color(0xFF7D5260)

internal val Red40 = Color(0xFFB3261E)
internal val Red80 = Color(0xFFF2B8B5)
