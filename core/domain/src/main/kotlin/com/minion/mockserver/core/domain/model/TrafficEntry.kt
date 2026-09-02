package com.minion.mockserver.core.domain.model

/**
 * One request/response pair as the inspector shows it (SPEC §7.2).
 *
 * Never persisted. The traffic log is an in-memory ring buffer cleared on process death: writing
 * it to Room would put disk I/O on the request path, the data has a lifespan of one debugging
 * session, and a phone should not accumulate megabytes of somebody else's request bodies.
 */
data class TrafficEntry(
    val id: String,
    val receivedAt: Long,
    val clientIp: String,

    val method: String,
    val path: String,
    val query: Map<String, List<String>> = emptyMap(),
    val requestHeaders: Map<String, List<String>> = emptyMap(),

    /** `null` for a binary body, an oversized one, or no body at all. */
    val requestBody: String? = null,

    /** True when the body was cut — by the 5 MB request ceiling or the 256 KB log cap. */
    val requestTruncated: Boolean = false,

    /** Size on the wire, which survives even when the body itself was not stored. */
    val requestByteCount: Int = 0,

    /** `null` when nothing matched — the single most important field on this screen. */
    val matchedRouteId: String? = null,
    val matchedRouteName: String? = null,
    val matchScore: Int? = null,

    val statusCode: Int = 0,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val responseTruncated: Boolean = false,

    /**
     * Time spent resolving and writing, **excluding** any injected chaos delay.
     *
     * Excluding it is the point: leave the delay in and every latency figure becomes a lie the
     * moment chaos is on, and the log stops being usable for the one thing latency numbers are
     * for. The delay is shown separately, from [chaosApplied].
     */
    val durationMillis: Long = 0L,

    /** What chaos did, or `null` if it did nothing. */
    val chaosApplied: ChaosApplied? = null,

    /** Placeholders in the response that resolved to nothing, e.g. `path.userId` (SPEC §5.1). */
    val unresolvedPlaceholders: List<String> = emptyList(),

    /** Why this entry got the status it did, for the chips on the row. */
    val outcome: Outcome = Outcome.MATCHED,
) {

    /** The distinct end states a request can reach, each with its own chip in the log. */
    enum class Outcome {
        /** A route matched and answered. */
        MATCHED,

        /** Nothing matched; the configurable fallback answered (SPEC §4.5). */
        UNMATCHED,

        /** The API key was missing or wrong (SPEC §8.6). */
        REJECTED,

        /** Pause mode refused it. */
        PAUSED,

        /** A CORS preflight answered by the collection's policy. */
        PREFLIGHT,

        /** Over the 5 MB body ceiling (SPEC §5.4). */
        TOO_LARGE,

        /** The handler threw. Answered 500 by `respondCatching` rather than dropping the socket. */
        ERRORED,
    }

    /** Whether the row shows the unmatched icon. */
    val isUnmatched: Boolean get() = matchedRouteId == null

    /** The 2xx/3xx/4xx/5xx bucket this entry filters under. */
    val statusClass: Int get() = statusCode / 100
}
