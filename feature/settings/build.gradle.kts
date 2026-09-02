/**
 * The Settings tab: port, API key, global chaos, log size, import/export, battery optimisation
 * and the chaos RNG seed (SPEC §10.1).
 */
plugins {
    id("mockserver.android.feature")
}

android {
    namespace = "com.rio.mockserver.feature.settings"
}

dependencies {
    // Battery-optimisation exemption and the storage-access framework are platform mechanisms
    // this screen drives directly; see the note in :feature:server's build file.
    implementation(project(":core:data"))
}
