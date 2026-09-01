import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * The convention every `:feature:*` module applies: Compose + Hilt + navigation + the MVI plumbing.
 *
 * A feature module gets `:core:common`, `:core:domain`, `:core:navigation`, `:core:designsystem`,
 * `:core:ui` (which carries the `MviViewModel` base) and `:core:network` for free.
 *
 * `:core:network` is here because a feature owns its whole vertical slice: its `*Api`, its DTOs,
 * its mappers and its repository implementation, all `internal`, bound by an `internal` Hilt
 * module. What it gets from `:core:network` is the shared `Retrofit` and the error boundary
 * (`safeCall`), not another feature's data. `:core:data` is deliberately absent — that module is
 * for data genuinely shared between features, and a feature reaching into it would be reaching
 * past the repository interface it is supposed to depend on.
 *
 * `:core:navigation` is how a feature reaches another feature's screen without depending on it —
 * both sides know only the route contract.
 */

plugins {
    id("minion.android.library.compose")
    id("minion.android.hilt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", project(":core:common"))
    add("implementation", project(":core:domain"))
    add("implementation", project(":core:navigation"))
    add("implementation", project(":core:designsystem"))
    add("implementation", project(":core:ui"))
    add("implementation", project(":core:network"))

    add("implementation", libs.findBundle("lifecycle").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    add("implementation", libs.findLibrary("compose-hilt-navigation").get())
    add("implementation", libs.findLibrary("compose-navigation").get())

    // Wired here rather than per-module so a new feature's tests get MainDispatcherRule and the
    // shared fakes without anyone having to remember.
    add("testImplementation", project(":core:testing"))
}
