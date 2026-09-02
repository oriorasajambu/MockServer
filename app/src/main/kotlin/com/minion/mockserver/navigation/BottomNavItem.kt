package com.minion.mockserver.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import com.minion.mockserver.R
import com.minion.mockserver.core.navigation.AppRoute
import com.minion.mockserver.core.navigation.CollectionsRoute
import com.minion.mockserver.core.navigation.ServerRoute
import com.minion.mockserver.core.navigation.SettingsRoute
import com.minion.mockserver.core.navigation.TrafficRoute

/**
 * The four bottom-bar destinations (SPEC §10.1).
 *
 * Four, and no overflow. Every one of them is somewhere a user goes repeatedly during a single
 * debugging session — start the server, edit a rule, watch what arrived, change a setting — and a
 * fifth would mean one of them is not.
 */
internal enum class BottomNavItem(
    val route: AppRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SERVER(ServerRoute, R.string.tab_server, Icons.Filled.PlayArrow),
    ROUTES(CollectionsRoute, R.string.tab_routes, Icons.AutoMirrored.Filled.List),
    TRAFFIC(TrafficRoute(), R.string.tab_traffic, Icons.Filled.Share),
    SETTINGS(SettingsRoute, R.string.tab_settings, Icons.Filled.Settings),
}
