/**
 * Data tier — the assembly point, and the error boundary.
 *
 * Repository implementations that satisfy `:core:domain`'s interfaces, DTO↔model mappers, and
 * the one place exceptions are allowed to exist: `safeCall` catches them here and converts them
 * into a typed `DomainError`, so nothing above this layer ever sees a `Throwable`.
 *
 * Never imports Compose or `:core:ui`.
 */
plugins {
    id("minion.android.library")
    id("minion.android.hilt")
}

android {
    namespace = "com.minion.scaffold.core.data"
}

dependencies {
    // api, not implementation: repository implementations return AppResult<T> and the mappers
    // produce domain models, so both are part of this module's public surface.
    api(project(":core:common"))
    api(project(":core:domain"))
    implementation(project(":core:network"))

    // ErrorMapper matches on retrofit2.HttpException to turn a status code into a DomainError.
    implementation(libs.retrofit.core)

    implementation(libs.data.store)
}
