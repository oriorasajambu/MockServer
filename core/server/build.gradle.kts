/**
 * The embedded server. The only module that imports Ktor.
 *
 * Pure Kotlin, deliberately — Ktor CIO has no Android dependency, so keeping the engine out of an
 * Android module buys two things. The request pipeline can be exercised end to end by Ktor's
 * `testApplication` on a plain JVM, with no device and no emulator (SPEC §14); and the "no
 * Android imports below the presentation layer" rule stays a compile error rather than a review
 * comment.
 *
 * The Android half of running a server — the foreground service, the notification, the WifiLock,
 * NSD — lives in `:core:data`, which owns the platform. This module owns the socket.
 *
 * Injection is by `javax.inject`, not Hilt: the annotations are the same ones Hilt reads, and
 * `@Singleton class MockServerEngine @Inject constructor(...)` is assembled by the component
 * generated in `:app` without this module ever seeing the Android DI runtime.
 */
plugins {
    id("mockserver.jvm.library")
}

dependencies {
    // api, not implementation: MockServerRepositoryImpl's signatures are declared in terms of
    // ServerConfig, RoutingSnapshot and ServerStatus, all of which come from :core:domain.
    api(project(":core:domain"))

    implementation(libs.bundles.server)

    // `testApplication` plus the in-VM client it hands back, which is how the request pipeline is
    // exercised end to end with no device and no bound socket (SPEC §14, tier 2).
    testImplementation(libs.ktor.server.test.host)
}
