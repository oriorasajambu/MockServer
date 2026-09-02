/**
 * The Server tab: start/stop, the address block, the QR code, and the live counters (SPEC §10.3).
 */
plugins {
    id("mockserver.android.feature")
}

android {
    namespace = "com.minion.mockserver.feature.server"
}

dependencies {
    // The service is started from here, and the QR bitmap comes from :core:data's encoder. Both
    // are platform mechanisms with no domain meaning, so there is no repository interface to hide
    // them behind — this is the documented exception to a feature not seeing :core:data.
    implementation(project(":core:data"))
}
