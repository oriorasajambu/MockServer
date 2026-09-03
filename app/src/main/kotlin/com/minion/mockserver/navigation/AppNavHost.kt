package com.minion.mockserver.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.minion.mockserver.core.navigation.ChaosEditorRoute
import com.minion.mockserver.core.navigation.CollectionDetailRoute
import com.minion.mockserver.core.navigation.CollectionsRoute
import com.minion.mockserver.core.navigation.ImportRoute
import com.minion.mockserver.core.navigation.QrRoute
import com.minion.mockserver.core.navigation.RouteEditorRoute
import com.minion.mockserver.core.navigation.ServerRoute
import com.minion.mockserver.core.navigation.SettingsRoute
import com.minion.mockserver.core.navigation.TrafficDetailRoute
import com.minion.mockserver.core.navigation.TrafficRoute
import com.minion.mockserver.feature.routes.collectionDetailScreen
import com.minion.mockserver.feature.routes.collectionsScreen
import com.minion.mockserver.feature.routes.routeEditorScreen
import com.minion.mockserver.feature.server.qrScreen
import com.minion.mockserver.feature.server.serverScreen
import com.minion.mockserver.feature.settings.chaosEditorScreen
import com.minion.mockserver.feature.settings.importScreen
import com.minion.mockserver.feature.settings.settingsScreen
import com.minion.mockserver.feature.traffic.trafficDetailScreen
import com.minion.mockserver.feature.traffic.trafficScreen

/**
 * The app's single navigation graph, assembled from every feature's entry point.
 *
 * `:app` is the only module allowed to see all the features at once — which is what lets features
 * stay ignorant of each other. Each contributes a `NavGraphBuilder` extension and nothing else;
 * its ViewModel, contract and screens stay `internal` behind it.
 *
 * Navigation lambdas are passed *down*, never a [NavHostController]. Handing a feature the
 * controller lets it navigate anywhere, which is the same as letting it know about every other
 * feature.
 *
 * The one cross-feature edge worth naming: an unmatched traffic entry opens the route editor
 * (SPEC §4.5), and Traffic knows nothing about Routes. Both know only the route contract in
 * `:core:navigation`, and this file is where the two are joined.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Hidden on full-screen destinations: the QR is meant to fill the screen, and the
            // editor needs every pixel it can get on a phone.
            if (currentDestination?.isTopLevel() == true) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    BottomNavItem.entries.forEach { item ->
                        val selected = currentDestination.hasRouteOf(item)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(item) },
                            icon = {
                                Icon(
                                    imageVector = ImageVector.vectorResource(
                                        if (selected) item.selectedIconRes else item.iconRes,
                                    ),
                                    // The label under it names the destination; announcing the
                                    // glyph too would read every tab twice.
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                // The accent pill behind the selected tab. Elsewhere this design
                                // does not flood an area with accent; at 52x26 it is a mark.
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ServerRoute,
            modifier = Modifier.padding(padding),
        ) {
            serverScreen(
                onNavigateToQr = { url -> navController.navigate(QrRoute(url)) },
                onNavigateToSettings = { navController.navigateToTab(BottomNavItem.SETTINGS) },
                // Pushed rather than switched to as a tab: this is a drill-down from a counter,
                // so back belongs on the Server tab the question was asked from, and a tab switch
                // would restore the log's saved unfiltered state over the filter just asked for.
                onNavigateToUnmatched = {
                    navController.navigate(TrafficRoute(unmatchedOnly = true))
                },
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
            qrScreen(onBack = navController::popBackStack)

            collectionsScreen(
                onNavigateToCollection = { id -> navController.navigate(CollectionDetailRoute(id)) },
            )
            collectionDetailScreen(
                onNavigateBack = navController::popBackStack,
                onNavigateToImport = { id -> navController.navigate(ImportRoute(id)) },
                onNavigateToEditor = { routeId ->
                    val collectionId = navController.currentCollectionId() ?: return@collectionDetailScreen
                    navController.navigate(RouteEditorRoute(collectionId, routeId))
                },
            )
            routeEditorScreen(
                onNavigateBack = navController::popBackStack,
                onNavigateToChaosEditor = { navController.navigate(ChaosEditorRoute()) },
                onNavigateToTrafficEntry = { entryId ->
                    navController.navigate(TrafficDetailRoute(entryId))
                },
            )

            trafficScreen(
                onNavigateToDetail = { entryId -> navController.navigate(TrafficDetailRoute(entryId)) },
                onNavigateToRouteEditor = { entryId ->
                    navController.navigate(
                        RouteEditorRoute(collectionId = "", routeId = null, prefillFromEntryId = entryId),
                    )
                },
            )
            trafficDetailScreen(
                onNavigateBack = navController::popBackStack,
                onNavigateToRouteEditor = { entryId ->
                    navController.navigate(
                        RouteEditorRoute(collectionId = "", routeId = null, prefillFromEntryId = entryId),
                    )
                },
            )

            settingsScreen(
                onNavigateToImport = { navController.navigate(ImportRoute()) },
                onNavigateToChaosEditor = { navController.navigate(ChaosEditorRoute()) },
            )
            importScreen(
                onNavigateToCollection = { id -> navController.navigate(CollectionDetailRoute(id)) },
                onNavigateBack = navController::popBackStack,
            )
            chaosEditorScreen(onNavigateBack = navController::popBackStack)
        }
    }
}

/**
 * Switches tabs without stacking them.
 *
 * `popUpTo(start)` with `saveState`, so each tab keeps its own scroll position and back goes to
 * the Server tab rather than walking backwards through every tab visited.
 */
private fun NavHostController.navigateToTab(item: BottomNavItem) {
    navigate(item.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The collection whose detail screen is on top, for the editor's route argument. */
private fun NavHostController.currentCollectionId(): String? = runCatching {
    currentBackStackEntry?.toRoute<CollectionDetailRoute>()?.collectionId
}.getOrNull()

private fun NavDestination.isTopLevel(): Boolean =
    BottomNavItem.entries.any { hasRouteOf(it) }

private fun NavDestination.hasRouteOf(item: BottomNavItem): Boolean =
    when (item) {
        BottomNavItem.SERVER -> hasRoute(ServerRoute::class)
        BottomNavItem.ROUTES -> hasRoute(CollectionsRoute::class)
        BottomNavItem.TRAFFIC -> hasRoute(TrafficRoute::class)
        BottomNavItem.SETTINGS -> hasRoute(SettingsRoute::class)
    }
