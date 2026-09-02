package com.minion.mockserver.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.mockserver.core.navigation.ChaosEditorRoute
import com.minion.mockserver.core.navigation.ImportRoute
import com.minion.mockserver.core.navigation.SettingsRoute
import com.minion.mockserver.feature.settings.chaos.ChaosEditorScreen
import com.minion.mockserver.feature.settings.importexport.ImportScreen

/** This module's entire public API. Everything else is `internal`. */
fun NavGraphBuilder.settingsScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToChaosEditor: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateToImport = onNavigateToImport,
            onNavigateToChaosEditor = onNavigateToChaosEditor,
        )
    }
}

fun NavGraphBuilder.importScreen(onNavigateToCollection: (String) -> Unit) {
    composable<ImportRoute> { ImportScreen(onNavigateToCollection = onNavigateToCollection) }
}

fun NavGraphBuilder.chaosEditorScreen() {
    composable<ChaosEditorRoute> { ChaosEditorScreen() }
}
