package com.minion.mockserver.core.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination in the app (SPEC §10.1).
 *
 * Declared in a module that owns no UI, so a feature can navigate to another feature's screen
 * while depending only on the contract. `:feature:traffic` constructs a [RouteEditorRoute];
 * `:feature:routes` registers a composable for it; neither knows the other exists.
 *
 * Arguments are ids and primitives only. A route carrying a `MockRoute` would make this module
 * depend on `:core:domain`, and would require the whole object to survive process death in the
 * saved-state bundle.
 */
sealed interface AppRoute

/** Start/stop, the address block, the QR, and the live counters. The start destination. */
@Serializable
data object ServerRoute : AppRoute

/** The collection list — the top level of the Routes tab. */
@Serializable
data object CollectionsRoute : AppRoute

/** The routes inside one collection. */
@Serializable
data class CollectionDetailRoute(val collectionId: String) : AppRoute

/**
 * The route editor.
 *
 * [routeId] is null for a new route. [prefillFromEntryId] carries a traffic entry the draft is
 * being built from (SPEC §4.5) — an id rather than the entry itself, because the log is in
 * memory and the editor can read it back from there.
 */
@Serializable
data class RouteEditorRoute(
    val collectionId: String,
    val routeId: String? = null,
    val prefillFromEntryId: String? = null,
) : AppRoute

/**
 * The live traffic log.
 *
 * @param unmatchedOnly opens the log with the unmatched filter already applied. Set when arriving
 *   from the Server tab's unmatched counter, which is a question the filtered log answers — the
 *   count is only useful if reaching the requests behind it takes one tap.
 */
@Serializable
data class TrafficRoute(val unmatchedOnly: Boolean = false) : AppRoute

/** One traffic entry, request and response. */
@Serializable
data class TrafficDetailRoute(val entryId: String) : AppRoute

/** Port, API key, chaos, log size, import/export. */
@Serializable
data object SettingsRoute : AppRoute

/** The chaos profile editor. [profileId] is null for a new profile. */
@Serializable
data class ChaosEditorRoute(val profileId: String? = null) : AppRoute

/** Pick a file, preview what is in it, resolve conflicts, commit. */
@Serializable
data object ImportRoute : AppRoute

/** The full-screen QR code, with the brightness boosted. */
@Serializable
data class QrRoute(val url: String) : AppRoute
