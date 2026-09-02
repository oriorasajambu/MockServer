package com.minion.mockserver.core.domain.model

/**
 * What chaos actually did to one request (SPEC §6.3).
 *
 * Recorded on every traffic entry, because without it an injected 503 is indistinguishable from a
 * bug in the mock rules — and a tool you cannot trust to tell you which of the two it is stops
 * being used.
 *
 * ### Delay is orthogonal to the failure modes
 *
 * SPEC §6.2 lists delay first among the failure modes and then says only one fires per request.
 * Read literally that makes error injection unreachable on any profile that also sets a delay,
 * which is a footgun rather than a feature: "500 errors 10% of the time, and everything takes
 * 200 ms" is an ordinary thing to want. So [delayMillis] is applied independently and the three
 * genuine failure modes stay mutually exclusive, first-one-wins, in SPEC order. The log shows
 * both, so nothing about what happened is hidden.
 */
data class ChaosApplied(
    /** Latency added before answering. Zero when no delay was configured or drawn. */
    val delayMillis: Long = 0L,

    /** The one failure mode that fired, or `null`. */
    val failure: ChaosFailure? = null,
) {

    /** Whether this is worth showing an icon for. An all-zero record is not. */
    val isNoteworthy: Boolean get() = delayMillis > 0L || failure != null

    companion object {

        /** Chaos considered the request and did nothing. */
        val NONE = ChaosApplied()
    }
}

/** The three mutually-exclusive ways chaos can break a response (SPEC §6.2). */
sealed interface ChaosFailure {

    /** The route's response was replaced with [status], drawn from the profile's weighted pool. */
    data class ErrorInjected(val status: Int) : ChaosFailure

    /** [bytesWritten] bytes went out, then the socket was killed without a clean end. */
    data class ConnectionReset(val bytesWritten: Int) : ChaosFailure

    /** The body was cut at [truncateAtPercent]% under a `Content-Length` declaring the full size. */
    data class MalformedBody(val truncateAtPercent: Int) : ChaosFailure
}
