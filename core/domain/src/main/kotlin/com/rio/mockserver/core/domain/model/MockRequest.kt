package com.rio.mockserver.core.domain.model

/**
 * One inbound request, as the domain sees it (SPEC §3).
 *
 * Deliberately not a Ktor type. This is the whole reason the matching pipeline can be tested
 * without binding a socket: `KtorCallAdapter` builds one of these and everything downstream is
 * pure Kotlin operating on data.
 */
data class MockRequest(
    /** Uppercase wire method. */
    val method: String,

    /** Percent-decoded and normalised: no query, no trailing slash, no duplicate slashes (SPEC §4.2). */
    val path: String,

    /** Repeated parameters are kept, in order. `{{query.x}}` resolves to the first (SPEC §13). */
    val query: Map<String, List<String>> = emptyMap(),

    /**
     * Repeated headers are kept, in order.
     *
     * Keys arrive in whatever casing the client sent. Never index this map directly — use
     * [header], which does the case-insensitive lookup SPEC §13 requires.
     */
    val headers: Map<String, List<String>> = emptyMap(),

    /**
     * The request body as text, or `null`.
     *
     * `null` means there was nothing to read, the content type was binary (SPEC §5.4), or the
     * body was over the 5 MB ceiling — the three cases where a body matcher must fail rather
     * than error. [bodyByteCount] still reports the size in all of them.
     */
    val body: String? = null,

    /** Declared content type, unparsed. */
    val contentType: String? = null,

    /** Size of the body on the wire, after any `gzip`/`deflate` decoding. */
    val bodyByteCount: Int = 0,

    /** Why [body] is what it is. Drives both the 413 branch and the log's `<binary, N bytes>`. */
    val bodyState: BodyState = BodyState.ABSENT,

    /** Peer address, for the traffic log. Never part of matching. */
    val clientIp: String = "",

    /** Epoch millis at which the request was accepted. */
    val receivedAt: Long = 0L,
) {

    /**
     * First value of [name], compared case-insensitively.
     *
     * A linear scan rather than a lowercased copy of the map: a request has a handful of headers,
     * and building a second map per lookup would cost more than the scan it saves.
     */
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    /** Every value of [name], compared case-insensitively. Empty when absent. */
    fun headerValues(name: String): List<String> = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        .orEmpty()

    /** True when the body is not available for matching, so every body matcher must fail. */
    val bodyOmitted: Boolean get() = bodyState != BodyState.PRESENT

    /** Whether [name] was sent at all, whatever its value. */
    fun hasHeader(name: String): Boolean = headers.keys.any { it.equals(name, ignoreCase = true) }

    /** First value of query parameter [name], or `null`. Case-sensitive, as query keys are. */
    fun queryValue(name: String): String? = query[name]?.firstOrNull()

    /**
     * What happened when the body was read.
     *
     * An enum rather than two booleans because the four cases are mutually exclusive and each one
     * means something different downstream: only [OVERSIZED] produces a 413, only [BINARY] shows
     * `<binary, N bytes>` in the log, and all three non-[PRESENT] cases fail every body matcher.
     */
    enum class BodyState {

        /** Nothing was sent, or the body was empty. */
        ABSENT,

        /** Read fully into [body] and available for matching. */
        PRESENT,

        /** A non-text content type. Not stored as text (SPEC §5.4). */
        BINARY,

        /** Over the 5 MB ceiling, so it was never read. Answered `413` (SPEC §5.4). */
        OVERSIZED,
    }
}
