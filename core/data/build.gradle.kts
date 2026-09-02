/**
 * Data tier — persistence, the platform, and the assembly point.
 *
 * Repository implementations that satisfy `:core:domain`'s interfaces, Room and DataStore, and
 * the Android half of running a server: the foreground service, the notification, the WifiLock,
 * NSD advertisement, interface enumeration and QR encoding.
 *
 * The socket itself is not here — it lives in `:core:server`, which is pure Kotlin so the request
 * pipeline can be tested without a device. This module owns the platform; that one owns the wire.
 *
 * Never imports Compose or `:core:ui`.
 */
plugins {
    id("mockserver.android.library")
    id("mockserver.android.hilt")
    alias(libs.plugins.kotlin.serializable)
}

android {
    namespace = "com.rio.mockserver.core.data"
}

ksp {
    // Room writes the schema JSON here so a migration can be diffed against the previous version.
    // Without it, `exportSchema = true` is a build warning and migrations are written blind.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // api, not implementation: repository implementations return AppResult<T> and the mappers
    // produce domain models, so both are part of this module's public surface.
    api(project(":core:common"))
    api(project(":core:domain"))

    // The Hilt component generated in :app binds MockServerRepository to the implementation in
    // :core:server, so that type must be on :app's compile classpath.
    api(project(":core:server"))
    api(project(":core:importexport"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.data.store)
    implementation(libs.kotlin.serializable)

    // Encode only. `zxing:android-core` and the embedded scanner — and the camera permission they
    // bring — are deliberately absent (SPEC §8.5).
    implementation(libs.zxing.core)

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.service)

    testImplementation(libs.room.testing)
}
