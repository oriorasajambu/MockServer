package com.minion.mockserver.feature.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.mockserver.core.navigation.CollectionDetailRoute
import com.minion.mockserver.core.navigation.CollectionsRoute
import com.minion.mockserver.core.navigation.RouteEditorRoute
import com.minion.mockserver.feature.routes.collections.CollectionsScreen
import com.minion.mockserver.feature.routes.detail.CollectionDetailScreen
import com.minion.mockserver.feature.routes.editor.RouteEditorScreen

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

fun NavGraphBuilder.collectionDetailScreen(
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToImport: (String) -> Unit,
    onNavigateToCollection: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<CollectionDetailRoute> {
        CollectionDetailScreen(
            onNavigateToEditor = onNavigateToEditor,
            onNavigateToImport = onNavigateToImport,
            onNavigateToCollection = onNavigateToCollection,
            onNavigateBack = onNavigateBack,
        )
    }
}

fun NavGraphBuilder.routeEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrafficEntry: (String) -> Unit,
    onNavigateToChaosEditor: () -> Unit,
) {
    composable<RouteEditorRoute> {
        RouteEditorScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToTrafficEntry = onNavigateToTrafficEntry,
            onNavigateToChaosEditor = onNavigateToChaosEditor,
        )
    }
}
