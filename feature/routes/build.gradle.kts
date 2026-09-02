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

dependencies {
    // "Test this route" issues a real loopback request through the running server, which is a
    // platform mechanism with no domain meaning; see the note in :feature:server's build file.
    implementation(project(":core:data"))
}
