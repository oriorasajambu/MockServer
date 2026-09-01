import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Convention for pure-Kotlin modules — `:core:domain` and nothing else, for now.
 *
 * Deliberately does NOT apply the Android plugin. That is the boundary rule made mechanical: a
 * stray `import android.*` or `import androidx.compose.*` in the domain layer fails to compile
 * rather than surviving to code review.
 */

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// Must match the Java level above, and must match what the Android modules compile to. Left at
// the daemon's default (21) this module would emit class files the Android modules cannot read.
// AGP's built-in Kotlin aligns this automatically; the standalone JVM plugin does not.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    add("implementation", libs.findLibrary("kotlin-stdlib").get())
    add("implementation", libs.findLibrary("coroutines-core").get())
    add("testImplementation", libs.findBundle("unit-test-jvm").get())
}
