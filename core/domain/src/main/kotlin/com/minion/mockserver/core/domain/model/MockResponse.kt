package com.minion.mockserver.core.domain.model

import com.minion.mockserver.core.common.config.AppConstants
import kotlinx.serialization.Serializable

/**
 * What a route sends back (SPEC §3, §5).
 *
 * Static by design. v1 has no fakers, no scripting and no randomness — the only dynamism is the
 * three placeholder sources in [com.minion.mockserver.core.domain.usecase.request.ResolvePlaceholdersUseCase],
 * because a mock whose output you cannot predict is a mock you cannot assert against.
 */
@Serializable
data class MockResponse(
    /** 100..599. Not validated here; [com.minion.mockserver.core.domain.usecase.route.ValidateRouteUseCase] does that. */
    val statusCode: Int = DEFAULT_STATUS,

    /**
     * Response headers, before CORS injection.
     *
     * `Content-Type` defaults to `application/json; charset=utf-8` when absent. `Content-Length`
     * and the headers Ktor owns are ignored with an editor warning (SPEC §5.2) — the writer
     * computes the length, and a hand-set one is either redundant or a lie.
     */
    val headers: Map<String, String> = emptyMap(),

    /** May contain `{{path.x}}`, `{{query.x}}` and `{{header.x}}` placeholders. */
    val body: String = "",

    /**
     * A fixed delay for this route alone, in milliseconds.
     *
     * Distinct from chaos: this is "this endpoint is slow", not "this endpoint is flaky", and it
     * survives turning chaos off. When set it *replaces* the chaos profile's delay rather than
     * adding to it, so the number in the editor is the number the client waits.
     */
    val delayMillisOverride: Long? = null,
) {

    /** The effective content type, applying the SPEC §5.2 default. */
    val contentType: String
        get() = headers.entries
            .firstOrNull { it.key.equals(CONTENT_TYPE, ignoreCase = true) }
            ?.value
            ?: AppConstants.Request.DEFAULT_CONTENT_TYPE

    /** Whether the declared content type claims JSON, which is what makes body validation binding. */
    val declaresJson: Boolean
        get() = contentType.substringBefore(';').trim().endsWith("json", ignoreCase = true)

    companion object {

        /** The `Content-Type` header name, in its canonical casing. */
        const val CONTENT_TYPE = "Content-Type"

        /** What a new route starts at. */
        const val DEFAULT_STATUS = 200

        /**
         * The body sent when nothing matched, unless the user has changed it (SPEC §4.5).
         *
         * Echoing the path and method back is deliberate: the most common cause of an unmatched
         * request is a path that is one character off from the rule, and seeing both side by side
         * is what makes that obvious.
         */
        const val DEFAULT_UNMATCHED_BODY =
            """{"error":"No mock route matched","path":"{{path}}","method":"{{method}}"}"""

        /** The fallback for an unmatched request, before the user configures one. */
        val DEFAULT_UNMATCHED = MockResponse(
            statusCode = 404,
            headers = mapOf(CONTENT_TYPE to AppConstants.Request.DEFAULT_CONTENT_TYPE),
            body = DEFAULT_UNMATCHED_BODY,
        )
    }
}
