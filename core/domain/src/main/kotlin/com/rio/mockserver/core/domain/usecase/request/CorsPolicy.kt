package com.rio.mockserver.core.domain.usecase.request

import com.rio.mockserver.core.domain.model.CorsConfig
import com.rio.mockserver.core.domain.model.MockRequest

/**
 * Builds the CORS headers a collection's policy implies (SPEC §8.7).
 *
 * Pure and stateless, so the header table a browser will see is assertable in a unit test rather
 * than only observable through a browser — which is the least debuggable place to discover that a
 * policy is wrong.
 *
 * `allowCredentials = true` with origin `*` is not handled here: the editor refuses to save that
 * combination, because a browser rejects it and the resulting console message says nothing about
 * credentials (SPEC §8.7).
 */
object CorsPolicy {

    /** `Access-Control-Allow-Origin` and friends, injected onto a normal response. */
    fun responseHeaders(config: CorsConfig, request: MockRequest): Map<String, String> {
        if (!config.isEnabled) return emptyMap()
        val headers = LinkedHashMap<String, String>(3)
        headers[ALLOW_ORIGIN] = resolveOrigin(config, request)
        if (config.allowCredentials) headers[ALLOW_CREDENTIALS] = "true"
        // Tells a cache that the answer depends on the request's Origin. Without it, a shared
        // cache can hand a response allowing origin A to a page on origin B.
        if (!config.allowedOrigins.contains(CorsConfig.WILDCARD)) headers[VARY] = ORIGIN
        return headers
    }

    /** The full preflight answer, including the method and header advertisements. */
    fun preflightHeaders(config: CorsConfig, request: MockRequest): Map<String, String> {
        val headers = LinkedHashMap(responseHeaders(config, request))
        headers[ALLOW_METHODS] = config.allowedMethods.joinToString(separator = ", ")
        headers[ALLOW_HEADERS] = resolveAllowedHeaders(config, request)
        headers[MAX_AGE] = config.maxAgeSeconds.toString()
        return headers
    }

    /**
     * Echoes the request's `Origin` when the policy names it, rather than replaying the list.
     *
     * `Access-Control-Allow-Origin` takes one origin, not a list — a comma-separated value is
     * rejected outright, which is a mistake worth making impossible here.
     */
    private fun resolveOrigin(config: CorsConfig, request: MockRequest): String {
        if (config.allowedOrigins.contains(CorsConfig.WILDCARD)) return CorsConfig.WILDCARD
        val origin = request.header(ORIGIN)
        return when {
            origin != null && config.allowedOrigins.contains(origin) -> origin
            // No match: name the first configured origin. The browser will refuse, which is the
            // correct outcome, and the developer sees which origins *are* allowed in the header.
            else -> config.allowedOrigins.firstOrNull() ?: CorsConfig.WILDCARD
        }
    }

    /**
     * Echoes `Access-Control-Request-Headers` when the policy is `*`.
     *
     * A literal `*` in `Access-Control-Allow-Headers` is ignored by browsers whenever credentials
     * are involved, and echoing what was asked for works in every case.
     */
    private fun resolveAllowedHeaders(config: CorsConfig, request: MockRequest): String =
        if (config.allowedHeaders.contains(CorsConfig.WILDCARD)) {
            request.header(REQUEST_HEADERS) ?: CorsConfig.WILDCARD
        } else {
            config.allowedHeaders.joinToString(separator = ", ")
        }

    private const val ORIGIN = "Origin"
    private const val VARY = "Vary"
    private const val ALLOW_ORIGIN = "Access-Control-Allow-Origin"
    private const val ALLOW_METHODS = "Access-Control-Allow-Methods"
    private const val ALLOW_HEADERS = "Access-Control-Allow-Headers"
    private const val ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials"
    private const val MAX_AGE = "Access-Control-Max-Age"
    private const val REQUEST_HEADERS = "Access-Control-Request-Headers"
}
