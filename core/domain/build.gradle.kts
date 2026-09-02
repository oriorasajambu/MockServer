/**
 * Domain tier. Pure Kotlin — zero Android, zero Ktor, zero Compose.
 *
 * Everything that decides *what the mock server answers* lives here: the models of SPEC §3, the
 * matching pipeline of §4, response generation of §5, the chaos engine of §6, and the traffic
 * contract of §7. The Ktor engine in `:core:server` is an adapter that translates a request into
 * `MockRequest`, calls one use case, and writes the result back out.
 *
 * That split is the point. The riskiest logic in the app — specificity scoring, tie-breaks,
 * body-subset matching, chaos probabilities — is unit-testable without binding a socket.
 *
 * The absence of the Android plugin is the boundary rule made mechanical: a stray `import
 * android.*` here fails to compile rather than surviving to code review.
 */
plugins {
    id("mockserver.jvm.library")
    // Body-subset matching (SPEC §4.4) compares two parsed JSON trees. kotlinx.serialization's
    // `JsonElement` is that tree, and it is pure Kotlin — no Android, no reflection, no service
    // loader. Hand-rolling a parser to keep this module dependency-free would trade a well-tested
    // one for an untested one in the single place where a subtle bug is hardest to notice.
    alias(libs.plugins.kotlin.serializable)
}

dependencies {
    // api, not implementation: use cases return AppResult<T> and repository interfaces are
    // declared in terms of it, so AppResult and DomainError are part of this module's own
    // public signatures.
    api(project(":core:common"))

    // api: `MockResponse.body` and the matchers are exchanged as JSON trees with :core:server and
    // :core:importexport, and `RoutingSnapshot` is serialised by :core:data.
    api(libs.kotlin.serializable)
}
