package com.minion.mockserver.core.domain.usecase.request

import com.minion.mockserver.core.common.config.AppConstants
import com.minion.mockserver.core.domain.model.ChaosApplied
import com.minion.mockserver.core.domain.model.ChaosFailure
import com.minion.mockserver.core.domain.model.MockRequest
import com.minion.mockserver.core.domain.model.MockResolution
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.PathNormaliser
import com.minion.mockserver.core.domain.model.ResponsePlan
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.ServerConfig
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.usecase.chaos.DecideChaosUseCase
import com.minion.mockserver.core.domain.usecase.traffic.LogTrafficUseCase
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The whole request pipeline, in one place (SPEC §4.1).
 *
 * This is the single decision point the Ktor engine calls. Everything it needs to know is here:
 * pause, the API-key guard, CORS preflights, the 413 ceiling, matching, chaos, placeholder
 * rendering, and the traffic entry. The engine builds a [MockRequest], calls this, and executes
 * the [ResponsePlan] it is handed.
 *
 * ### Why the transport is a lambda
 *
 * `write` inverts control on purpose. It keeps the *order* of operations — delay, then write,
 * then log, with the duration measured around the write but excluding the delay — inside the
 * domain, where a test with a recording lambda can assert on all of it without a socket. The
 * alternative, returning a plan and letting the engine sequence it, would put the one piece of
 * timing logic anybody would get wrong in the one layer that cannot be unit-tested.
 *
 * The pipeline order is fixed and matches SPEC §4.1: pause → API key → CORS preflight → oversized
 * body → matching → chaos → render.
 */
@Singleton
class HandleMockRequestUseCase @Inject constructor(
    private val resolveRoute: ResolveRouteUseCase,
    private val decideChaos: DecideChaosUseCase,
    private val resolvePlaceholders: ResolvePlaceholdersUseCase,
    private val logTraffic: LogTrafficUseCase,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) {

    /**
     * Resolves [request], writes the answer through [write], and logs the result.
     *
     * @param snapshot the rule set captured at entry, so an in-flight request finishes against the
     *   rules it started with even if the user saves a route mid-flight (SPEC §5.5).
     * @param config the socket configuration for this run, which is where the API key lives.
     * @param write executes a plan on the transport. May throw; the throwable is recorded on the
     *   traffic entry and rethrown so the engine's own boundary can answer 500.
     */
    suspend operator fun invoke(
        request: MockRequest,
        snapshot: RoutingSnapshot,
        config: ServerConfig,
        write: suspend (ResponsePlan) -> Unit,
    ): MockResolution {
        val decision = decide(request, snapshot, config)

        if (decision.chaos.delayMillis > 0L) {
            // `delay`, not a blocking sleep: a 30-second chaos delay must cost a suspended
            // coroutine and no thread, or a handful of slow endpoints would exhaust the pool
            // (SPEC §12).
            delay(decision.chaos.delayMillis)
        }

        val startedAt = timeProvider.monotonicNanos()
        var thrown: Throwable? = null
        try {
            write(decision.plan)
        } catch (e: Throwable) {
            thrown = e
            throw e
        } finally {
            val elapsedMillis = (timeProvider.monotonicNanos() - startedAt) / NANOS_PER_MILLI
            logTraffic(decision.toTrafficEntry(request, elapsedMillis, thrown))
        }

        return decision.resolution
    }

    /**
     * Everything the pipeline decided, before anything touches the socket.
     *
     * Exposed separately so the route editor's "test this route" preview and the unit tests can
     * ask what *would* happen without anything happening.
     */
    fun decide(
        request: MockRequest,
        snapshot: RoutingSnapshot,
        config: ServerConfig,
    ): Decision {
        // 1. Pause. Ahead of everything else because a paused server has nothing to say about
        //    keys, origins or routes — but the request is still logged, so pausing does not make
        //    the inspector go quiet.
        val pause = snapshot.pause
        if (pause.isEnabled) {
            return Decision(
                resolution = MockResolution.Paused(pause.retryAfterSeconds),
                plan = ResponsePlan.complete(
                    statusCode = pause.statusCode,
                    headers = jsonHeaders(RETRY_AFTER to pause.retryAfterSeconds.toString()),
                    body = pause.body,
                ),
                outcome = TrafficEntry.Outcome.PAUSED,
            )
        }

        val isOptions = request.method.equals(OPTIONS, ignoreCase = true)

        // 2. API key. OPTIONS is exempt: a browser will not attach a custom header to a preflight,
        //    so guarding it would make every browser client fail before it ever sent the real
        //    request (SPEC §8.6).
        val apiKey = config.apiKey
        if (apiKey != null && !isOptions && request.header(apiKey.headerName) != apiKey.value) {
            return Decision(
                resolution = MockResolution.Unauthorized,
                plan = ResponsePlan.complete(
                    statusCode = UNAUTHORIZED,
                    headers = jsonHeaders(),
                    body = UNAUTHORIZED_BODY,
                ),
                outcome = TrafficEntry.Outcome.REJECTED,
            )
        }

        // 3. CORS preflight, answered by the collection whose base path is the longest prefix of
        //    the request. A preflight addresses a path whose real handler is usually a different
        //    method, so it cannot wait for route resolution.
        val segments = PathNormaliser.segments(request.path)
        if (isOptions) {
            val collection = snapshot.collectionFor(segments)
            if (collection != null && collection.cors.isEnabled) {
                return Decision(
                    resolution = MockResolution.Preflight(collection),
                    plan = ResponsePlan.complete(
                        statusCode = NO_CONTENT,
                        headers = CorsPolicy.preflightHeaders(collection.cors, request),
                        body = "",
                    ),
                    outcome = TrafficEntry.Outcome.PREFLIGHT,
                )
            }
        }

        // 4. Oversized body. The adapter refused to read it, so there is nothing to match against
        //    and nothing to log but the size (SPEC §5.4).
        if (request.bodyState == MockRequest.BodyState.OVERSIZED) {
            return Decision(
                resolution = MockResolution.PayloadTooLarge,
                plan = ResponsePlan.complete(
                    statusCode = PAYLOAD_TOO_LARGE,
                    headers = jsonHeaders(),
                    body = payloadTooLargeBody(config.maxRequestBodyBytes),
                ),
                outcome = TrafficEntry.Outcome.TOO_LARGE,
            )
        }

        // 5. Matching.
        val match = resolveRoute(request, snapshot)
        val response = match?.compiled?.route?.response ?: snapshot.unmatchedResponse
        val captures = match?.pathCaptures.orEmpty()

        // 6. Chaos, resolved against the route's override or the global profile.
        val chaos = decideChaos(
            profile = match?.compiled?.route?.chaosOverride ?: snapshot.globalChaos,
            routeDelayOverride = response.delayMillisOverride,
        )

        // 7. Render, then let chaos rewrite the result.
        val rendered = resolvePlaceholders.render(response, request, captures)
        val corsHeaders = match?.compiled?.collection?.cors
            ?.takeIf { it.isEnabled }
            ?.let { CorsPolicy.responseHeaders(it, request) }
            .orEmpty()

        val plan = buildPlan(
            statusCode = rendered.statusCode,
            // CORS headers go on *after* the user's, so they win on conflict (SPEC §5.2).
            headers = withDefaults(rendered.headers) + corsHeaders,
            body = rendered.body,
            chaos = chaos,
            errorBody = errorBodyFor(match, snapshot),
            errorRetryAfter = retryAfterFor(match, snapshot),
            isHead = request.method.equals(HEAD, ignoreCase = true),
        )

        return Decision(
            resolution = match
                ?.let { MockResolution.Matched(it.compiled.route, it.score) }
                ?: MockResolution.Unmatched(snapshot.unmatchedResponse),
            plan = plan,
            outcome = if (match != null) TrafficEntry.Outcome.MATCHED else TrafficEntry.Outcome.UNMATCHED,
            chaos = chaos,
            matchedRouteId = match?.compiled?.route?.id,
            matchedRouteName = match?.compiled?.route?.name,
            matchScore = match?.score?.total,
            unresolvedPlaceholders = rendered.unresolvedPlaceholders,
        )
    }

    /**
     * Turns a rendered response plus a chaos decision into a transport plan.
     *
     * The three body-length numbers are where the failure modes actually live:
     * - **Error injection** replaces the body and status outright, so the plan is honest again.
     * - **Connection reset** writes `dropAfterBytes` and then aborts, so `bytesToWrite` is below
     *   `declaredContentLength` and the socket dies without a clean end.
     * - **Malformed body** writes a fraction while `Content-Length` still declares the full
     *   length, which is what makes a strict client report an incomplete body and a lenient one
     *   report a JSON parse error.
     */
    private fun buildPlan(
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
        chaos: ChaosApplied,
        errorBody: String,
        errorRetryAfter: Int,
        isHead: Boolean,
    ): ResponsePlan {
        val effectiveStatus: Int
        val effectiveBody: String
        val effectiveHeaders: Map<String, String>

        when (val failure = chaos.failure) {
            is ChaosFailure.ErrorInjected -> {
                effectiveStatus = failure.status
                effectiveBody = errorBody
                effectiveHeaders = if (failure.status == TOO_MANY_REQUESTS) {
                    headers + (RETRY_AFTER to errorRetryAfter.toString())
                } else {
                    headers
                }
            }

            // Enumerated rather than `else`, so a fourth failure mode is a compile error here
            // instead of silently behaving as "no chaos" — which is how a chaos mode ends up
            // shipped and doing nothing.
            is ChaosFailure.ConnectionReset, is ChaosFailure.MalformedBody, null -> {
                effectiveStatus = statusCode
                effectiveBody = body
                effectiveHeaders = headers
            }
        }

        val fullLength = effectiveBody.toByteArray(Charsets.UTF_8).size
        // HEAD answers with the headers a GET would produce and no body, including the
        // Content-Length the body *would* have had (SPEC §5.3).
        val bytesToWrite = if (isHead) {
            0
        } else {
            when (val failure = chaos.failure) {
                is ChaosFailure.ConnectionReset -> failure.bytesWritten.coerceIn(0, fullLength)
                is ChaosFailure.MalformedBody -> fullLength * failure.truncateAtPercent / PERCENT
                is ChaosFailure.ErrorInjected, null -> fullLength
            }
        }

        return ResponsePlan(
            statusCode = effectiveStatus,
            headers = effectiveHeaders,
            body = effectiveBody,
            declaredContentLength = fullLength,
            bytesToWrite = bytesToWrite,
            abortAfterWrite = chaos.failure is ChaosFailure.ConnectionReset,
        )
    }

    /** Everything decided about one request, before it reaches the wire. */
    data class Decision(
        val resolution: MockResolution,
        val plan: ResponsePlan,
        val outcome: TrafficEntry.Outcome,
        val chaos: ChaosApplied = ChaosApplied.NONE,
        val matchedRouteId: String? = null,
        val matchedRouteName: String? = null,
        val matchScore: Int? = null,
        val unresolvedPlaceholders: List<String> = emptyList(),
    )

    private fun Decision.toTrafficEntry(
        request: MockRequest,
        durationMillis: Long,
        thrown: Throwable?,
    ) = TrafficEntry(
        id = idGenerator.newId(),
        receivedAt = request.receivedAt.takeIf { it > 0L } ?: timeProvider.nowMillis(),
        clientIp = request.clientIp,
        method = request.method,
        path = request.path,
        query = request.query,
        requestHeaders = request.headers,
        requestBody = request.body,
        requestTruncated = request.bodyState == MockRequest.BodyState.OVERSIZED,
        requestByteCount = request.bodyByteCount,
        matchedRouteId = matchedRouteId,
        matchedRouteName = matchedRouteName,
        matchScore = matchScore,
        statusCode = plan.statusCode,
        responseHeaders = plan.headers,
        responseBody = plan.body,
        // A body the client never fully received is a truncated body in the log too, so the
        // entry does not claim to show bytes that were dropped on the floor.
        responseTruncated = plan.bytesToWrite < plan.declaredContentLength &&
            !request.method.equals(HEAD, ignoreCase = true),
        durationMillis = durationMillis,
        chaosApplied = chaos.takeIf { it.isNoteworthy },
        unresolvedPlaceholders = unresolvedPlaceholders,
        outcome = if (thrown != null) TrafficEntry.Outcome.ERRORED else outcome,
    )

    /** Supplies the `Content-Type` default without overriding a user-set one (SPEC §5.2). */
    private fun withDefaults(headers: Map<String, String>): Map<String, String> {
        val hasContentType = headers.keys.any { it.equals(MockResponse.CONTENT_TYPE, ignoreCase = true) }
        val filtered = headers.filterKeys { key ->
            key.lowercase() !in AppConstants.Request.RESERVED_RESPONSE_HEADERS
        }
        return if (hasContentType) {
            filtered
        } else {
            filtered + (MockResponse.CONTENT_TYPE to AppConstants.Request.DEFAULT_CONTENT_TYPE)
        }
    }

    private fun errorBodyFor(match: ResolveRouteUseCase.Match?, snapshot: RoutingSnapshot): String =
        (match?.compiled?.route?.chaosOverride ?: snapshot.globalChaos)
            ?.errorInjection?.body
            ?: AppConstants.Chaos.DEFAULT_ERROR_BODY

    private fun retryAfterFor(match: ResolveRouteUseCase.Match?, snapshot: RoutingSnapshot): Int =
        (match?.compiled?.route?.chaosOverride ?: snapshot.globalChaos)
            ?.errorInjection?.retryAfterSeconds
            ?: AppConstants.Chaos.DEFAULT_RETRY_AFTER_SECONDS

    private fun jsonHeaders(vararg extra: Pair<String, String>): Map<String, String> =
        mapOf(MockResponse.CONTENT_TYPE to AppConstants.Request.DEFAULT_CONTENT_TYPE) + extra

    private fun payloadTooLargeBody(maxBytes: Long): String =
        """{"error":"Request body exceeds the $maxBytes byte limit"}"""

    private companion object {
        const val OPTIONS = "OPTIONS"
        const val HEAD = "HEAD"
        const val RETRY_AFTER = "Retry-After"
        const val UNAUTHORIZED = 401
        const val NO_CONTENT = 204
        const val PAYLOAD_TOO_LARGE = 413
        const val TOO_MANY_REQUESTS = 429
        const val PERCENT = 100
        const val NANOS_PER_MILLI = 1_000_000L
        const val UNAUTHORIZED_BODY = """{"error":"Missing or invalid API key"}"""
    }
}
