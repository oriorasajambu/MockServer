pluginManagement {
    // Compiles the convention plugins before anything else configures. Everything modular
    // depends on this being here.
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MockServer"

// The only module that may see both a feature and a repository implementation: it assembles the
// navigation graph and the DI graph, and does nothing else.
include(":app")

// Foundation — pure Kotlin, depends on nothing.
include(":core:common")

// Domain — pure Kotlin. Models, repository interfaces, use cases.
include(":core:domain")

// Route contracts — pure Kotlin. The only channel through which one feature reaches another.
include(":core:navigation")

// UI
include(":core:designsystem")
include(":core:ui")

// The embedded server. Pure Kotlin: Ktor CIO has no Android dependency, so keeping the engine
// out of an Android module means the request pipeline can be exercised by `testApplication`
// on the JVM, with no device and no emulator (SPEC §14).
include(":core:server")

// Import/export wire formats. Pure Kotlin, so every importer is testable against golden files.
include(":core:importexport")

// Data
include(":core:data")

// Test infrastructure, consumed via testImplementation.
include(":core:testing")

// Features — one per screen area (SPEC §10.1). The four bottom-bar tabs plus the full-screen
// destinations they push to.
include(":feature:server")
include(":feature:routes")
include(":feature:traffic")
include(":feature:settings")

