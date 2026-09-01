/**
 * Data tier — the wire.
 *
 * The shared `OkHttpClient` and `Retrofit` instance, plus the interceptors every call goes
 * through. Chucker on debug only.
 *
 * Per-feature `*Api` interfaces and their DTOs do NOT live here — they stay `internal` to the
 * feature that owns them and are built from this module's `Retrofit`. A DTO that crossed a
 * module boundary would make the wire format part of the app's shared vocabulary.
 *
 * The base URL arrives through the `@BaseUrl` qualifier, provided by `:app`. This module never
 * reads a `BuildConfig` — that is what would quietly make it app-specific.
 */
plugins {
    id("minion.android.library")
    id("minion.android.hilt")
}

android {
    namespace = "com.minion.scaffold.core.network"
}

dependencies {
    // api, not implementation: safeCall returns AppResult<T> and the error mapper produces a
    // DomainError, so both are in this module's public signatures.
    api(project(":core:common"))

    // api, not implementation: everything NetworkModule @Provides — Retrofit, OkHttpClient, Gson
    // — is referenced by the Hilt component generated in :app, so those types must be on :app's
    // compile classpath. A Hilt module's provided types are part of the module's public surface
    // even when no hand-written code mentions them.
    api(libs.bundles.networking)

    // Not api, deliberately: Chucker is referenced only inside provideOkHttpClient and never
    // appears in a @Provides return type, so it stays off every consumer's classpath. That is
    // what keeps the debug-only inspector debug-only.
    debugImplementation(libs.chucker)
    releaseImplementation(libs.chucker.noop)
}
