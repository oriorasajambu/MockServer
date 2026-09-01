import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Hilt + KSP wiring, applied on its own so that data-layer modules can have DI without dragging
 * in Compose.
 */

plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.findLibrary("hilt-android").get())
    add("ksp", libs.findLibrary("hilt-compiler").get())
    add("ksp", libs.findLibrary("hilt-androidx-compiler").get())
}
