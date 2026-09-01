package com.rio.mockserver.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rio.mockserver.core.navigation.HomeRoute

/**
 * The app's single navigation graph, assembled from every feature's entry point.
 *
 * `:app` is the only module allowed to see all the features at once — that is what lets features
 * stay ignorant of each other. Each contributes a `NavGraphBuilder` extension and nothing else;
 * its ViewModel, contract and screen stay `internal` behind it.
 *
 * Navigation lambdas are passed *down* into the feature, never a [NavHostController]. Handing a
 * feature the controller lets it navigate anywhere, which is the same as letting it know about
 * every other feature.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        // Each feature registers itself here with one line, e.g.:
        //
        //     homeScreen(
        //         onNavigateToDetail = { id -> navController.navigate(DetailRoute(id)) },
        //     )
        //
        // `python scripts/scaffold_feature.py --name Home` generates that extension.
        composable<HomeRoute> { PlaceholderScreen() }
    }
}

/** Stands in for the first real feature so the scaffold runs. Delete it when one exists. */
@Composable
private fun PlaceholderScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Add a feature module to replace this screen.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
