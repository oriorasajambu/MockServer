package com.rio.mockserver.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The method a route answers on.
 *
 * A filter rather than a plain method because [ANY] is a real authoring choice — "mock this path
 * whatever the client does with it" is how most people start, and narrowing it later is a
 * one-tap change that also raises the route's specificity by
 * [com.rio.mockserver.core.common.config.AppConstants.Matching.SCORE_EXPLICIT_METHOD].
 */
@Serializable
enum class HttpMethodFilter {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS,

    /** Matches every method. Scores lower than an explicit one, so a narrower route always wins. */
    ANY,
    ;

    /** True when this filter is anything other than [ANY], which is what earns the method points. */
    val isExplicit: Boolean get() = this != ANY

    /**
     * Whether a request using [method] should be considered against this filter.
     *
     * `HEAD` is the one asymmetry: SPEC §5.3 has it match routes declared `GET` (or [ANY]), so a
     * client probing for `Content-Length` gets the real headers instead of a 404. A route declared
     * `HEAD` explicitly still answers only `HEAD`.
     */
    fun accepts(method: String): Boolean {
        val normalised = method.uppercase()
        return when (this) {
            ANY -> true
            GET -> normalised == GET.name || normalised == HEAD.name
            else -> normalised == name
        }
    }

    companion object {

        /** Parses a wire method name, falling back to [ANY] for anything unrecognised. */
        fun fromWire(method: String): HttpMethodFilter =
            entries.firstOrNull { it.name == method.uppercase() } ?: ANY
    }
}
