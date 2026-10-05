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

val releaseStoreFile = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningEnabled = releaseSigningValues.any { !it.isNullOrBlank() }
require(!releaseSigningEnabled || releaseSigningValues.all { !it.isNullOrBlank() }) {
    "Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD."
}

android {
    namespace = "com.minion.mockserver"

    defaultConfig {
        applicationId = "com.minion.mockserver"
        versionCode = providers.gradleProperty("appVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("1.0").get()
    }

    if (releaseSigningEnabled) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
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
