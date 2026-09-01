/**
 * UI tier — the MVI base, and business-aware composites.
 *
 * Two things live here. First, the presentation plumbing every feature extends: `MviViewModel`,
 * `ObserveAsEvents`, and the mapping from a `DomainError` to a string resource — the UI is the
 * only layer allowed to touch `R.string`. Second, widgets shared across features that render
 * domain models, built from `:core:designsystem` atoms.
 *
 * May import `:core:domain`; may not import any repository implementation.
 */
plugins {
    id("minion.android.library.compose")
}

android {
    namespace = "com.minion.scaffold.core.ui"
}

dependencies {
    // api, not implementation: MviViewModel's type parameters are UiState/UiIntent/UiEffect from
    // :core:common, so every feature that extends it needs those types on its own classpath.
    api(project(":core:common"))
    api(project(":core:designsystem"))
    implementation(project(":core:domain"))

    // MviViewModel is a ViewModel, and ObserveAsEvents needs repeatOnLifecycle.
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
