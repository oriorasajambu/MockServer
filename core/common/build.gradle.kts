/**
 * Foundation tier. Pure Kotlin, depends on nothing.
 *
 * Holds the vocabulary every other module speaks: `AppResult`, `DomainError`, the dispatcher
 * qualifiers, and the `UiState`/`UiIntent`/`UiEffect` marker interfaces that the MVI contracts
 * implement.
 *
 * The `MviViewModel` base class does NOT live here — it needs androidx.lifecycle, and this
 * module must stay consumable by pure-Kotlin `:core:domain`. It lives in `:core:ui` instead.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    // api, not implementation: the dispatcher qualifiers are part of this module's public surface,
    // so every consumer that injects one needs javax.inject on its own compile classpath.
    api(libs.javax.inject)
}
