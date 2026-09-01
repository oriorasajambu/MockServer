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
        debug {
            // :app is the only module that knows which environment it is talking to. :core:network
            // receives this through the @BaseUrl qualifier and never reads BuildConfig itself —
            // a library module reading the app's BuildConfig is how a "core" module quietly
            // becomes app-specific.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL", "\"https://example.com/\"")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    // Features are added here as they are created — this is the one place that sees all of them.
    // implementation(project(":feature:home"))

    implementation(libs.androidx.splashscreen)

    testImplementation(project(":core:testing"))
}
