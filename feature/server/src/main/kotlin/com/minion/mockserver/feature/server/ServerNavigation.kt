package com.minion.mockserver.feature.server

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.minion.mockserver.core.navigation.QrRoute
import com.minion.mockserver.core.navigation.ServerRoute
import com.minion.mockserver.feature.server.qr.QrScreen
import com.minion.mockserver.feature.server.status.ServerScreen

/**
 * This module's entire public API.
 *
 * Everything else in the feature is `internal`. Without that, `:app` could construct
 * [ServerViewModel] directly and the module boundary would be decorative.
 *
 * Navigation arrives as lambdas rather than a `NavHostController`: handing a feature the
 * controller lets it navigate anywhere, which is the same as letting it know about every other
 * feature.
 */
fun NavGraphBuilder.serverScreen(
    onNavigateToQr: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    composable<ServerRoute> {
        ServerScreen(
            onNavigateToQr = onNavigateToQr,
            onNavigateToSettings = onNavigateToSettings,
            onRequestNotificationPermission = onRequestNotificationPermission,
        )
    }
}

/** The full-screen QR display, with the screen brightness boosted while it is shown. */
fun NavGraphBuilder.qrScreen(onBack: () -> Unit) {
    composable<QrRoute> { backStackEntry ->
        // Type-safe navigation deserialises the route back into the same object that was
        // navigated with — no argument keys to misspell.
        val route: QrRoute = backStackEntry.toRoute()
        QrScreen(url = route.url, onBack = onBack)
    }
}
