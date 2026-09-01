package com.minion.scaffold.core.navigation

import kotlinx.serialization.Serializable

/**
 * Marker for every destination in the app.
 *
 * Routes are declared here, in a module that owns no UI, so that a feature can navigate to
 * another feature's screen while depending only on the contract. `:feature:a` constructs a
 * [AppRoute]; `:feature:b` registers a composable for it; neither knows the other exists.
 *
 * Type-safe navigation (navigation-compose 2.8+) serialises these directly — no string route
 * templates, no argument keys to misspell, and `savedStateHandle.toRoute<T>()` in the ViewModel
 * gives back the same object.
 *
 * Route arguments must be ids and primitives, never domain models. A route carrying a `User`
 * would mean `:core:navigation` depends on `:core:domain`, and that the whole object had to
 * survive process death in the saved-state bundle.
 */
sealed interface AppRoute

/** The start destination. Replace with the app's real entry screen. */
@Serializable
data object HomeRoute : AppRoute
