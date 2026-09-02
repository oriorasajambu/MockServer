package com.rio.mockserver.feature.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.rio.mockserver.core.navigation.CollectionDetailRoute
import com.rio.mockserver.core.navigation.CollectionsRoute
import com.rio.mockserver.core.navigation.RouteEditorRoute
import com.rio.mockserver.feature.routes.collections.CollectionsScreen
import com.rio.mockserver.feature.routes.detail.CollectionDetailScreen
import com.rio.mockserver.feature.routes.editor.RouteEditorScreen

/**
 * This module's entire public API: three destinations, three lambdas.
 *
 * Everything else is `internal`, so `:app` cannot construct a ViewModel or reach a screen except
 * through these.
 */
fun NavGraphBuilder.collectionsScreen(onNavigateToCollection: (String) -> Unit) {
    composable<CollectionsRoute> {
        CollectionsScreen(onNavigateToCollection = onNavigateToCollection)
    }
}

fun NavGraphBuilder.collectionDetailScreen(onNavigateToEditor: (String?) -> Unit) {
    composable<CollectionDetailRoute> {
        CollectionDetailScreen(onNavigateToEditor = onNavigateToEditor)
    }
}

fun NavGraphBuilder.routeEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrafficEntry: (String) -> Unit,
) {
    composable<RouteEditorRoute> {
        RouteEditorScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToTrafficEntry = onNavigateToTrafficEntry,
        )
    }
}
