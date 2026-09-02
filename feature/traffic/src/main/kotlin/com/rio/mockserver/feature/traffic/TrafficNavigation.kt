package com.rio.mockserver.feature.traffic

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.rio.mockserver.core.navigation.TrafficDetailRoute
import com.rio.mockserver.core.navigation.TrafficRoute

/** This module's entire public API. Everything else is `internal`. */
fun NavGraphBuilder.trafficScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToRouteEditor: (String) -> Unit,
) {
    composable<TrafficRoute> {
        TrafficScreen(
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToRouteEditor = onNavigateToRouteEditor,
        )
    }
}

/** The full-screen detail for one entry. */
fun NavGraphBuilder.trafficDetailScreen(onNavigateToRouteEditor: (String) -> Unit) {
    composable<TrafficDetailRoute> {
        TrafficDetailScreen(onNavigateToRouteEditor = onNavigateToRouteEditor)
    }
}
