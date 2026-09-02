/**
 * The host. Nav graph, DI aggregation, `Application`, `MainActivity` — nothing else.
 *
 * Everything shared with the library modules (compileSdk, Java level, desugaring, Compose, Hilt,
 * Showkase, test wiring) lives in the `mockserver.android.application` convention so this file and
 * `mockserver.android.library` cannot drift apart. What stays here is what is genuinely unique to
 * the application: identity, versioning, environment constants, and the module graph.
 */
plugins {
    id("mockserver.android.application")
}

android {
    namespace = "com.rio.mockserver"

    defaultConfig {
        applicationId = "com.rio.mockserver"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Off for now. The app is sideloaded to a known set of devices, and the first thing
            // minification would break is Room's and kotlinx.serialization's generated code —
            // worth turning on deliberately, with the rules written and tested, not by default.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    // :app is the only module that sees both the features and the implementations. It assembles
    // the navigation graph and the DI graph, and does nothing else.
    implementation(project(":core:data"))
    implementation(project(":core:server"))
    implementation(project(":core:importexport"))

    implementation(project(":feature:server"))
    implementation(project(":feature:routes"))
    implementation(project(":feature:traffic"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.splashscreen)

    testImplementation(project(":core:testing"))
}
