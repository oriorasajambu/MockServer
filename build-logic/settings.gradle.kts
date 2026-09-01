// build-logic is a separate, included build: it compiles the convention plugins that the main
// build then applies. It therefore needs its own repository and version-catalog wiring — the
// root settings.gradle.kts does not reach in here.

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            // One catalog for both builds. Versions declared for the app are the same versions
            // the convention plugins compile against, so the two can never drift apart.
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
