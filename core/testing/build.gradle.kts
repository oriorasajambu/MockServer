/**
 * Shared test infrastructure: dispatcher rules, fakes, Turbine helpers.
 *
 * Consumed as `testImplementation(project(":core:testing"))` — which the
 * `minion.android.feature` convention adds automatically, so a new feature's tests get it
 * without anyone having to remember.
 *
 * Note this module's sources live in `src/main`, not `src/test`. A module's test source set is
 * not published to its consumers; only `main` is. That is also why the test libraries below are
 * `api` rather than `testImplementation` — a consumer that gets `MainDispatcherRule` must also
 * get the JUnit `TestWatcher` it extends.
 */
plugins {
    id("minion.android.library")
}

android {
    namespace = "com.minion.scaffold.core.testing"
}

dependencies {
    api(libs.test.junit)
    api(libs.test.coroutines)
    api(libs.test.turbine)
    api(libs.test.mockk)
}
