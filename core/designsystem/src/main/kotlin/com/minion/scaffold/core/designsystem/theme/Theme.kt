package com.minion.scaffold.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    error = Red40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    error = Red80,
)

/**
 * The single theme wrapper. Everything the app draws sits inside exactly one of these, applied
 * once in `MainActivity`.
 *
 * Features read colour, type and shape from `MaterialTheme`; they never see [LightColorScheme] or
 * the raw palette. That indirection is the whole point — it is what makes dark mode and dynamic
 * colour work without touching a single screen.
 *
 * @param darkTheme whether to use the dark scheme; follows the system by default
 * @param dynamicColor whether to derive colours from the user's wallpaper on Android 12+.
 *   On by default because users expect it, but a product with strong brand colour should turn it
 *   off — dynamic colour discards the palette above entirely.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
