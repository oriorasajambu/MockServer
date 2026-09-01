package com.minion.scaffold

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity. Installs the splash screen, applies the theme, hands off to the nav graph.
 *
 * One activity, many composable destinations — screens are added to [AppNavHost], never as new
 * activities. A second activity would mean a second `ViewModelStore`, a second back stack and a
 * second place to remember to apply the theme.
 *
 * Nothing screen-specific belongs here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, and before setContent: the splash screen has to be installed
        // while the activity's theme is still the splash theme, or the system swaps to the app
        // theme first and the transition flickers.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavHost()
            }
        }
    }
}
