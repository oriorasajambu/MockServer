package com.rio.mockserver

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rio.mockserver.core.designsystem.theme.AppTheme
import com.rio.mockserver.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity. Installs the splash screen, applies the theme, hands off to the nav graph.
 *
 * One activity, many composable destinations. A second activity would mean a second
 * `ViewModelStore`, a second back stack, and a second place to remember to apply the theme.
 *
 * The notification permission is requested here rather than in a feature, because the launcher
 * has to be registered before the activity is started and a feature cannot do that. It is asked
 * for when the user first starts the server — not on launch (SPEC §8.1). Denial does not stop the
 * server: what is lost is the ongoing notification, and with it the ability to stop the server
 * without opening the app.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Granted or not, the server still runs. The in-app banner explains the difference. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate and before setContent: the splash has to be installed while the
        // activity's theme is still the splash theme, or the system swaps first and it flickers.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavHost(onRequestNotificationPermission = ::requestNotificationPermission)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
