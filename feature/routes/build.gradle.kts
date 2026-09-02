/**
 * The Routes tab: collections, the routes inside one, and the route editor (SPEC §10.2).
 *
 * Three destinations in one module because they are one flow — a collection is meaningless
 * without its routes, and the editor is where nearly all of the time in this app is spent.
 * Splitting them would mean routing every navigation between them through `:core:navigation` for
 * no boundary anyone benefits from.
 */
plugins {
    id("mockserver.android.feature")
}

android {
    namespace = "com.rio.mockserver.feature.routes"
}
