package com.minion.mockserver.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.minion.mockserver.R
import com.minion.mockserver.core.navigation.AppRoute
import com.minion.mockserver.core.navigation.CollectionsRoute
import com.minion.mockserver.core.navigation.ServerRoute
import com.minion.mockserver.core.navigation.SettingsRoute
import com.minion.mockserver.core.navigation.TrafficRoute
import com.minion.mockserver.core.designsystem.R as DesignR

/**
 * The four bottom-bar destinations (SPEC §10.1).
 *
 * Four, and no overflow. Every one of them is somewhere a user goes repeatedly during a single
 * debugging session — start the server, edit a rule, watch what arrived, change a setting — and a
 * fifth would mean one of them is not.
 *
 * Icons are carried as resource ids rather than as `ImageVector`s. `MockIcons` resolves a vector
 * through `vectorResource`, which is `@Composable` and so cannot be evaluated in an enum
 * constructor; the bar reads these inside composition instead. Each destination has two, because
 * the selected tab takes the filled weight — the pill behind it is the loud signal, and the
 * filled glyph is what keeps it legible against that fill.
 */
internal enum class BottomNavItem(
    val route: AppRoute,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int,
) {
    SERVER(
        ServerRoute,
        R.string.tab_server,
        DesignR.drawable.ic_ph_play,
        DesignR.drawable.ic_ph_play_fill,
    ),
    ROUTES(
        CollectionsRoute,
        R.string.tab_routes,
        DesignR.drawable.ic_ph_list_dashes,
        DesignR.drawable.ic_ph_list_dashes_fill,
    ),
    TRAFFIC(
        TrafficRoute(),
        R.string.tab_traffic,
        DesignR.drawable.ic_ph_pulse,
        DesignR.drawable.ic_ph_pulse_fill,
    ),
    SETTINGS(
        SettingsRoute,
        R.string.tab_settings,
        DesignR.drawable.ic_ph_gear,
        DesignR.drawable.ic_ph_gear_fill,
    ),
}
