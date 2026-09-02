package com.minion.mockserver.feature.traffic

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.mockserver.core.navigation.TrafficDetailRoute
import com.minion.mockserver.core.navigation.TrafficRoute
import com.minion.mockserver.feature.traffic.detail.TrafficDetailScreen
import com.minion.mockserver.feature.traffic.log.TrafficScreen

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
fun NavGraphBuilder.trafficDetailScreen(
    onNavigateToRouteEditor: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<TrafficDetailRoute> {
        TrafficDetailScreen(
            onNavigateToRouteEditor = onNavigateToRouteEditor,
            onNavigateBack = onNavigateBack,
        )
    }
}
