package com.minion.mockserver.core.domain.usecase.request

import com.minion.mockserver.core.domain.fixture.FakeIdGenerator
import com.minion.mockserver.core.domain.fixture.FakeTimeProvider
import com.minion.mockserver.core.domain.fixture.Fixtures
import com.minion.mockserver.core.domain.fixture.RecordingTrafficRepository
import com.minion.mockserver.core.domain.model.ApiKeyConfig
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.ConnectionResetSpec
import com.minion.mockserver.core.domain.model.CorsConfig
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.domain.model.ErrorInjection
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.MalformedBodySpec
import com.minion.mockserver.core.domain.model.MockRequest
import com.minion.mockserver.core.domain.model.MockResolution
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.PauseSpec
import com.minion.mockserver.core.domain.model.ResponsePlan
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.ServerConfig
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.model.WeightedStatus
import com.minion.mockserver.core.domain.usecase.chaos.ChaosRandom
import com.minion.mockserver.core.domain.usecase.chaos.DecideChaosUseCase
import com.minion.mockserver.core.domain.usecase.traffic.LogTrafficUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC §4.1 — the pipeline order, and what each stage puts on the wire and in the log.
 *
 * The transport is a recording lambda, so every assertion here is about the [ResponsePlan] the
 * domain produced. That is the whole reason the pipeline takes a `write` callback: the ordering
 * that would be easiest to get wrong — delay, then write, then log, with the duration measured
 * around the write but excluding the delay — is checkable without a socket.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HandleMockRequestUseCaseTest {

    private val traffic = RecordingTrafficRepository()
    private val time = FakeTimeProvider()
    private val chaosRandom = ChaosRandom()

    private val handle = HandleMockRequestUseCase(
        resolveRoute = ResolveRouteUseCase(MatchScorer(BodySubsetMatcher())),
        decideChaos = DecideChaosUseCase(chaosRandom),
        resolvePlaceholders = ResolvePlaceholdersUseCase(),
        logTraffic = LogTrafficUseCase(traffic),
        timeProvider = time,
        idGenerator = FakeIdGenerator("entry"),
    )

    private val config = ServerConfig(port = 8080)

    // ---------------------------------------------------------------- happy path

    @Test
    fun `a matched route answers with its response and a computed Content-Length`() = runTest {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(
                    pathPattern = "/users/{id}",
                    response = MockResponse(statusCode = 200, body = """{"id":"{{path.id}}"}"""),
                ),
            ),
        )

        val plan = write(Fixtures.request(path = "/users/42"), snapshot)

        assertEquals(200, plan.statusCode)
        assertEquals("""{"id":"42"}""", plan.body)
        assertEquals(plan.body.toByteArray().size, plan.declaredContentLength)
        assertEquals(plan.declaredContentLength, plan.bytesToWrite)
        assertEquals(
            "application/json; charset=utf-8",
            plan.headers[MockResponse.CONTENT_TYPE],
        )
    }

    @Test
    fun `a user-set Content-Type is not overridden`() = runTest {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(
                    pathPattern = "/x",
                    response = MockResponse(headers = mapOf("Content-Type" to "text/csv"), body = "a,b"),
                ),
            ),
        )
        assertEquals("text/csv", write(Fixtures.request(path = "/x"), snapshot).headers["Content-Type"])
    }

    @Test
    fun `reserved headers are dropped`() = runTest {
        // Content-Length is computed by the writer; a hand-set one is either redundant or a lie.
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(
                    pathPattern = "/x",
                    response = MockResponse(
                        headers = mapOf("Content-Length" to "9999", "Server" to "nginx"),
                        body = "hi",
                    ),
                ),
            ),
        )

        val plan = write(Fixtures.request(path = "/x"), snapshot)

        assertNull(plan.headers["Content-Length"])
        assertNull(plan.headers["Server"])
        assertEquals(2, plan.declaredContentLength)
    }

    @Test
    fun `HEAD returns the headers a GET would, with no body and the full length`() = runTest {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(
                    method = HttpMethodFilter.GET,
                    pathPattern = "/users",
                    response = MockResponse(body = """{"users":[]}"""),
                ),
            ),
        )

        val plan = write(Fixtures.request(method = "HEAD", path = "/users"), snapshot)

        assertEquals(0, plan.bytesToWrite)
        assertEquals("""{"users":[]}""".length, plan.declaredContentLength)
        assertTrue("HEAD is not a truncated response", !traffic.logged.single().responseTruncated)
    }

    // ---------------------------------------------------------------- pipeline order

    @Test
    fun `pause is answered before the API key is even considered`() = runTest {
        val snapshot = Fixtures.snapshot(
            routes = listOf(Fixtures.route(pathPattern = "/x")),
            pause = PauseSpec(isEnabled = true, statusCode = 503, retryAfterSeconds = 7),
        )
        val guarded = config.copy(apiKey = ApiKeyConfig(value = "secret"))

        val plan = write(Fixtures.request(path = "/x"), snapshot, guarded)

        assertEquals(503, plan.statusCode)
        assertEquals("7", plan.headers["Retry-After"])
        assertEquals(TrafficEntry.Outcome.PAUSED, traffic.logged.single().outcome)
    }

    @Test
    fun `a paused request is still logged`() = runTest {
        // Pausing must not make the inspector go quiet — that is where you look to see the client
        // retrying.
        val snapshot = Fixtures.snapshot(
            routes = listOf(Fixtures.route(pathPattern = "/x")),
            pause = PauseSpec(isEnabled = true),
        )
        write(Fixtures.request(path = "/x"), snapshot)
        assertEquals(1, traffic.logged.size)
    }

    @Test
    fun `a missing or wrong API key is rejected with 401`() = runTest {
        val snapshot = Fixtures.snapshot(listOf(Fixtures.route(pathPattern = "/x")))
        val guarded = config.copy(apiKey = ApiKeyConfig(headerName = "X-Mock-Key", value = "secret"))

        val missing = write(Fixtures.request(path = "/x"), snapshot, guarded)
        assertEquals(401, missing.statusCode)

        val wrong = write(
            Fixtures.request(path = "/x", headers = mapOf("X-Mock-Key" to listOf("nope"))),
            snapshot,
            guarded,
        )
        assertEquals(401, wrong.statusCode)

        val right = write(
            Fixtures.request(path = "/x", headers = mapOf("X-Mock-Key" to listOf("secret"))),
            snapshot,
            guarded,
        )
        assertEquals(200, right.statusCode)
    }

    @Test
    fun `a rejected request is logged with no matched route`() = runTest {
        val snapshot = Fixtures.snapshot(listOf(Fixtures.route(pathPattern = "/x")))
        write(Fixtures.request(path = "/x"), snapshot, config.copy(apiKey = ApiKeyConfig(value = "s")))

        val entry = traffic.logged.single()
        assertEquals(TrafficEntry.Outcome.REJECTED, entry.outcome)
        assertNull(entry.matchedRouteId)
    }

    @Test
    fun `OPTIONS bypasses the API key guard`() = runTest {
        // A browser will not attach a custom header to a preflight, so guarding it would make
        // every browser client fail before it ever sent the real request.
        val snapshot = corsSnapshot()
        val plan = write(
            Fixtures.request(method = "OPTIONS", path = "/api/users"),
            snapshot,
            config.copy(apiKey = ApiKeyConfig(value = "secret")),
        )

        assertEquals(204, plan.statusCode)
    }

    @Test
    fun `a CORS preflight is answered by the collection whose base path is the longest prefix`() = runTest {
        val plan = write(
            Fixtures.request(
                method = "OPTIONS",
                path = "/api/users",
                headers = mapOf(
                    "Origin" to listOf("https://app.example.com"),
                    "Access-Control-Request-Headers" to listOf("X-Tenant"),
                ),
            ),
            corsSnapshot(),
        )

        assertEquals(204, plan.statusCode)
        assertEquals("https://app.example.com", plan.headers["Access-Control-Allow-Origin"])
        assertEquals("X-Tenant", plan.headers["Access-Control-Allow-Headers"])
        assertEquals("3600", plan.headers["Access-Control-Max-Age"])
        assertEquals(TrafficEntry.Outcome.PREFLIGHT, traffic.logged.single().outcome)
    }

    @Test
    fun `OPTIONS goes through normal matching when CORS is off`() = runTest {
        val snapshot = Fixtures.snapshot(
            listOf(Fixtures.route(method = HttpMethodFilter.OPTIONS, pathPattern = "/x")),
        )
        assertEquals(200, write(Fixtures.request(method = "OPTIONS", path = "/x"), snapshot).statusCode)
    }

    @Test
    fun `CORS headers win over user headers on a matched response`() = runTest {
        val snapshot = corsSnapshot(
            route = Fixtures.route(
                pathPattern = "/users",
                response = MockResponse(
                    headers = mapOf("Access-Control-Allow-Origin" to "https://evil.example.com"),
                    body = "{}",
                ),
            ),
        )

        val plan = write(
            Fixtures.request(path = "/api/users", headers = mapOf("Origin" to listOf("https://app.example.com"))),
            snapshot,
        )

        assertEquals("https://app.example.com", plan.headers["Access-Control-Allow-Origin"])
    }

    @Test
    fun `an oversized body is answered 413`() = runTest {
        val snapshot = Fixtures.snapshot(listOf(Fixtures.route(pathPattern = "/upload")))
        val plan = write(
            Fixtures.request(
                method = "POST",
                path = "/upload",
                bodyState = MockRequest.BodyState.OVERSIZED,
            ),
            snapshot,
        )

        assertEquals(413, plan.statusCode)
        assertEquals(TrafficEntry.Outcome.TOO_LARGE, traffic.logged.single().outcome)
        assertTrue(traffic.logged.single().requestTruncated)
    }

    // ---------------------------------------------------------------- unmatched

    @Test
    fun `an unmatched request gets the configured fallback with placeholders resolved`() = runTest {
        val snapshot = Fixtures.snapshot(routes = emptyList())

        val plan = write(Fixtures.request(method = "PUT", path = "/nope"), snapshot)

        assertEquals(404, plan.statusCode)
        assertEquals(
            """{"error":"No mock route matched","path":"/nope","method":"PUT"}""",
            plan.body,
        )
        assertEquals(TrafficEntry.Outcome.UNMATCHED, traffic.logged.single().outcome)
        assertNull(traffic.logged.single().matchedRouteId)
    }

    // ---------------------------------------------------------------- chaos

    @Test
    fun `an injected error replaces the status and the body`() = runTest {
        val snapshot = chaosSnapshot(
            ChaosProfile(
                id = "c",
                name = "errors",
                errorInjection = ErrorInjection(
                    probability = 1.0,
                    statuses = listOf(WeightedStatus(503)),
                    body = """{"chaos":true}""",
                ),
            ),
        )

        val plan = write(Fixtures.request(path = "/x"), snapshot)

        assertEquals(503, plan.statusCode)
        assertEquals("""{"chaos":true}""", plan.body)
    }

    @Test
    fun `an injected 429 carries Retry-After`() = runTest {
        val snapshot = chaosSnapshot(
            ChaosProfile(
                id = "c",
                name = "throttle",
                errorInjection = ErrorInjection(
                    probability = 1.0,
                    statuses = listOf(WeightedStatus(429)),
                    retryAfterSeconds = 30,
                ),
            ),
        )
        assertEquals("30", write(Fixtures.request(path = "/x"), snapshot).headers["Retry-After"])
    }

    @Test
    fun `a truncated body declares the full length`() = runTest {
        // This is the whole mechanism: strict clients report an incomplete body, lenient ones a
        // JSON parse error.
        val body = "0123456789"
        val snapshot = chaosSnapshot(
            ChaosProfile(id = "c", name = "malformed", malformedBody = MalformedBodySpec(1.0, 60)),
            response = MockResponse(body = body),
        )

        val plan = write(Fixtures.request(path = "/x"), snapshot)

        assertEquals(10, plan.declaredContentLength)
        assertEquals(6, plan.bytesToWrite)
        assertTrue(!plan.abortAfterWrite)
        assertTrue(traffic.logged.single().responseTruncated)
    }

    @Test
    fun `a connection reset writes a prefix and then aborts`() = runTest {
        val snapshot = chaosSnapshot(
            ChaosProfile(
                id = "c",
                name = "reset",
                connectionReset = ConnectionResetSpec(probability = 1.0, dropAfterBytes = 4),
            ),
            response = MockResponse(body = "0123456789"),
        )

        val plan = write(Fixtures.request(path = "/x"), snapshot)

        assertEquals(4, plan.bytesToWrite)
        assertEquals(10, plan.declaredContentLength)
        assertTrue(plan.abortAfterWrite)
    }

    @Test
    fun `dropAfterBytes beyond the body length writes the whole body`() = runTest {
        val snapshot = chaosSnapshot(
            ChaosProfile(
                id = "c",
                name = "reset",
                connectionReset = ConnectionResetSpec(probability = 1.0, dropAfterBytes = 9_999),
            ),
            response = MockResponse(body = "short"),
        )

        val plan = write(Fixtures.request(path = "/x"), snapshot)

        assertEquals(5, plan.bytesToWrite)
        assertTrue(plan.abortAfterWrite)
    }

    @Test
    fun `a route chaos override replaces the global profile wholesale`() = runTest {
        val global = ChaosProfile(
            id = "global",
            name = "global",
            delay = DelaySpec.Fixed(5_000),
            errorInjection = ErrorInjection(probability = 1.0, statuses = listOf(WeightedStatus(500))),
        )
        val override = ChaosProfile(id = "route", name = "route", delay = DelaySpec.Fixed(10))
        val snapshot = Fixtures.snapshot(
            routes = listOf(Fixtures.route(pathPattern = "/x", chaosOverride = override)),
            globalChaos = global,
        )

        val plan = write(Fixtures.request(path = "/x"), snapshot)

        // Not merged: no 500 from the global profile, and the delay is the override's.
        assertEquals(200, plan.statusCode)
        assertEquals(10L, traffic.logged.single().chaosApplied?.delayMillis)
    }

    @Test
    fun `the injected delay suspends without being counted in the entry duration`() = runTest {
        val snapshot = chaosSnapshot(ChaosProfile(id = "c", name = "slow", delay = DelaySpec.Fixed(2_400)))

        val before = currentTime
        handle(Fixtures.request(path = "/x"), snapshot, config) { }
        val elapsed = currentTime - before

        assertEquals("the delay must actually suspend", 2_400L, elapsed)
        val entry = traffic.logged.single()
        assertEquals("and must not be counted as response time", 0L, entry.durationMillis)
        assertEquals(2_400L, entry.chaosApplied?.delayMillis)
    }

    // ---------------------------------------------------------------- logging

    @Test
    fun `the entry records the matched route, its score and any unresolved placeholders`() = runTest {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(
                    id = "r1",
                    pathPattern = "/users/{id}",
                    response = MockResponse(body = """{"id":"{{path.id}}","x":"{{path.nope}}"}"""),
                ),
            ),
        )

        write(Fixtures.request(path = "/users/7"), snapshot)

        val entry = traffic.logged.single()
        assertEquals("r1", entry.matchedRouteId)
        assertEquals(14, entry.matchScore)
        assertEquals(listOf("path.nope"), entry.unresolvedPlaceholders)
        assertEquals("192.168.1.10", entry.clientIp)
    }

    @Test
    fun `a throwing transport is logged as ERRORED and rethrown`() = runTest {
        val snapshot = Fixtures.snapshot(listOf(Fixtures.route(pathPattern = "/x")))

        val thrown = runCatching {
            handle(Fixtures.request(path = "/x"), snapshot, config) { error("socket died") }
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertEquals(TrafficEntry.Outcome.ERRORED, traffic.logged.single().outcome)
    }

    @Test
    fun `the resolution reports what happened`() = runTest {
        val snapshot = Fixtures.snapshot(listOf(Fixtures.route(id = "r1", pathPattern = "/x")))

        val matched = handle(Fixtures.request(path = "/x"), snapshot, config) { }
        val unmatched = handle(Fixtures.request(path = "/y"), snapshot, config) { }

        assertTrue(matched is MockResolution.Matched)
        assertEquals("r1", (matched as MockResolution.Matched).route.id)
        assertTrue(unmatched is MockResolution.Unmatched)
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun write(
        request: MockRequest,
        snapshot: RoutingSnapshot,
        serverConfig: ServerConfig = config,
    ): ResponsePlan {
        var captured: ResponsePlan? = null
        handle(request, snapshot, serverConfig) { captured = it }
        return requireNotNull(captured)
    }

    private fun corsSnapshot(
        route: com.minion.mockserver.core.domain.model.MockRoute = Fixtures.route(pathPattern = "/users"),
    ) = Fixtures.snapshot(
        routes = listOf(route),
        collections = listOf(
            Fixtures.collection(
                basePath = "/api",
                cors = CorsConfig(
                    isEnabled = true,
                    allowedOrigins = listOf("https://app.example.com"),
                ),
            ),
        ),
    )

    private fun chaosSnapshot(
        profile: ChaosProfile,
        response: MockResponse = MockResponse(statusCode = 200, body = "{}"),
    ) = Fixtures.snapshot(
        routes = listOf(Fixtures.route(pathPattern = "/x", response = response)),
        globalChaos = profile,
    )
}
