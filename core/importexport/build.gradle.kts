/**
 * Import and export wire formats (SPEC §9).
 *
 * Pure Kotlin, so every importer is testable against golden files with no device — which is the
 * only practical way to cover "OpenAPI 3.1 in YAML with no examples" and the dozen other shapes a
 * real spec arrives in.
 *
 * The DTO → mapper → domain discipline applies here exactly as it would to an API response: the
 * OpenAPI, Postman and HAR documents are wire formats, they are parsed into DTOs local to this
 * module, and only mapped models leave it. A `$ref` never reaches `:core:domain`.
 *
 * `swagger-parser` is deliberately absent. It is a large Java dependency with reflection and
 * file-system assumptions that do not sit well in an APK, and this app reads a handful of fields.
 */
plugins {
    id("mockserver.jvm.library")
    alias(libs.plugins.kotlin.serializable)
}

dependencies {
    // api, not implementation: the gateways it implements are declared in :core:domain and their
    // signatures are all domain models.
    api(project(":core:domain"))

    // YAML, parsed into kotlinx.serialization's own tree — so a JSON spec and a YAML spec
    // converge on one traversal instead of two.
    implementation(libs.kaml)
}
