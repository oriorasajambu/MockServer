import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Convention for the single application module.
 *
 * The counterpart to [minion.android.library]: same compileSdk / minSdk / Java level /
 * desugaring / test wiring, but applying `com.android.application` and adding what only the
 * host needs — Compose, Hilt's code generation, Showkase's browser.
 *
 * `:app` sets its own `applicationId`, version and `buildConfigField`s, and lists its module
 * dependencies. Everything else is here so the two build files cannot drift apart.
 */

plugins {
    // AGP 9 has built-in Kotlin support; applying 'org.jetbrains.kotlin.android' alongside it is
    // now a hard error. See https://kotl.in/gradle/agp-built-in-kotlin
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

// Precompiled script plugins get no type-safe `libs` accessor, so the catalog is resolved by hand.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // The library modules enable this, and a consumer must match. It was previously absent
        // here, which made the coreLibraryDesugaring dependency below inert.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // :app is where environment-dependent constants (the base URL) enter the graph, so it is
        // the one module that needs a generated BuildConfig.
        buildConfig = true
    }
}

dependencies {
    add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk").get())
    add("implementation", libs.findLibrary("kotlin-stdlib").get())
    add("implementation", libs.findBundle("reactive").get())
    add("implementation", libs.findBundle("jetpack").get())
    add("implementation", libs.findBundle("lifecycle").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())

    val composeBom = platform(libs.findLibrary("compose-bom").get())
    add("implementation", composeBom)
    add("androidTestImplementation", composeBom)

    add("implementation", libs.findBundle("compose").get())
    add("debugImplementation", libs.findLibrary("compose-debug").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
    add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit").get())

    add("implementation", libs.findLibrary("hilt-android").get())
    add("ksp", libs.findLibrary("hilt-compiler").get())
    add("ksp", libs.findLibrary("hilt-androidx-compiler").get())

    // Showkase, matching minion.android.library.compose. Debug only: the processor's output and
    // the browser have no place in a release build.
    add("implementation", libs.findLibrary("showkase-annotation").get())
    add("debugImplementation", libs.findLibrary("showkase").get())
    add("kspDebug", libs.findLibrary("showkase-processor").get())

    add("testImplementation", libs.findBundle("unit-test").get())
    add("androidTestImplementation", libs.findBundle("instrumentation-test").get())
}

/**
 * `check` compiles the instrumented tests, without needing a device.
 *
 * Same guard as [minion.android.library], repeated because `:app` does not apply that convention.
 * Neither `testDebugUnitTest` nor `assembleDebug` builds `androidTest` sources, so they can stop
 * compiling and nothing says so.
 */
tasks.matching { it.name == "check" }.configureEach {
    dependsOn("compileDebugAndroidTestKotlin")
}
