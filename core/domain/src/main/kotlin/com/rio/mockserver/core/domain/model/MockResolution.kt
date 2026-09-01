package com.rio.mockserver.core.domain.model

/**
 * What the pipeline decided to do with a request, before chaos and before rendering (SPEC §3).
 *
 * The resolver returns this rather than a bare [MockResponse] on purpose: it is the extension
 * point v2's scenario state machine needs (SPEC §15), and it is what lets the traffic log
 * distinguish "no rule matched" from "the rule that matched answered 404".
 */
sealed interface MockResolution {

    /** A route won, with the [score] that beat every other candidate. */
    data class Matched(val route: MockRoute, val score: MatchScore) : MockResolution

    /** Nothing matched; [fallback] is the configured default response (SPEC §4.5). */
    data class Unmatched(val fallback: MockResponse) : MockResolution

    /** The API key was missing or wrong (SPEC §8.6). Logged with a distinct **Rejected** chip. */
    data object Unauthorized : MockResolution

    /** Pause mode is on, so everything is refused with a 503 and a `Retry-After`. */
    data class Paused(val retryAfterSeconds: Int) : MockResolution

    /** A CORS preflight this collection answers directly (SPEC §8.7). */
    data class Preflight(val collection: MockCollection) : MockResolution

    /** The request body was over the 5 MB ceiling, so it was refused with a 413 (SPEC §5.4). */
    data object PayloadTooLarge : MockResolution
}
