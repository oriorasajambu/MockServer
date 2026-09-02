package com.minion.mockserver.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Per-collection CORS policy (SPEC §8.7).
 *
 * Per collection rather than per route because CORS is a property of the *origin* a browser is
 * talking to, and a collection is the closest thing this app has to one. A per-route policy would
 * also mean a preflight — which carries no body and often no matching path — could not be
 * answered without first resolving a route it is not really addressed to.
 */
@Serializable
data class CorsConfig(
    /** Whether this collection answers preflights and injects CORS headers at all. */
    val isEnabled: Boolean = false,

    /** `["*"]` by default. An explicit list is required alongside [allowCredentials]. */
    val allowedOrigins: List<String> = listOf(WILDCARD),

    /** Methods advertised in `Access-Control-Allow-Methods`. */
    val allowedMethods: List<String> = DEFAULT_METHODS,

    /** Headers advertised in `Access-Control-Allow-Headers`. `["*"]` by default. */
    val allowedHeaders: List<String> = listOf(WILDCARD),

    /** Whether `Access-Control-Allow-Credentials: true` is sent. */
    val allowCredentials: Boolean = false,

    /** Seconds a browser may cache the preflight result. */
    val maxAgeSeconds: Int = DEFAULT_MAX_AGE_SECONDS,
) {

    /**
     * `allowCredentials` with a wildcard origin, which every browser rejects.
     *
     * The combination is invalid per the CORS spec, and the failure surfaces in the browser as a
     * bare "blocked by CORS policy" with no mention of credentials — which is exactly the kind of
     * thing that eats an afternoon. The editor blocks saving while this is true (SPEC §8.7).
     */
    val hasCredentialsWildcardConflict: Boolean
        get() = allowCredentials && allowedOrigins.any { it == WILDCARD }

    companion object {

        /** The any-origin token. */
        const val WILDCARD = "*"

        /** One hour, matching the SPEC §8.7 default. */
        const val DEFAULT_MAX_AGE_SECONDS = 3600

        /** Every method this server can serve. */
        val DEFAULT_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

        /** CORS off — the default for a new collection. */
        val DISABLED = CorsConfig()
    }
}
