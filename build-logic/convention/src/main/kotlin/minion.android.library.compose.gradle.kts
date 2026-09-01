import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Adds Compose on top of [minion.android.library].
 *
 * For modules that draw but do not own a ViewModel — `:core:designsystem` and `:core:ui`.
 * Feature modules want [minion.android.feature] instead.
 */

plugins {
    id("minion.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    // For Showkase's processor. Applying it here is harmless where minion.android.hilt also
    // applies it — Gradle applies a plugin once regardless of how many times it is requested.
    id("com.google.devtools.ksp")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    buildFeatures {
        compose = true
    }
}

ksp {
    // Showkase rejects a private @Preview outright, because it cannot call one. Without this,
    // a developer's throwaway preview fails the build of a module that has nothing to do with
    // the catalog. With it, private previews are simply skipped.
    //
    // A preview that IS meant for the catalog must therefore be `internal`, not `private`.
    arg("skipPrivatePreviews", "true")
}

dependencies {
    val composeBom = platform(libs.findLibrary("compose-bom").get())
    add("implementation", composeBom)
    add("androidTestImplementation", composeBom)

    add("implementation", libs.findBundle("compose").get())
    add("debugImplementation", libs.findLibrary("compose-debug").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
    add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit").get())

    // Showkase, for every module that draws. Declared here rather than per-module so a new feature
    // contributes its previews to the catalog without anyone having to remember — the failure mode
    // otherwise is silent, a module simply missing from the browser.
    //
    // Debug only: the processor's output and the browser have no place in a release build.
    add("implementation", libs.findLibrary("showkase-annotation").get())
    add("debugImplementation", libs.findLibrary("showkase").get())
    add("kspDebug", libs.findLibrary("showkase-processor").get())
}
