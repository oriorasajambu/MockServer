package com.rio.mockserver.core.server

import com.rio.mockserver.core.domain.model.ApiKeyConfig
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.ConnectionResetSpec
import com.rio.mockserver.core.domain.model.CorsConfig
import com.rio.mockserver.core.domain.model.ErrorInjection
import com.rio.mockserver.core.domain.model.HttpMethodFilter
import com.rio.mockserver.core.domain.model.MalformedBodySpec
import com.rio.mockserver.core.domain.model.MockResponse
import com.rio.mockserver.core.domain.model.PauseSpec
import com.rio.mockserver.core.domain.model.RequestMatcher
import com.rio.mockserver.core.domain.model.RoutingSnapshot
import com.rio.mockserver.core.domain.model.ServerConfig
import com.rio.mockserver.core.domain.model.StringOp
import com.rio.mockserver.core.domain.model.TrafficEntry
import com.rio.mockserver.core.domain.model.WeightedStatus
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.head
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC §14, tier 2 — the wiring the unit tests cannot see.
 *
 * The domain tests already prove that the pipeline *decides* correctly. These prove that the
 * decision reaches the socket: that headers and `Content-Length` come out right, that the stages
 * run in the order SPEC §4.1 fixes, that chaos's byte arithmetic survives the writer, and that
 * a snapshot swap between two requests takes effect on the second and not the first.
 */
class MockServerEngineTest {

    // ---------------------------------------------------------------- end to end

    @Test
    fun `a matched route reaches the wire with its status, body and headers`() = testApplication {
        val traffic = RecordingTraffic()
        mount(
            traffic = traffic,
            snapshot = EngineFixture.snapshot(
                listOf(
                    EngineFixture.route(
                        method = HttpMethodFilter.GET,
                        pathPattern = "/users/{id}",
                        response = MockResponse(
                            statusCode = 201,
                            headers = mapOf("X-Custom" to "yes", "Location" to "/users/{{path.id}}"),
                            body = """{"id":"{{path.id}}"}""",
                        ),
                    ),
                ),
            ),
        )

        val response = client.get("/users/42")

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("""{"id":"42"}""", response.bodyAsText())
        assertEquals("yes", response.headers["X-Custom"])
        assertEquals("/users/42", response.headers["Location"])
        assertEquals("11", response.headers["Content-Length"])
        assertTrue(response.headers["Content-Type"].orEmpty().contains("application/json"))
        assertEquals("r1", traffic.logged.single().matchedRouteId)
    }

    @Test
    fun `a trailing slash and a duplicated slash reach the same route`() = testApplication {
        mount(snapshot = EngineFixture.snapshot(listOf(EngineFixture.route(pathPattern = "/api/users"))))

        // A leading `//` is not tested here: the client parses `//users` as a protocol-relative
        // URL and never sends it. The normalisation itself is covered in PathNormaliserTest.
        listOf("/api/users", "/api/users/", "/api//users").forEach { path ->
            assertEquals(path, HttpStatusCode.OK, client.get(path).status)
        }
    }

    @Test
    fun `an unmatched request gets the fallback with placeholders resolved`() = testApplication {
        val traffic = RecordingTraffic()
        mount(traffic = traffic, snapshot = EngineFixture.snapshot(emptyList()))

        val response = client.get("/nothing/here?x=1")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            """{"error":"No mock route matched","path":"/nothing/here","method":"GET"}""",
            response.bodyAsText(),
        )
        assertEquals(TrafficEntry.Outcome.UNMATCHED, traffic.logged.single().outcome)
    }

    @Test
    fun `HEAD returns no body with the length a GET would have declared`() = testApplication {
        mount(
            snapshot = EngineFixture.snapshot(
                listOf(
                    EngineFixture.route(
                        method = HttpMethodFilter.GET,
                        pathPattern = "/users",
                        response = MockResponse(body = """{"users":[]}"""),
                    ),
                ),
            ),
        )

        val response = client.head("/users")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.bodyAsText())
        assertEquals("12", response.headers["Content-Length"])
    }

    @Test
    fun `a query and a header matcher are evaluated against the real request`() = testApplication {
        mount(
            snapshot = EngineFixture.snapshot(
                listOf(
                    EngineFixture.route(
                        id = "specific",
                        pathPattern = "/search",
                        matchers = listOf(
                            RequestMatcher.Query("q", StringOp.EQUALS, "kotlin"),
                            RequestMatcher.Header("X-Tenant", StringOp.EXISTS),
                        ),
                        response = MockResponse(body = """{"hit":true}"""),
                    ),
                    EngineFixture.route(id = "generic", pathPattern = "/search"),
                ),
            ),
        )

        val hit = client.get("/search?q=kotlin") { header("X-Tenant", "acme") }
        val miss = client.get("/search?q=java") { header("X-Tenant", "acme") }

        assertEquals("""{"hit":true}""", hit.bodyAsText())
        assertEquals("{}", miss.bodyAsText())
    }

    @Test
    fun `a body subset matcher sees the posted body`() = testApplication {
        mount(
            snapshot = EngineFixture.snapshot(
                listOf(
                    EngineFixture.route(
                        id = "express",
                        method = HttpMethodFilter.POST,
                        pathPattern = "/orders",
                        matchers = listOf(RequestMatcher.BodySubset("""{"shipping":"express"}""")),
                        response = MockResponse(statusCode = 201, body = """{"queue":"fast"}"""),
                    ),
                    EngineFixture.route(
                        id = "standard",
                        method = HttpMethodFilter.POST,
                        pathPattern = "/orders",
                        response = MockResponse(statusCode = 201, body = """{"queue":"normal"}"""),
                    ),
                ),
            ),
        )

        val express = client.post("/orders") { setBody("""{"shipping":"express","items":2}""") }
        val standard = client.post("/orders") { setBody("""{"shipping":"standard"}""") }

        assertEquals("""{"queue":"fast"}""", express.bodyAsText())
        assertEquals("""{"queue":"normal"}""", standard.bodyAsText())
    }

    // ---------------------------------------------------------------- stage ordering

    @Test
    fun `pause answers before the API key guard`() = testApplication {
        mount(
            config = ServerConfig(apiKey = ApiKeyConfig(value = "secret")),
            snapshot = EngineFixture.snapshot(
                routes = listOf(EngineFixture.route(pathPattern = "/x")),
                pause = PauseSpec(isEnabled = true, statusCode = 503, retryAfterSeconds = 7),
            ),
        )

        val response = client.get("/x")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("7", response.headers["Retry-After"])
    }

    @Test
    fun `the API key guard rejects a missing or wrong key and accepts the right one`() = testApplication {
        mount(
            config = ServerConfig(apiKey = ApiKeyConfig(headerName = "X-Mock-Key", value = "secret")),
            snapshot = EngineFixture.snapshot(listOf(EngineFixture.route(pathPattern = "/x"))),
        )

        assertEquals(HttpStatusCode.Unauthorized, client.get("/x").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/x") { header("X-Mock-Key", "wrong") }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/x") { header("X-Mock-Key", "secret") }.status,
        )
    }

    @Test
    fun `OPTIONS bypasses the API key guard and is answered as a preflight`() = testApplication {
        mount(
            config = ServerConfig(apiKey = ApiKeyConfig(value = "secret")),
            snapshot = corsSnapshot(),
        )

        val response = client.options("/api/users") {
            header("Origin", "https://app.example.com")
            header("Access-Control-Request-Method", "POST")
            header("Access-Control-Request-Headers", "X-Tenant, Content-Type")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("https://app.example.com", response.headers["Access-Control-Allow-Origin"])
        assertEquals("X-Tenant, Content-Type", response.headers["Access-Control-Allow-Headers"])
        assertNotNull(response.headers["Access-Control-Allow-Methods"])
        assertEquals("3600", response.headers["Access-Control-Max-Age"])
    }

    @Test
    fun `CORS headers are injected on a matched response and beat the user's own`() = testApplication {
        mount(
            snapshot = corsSnapshot(
                route = EngineFixture.route(
                    pathPattern = "/users",
                    response = MockResponse(
                        headers = mapOf("Access-Control-Allow-Origin" to "https://evil.example.com"),
                        body = "{}",
                    ),
                ),
            ),
        )

        val response = client.get("/api/users") { header("Origin", "https://app.example.com") }

        assertEquals("https://app.example.com", response.headers["Access-Control-Allow-Origin"])
        assertEquals("Origin", response.headers["Vary"])
    }

    @Test
    fun `OPTIONS falls through to matching when the collection has CORS off`() = testApplication {
        mount(
            snapshot = EngineFixture.snapshot(
                listOf(
                    EngineFixture.route(
                        method = HttpMethodFilter.OPTIONS,
                        pathPattern = "/x",
                        response = MockResponse(body = """{"handled":true}"""),
                    ),
                ),
            ),
        )

        assertEquals("""{"handled":true}""", client.options("/x").bodyAsText())
    }

    @Test
    fun `a body over the ceiling is refused with 413`() = testApplication {
        val traffic = RecordingTraffic()
        mount(
            traffic = traffic,
            config = ServerConfig(maxRequestBodyBytes = 64),
            snapshot = EngineFixture.snapshot(
                listOf(EngineFixture.route(method = HttpMethodFilter.POST, pathPattern = "/upload")),
            ),
        )

        val response = client.post("/upload") { setBody("x".repeat(500)) }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        val entry = traffic.logged.single()
        assertEquals(TrafficEntry.Outcome.TOO_LARGE, entry.outcome)
        assertNull(entry.requestBody)
    }

    // ---------------------------------------------------------------- chaos wiring

    @Test
    fun `an injected error status reaches the client`() = testApplication {
        mount(
            snapshot = EngineFixture.snapshot(
                routes = listOf(EngineFixture.route(pathPattern = "/x")),
                globalChaos = ChaosProfile(
                    id = "c",
                    name = "always 503",
                    errorInjection = ErrorInjection(
                        probability = 1.0,
                        statuses = listOf(WeightedStatus(503)),
                        body = """{"chaos":true}""",
                    ),
                ),
            ),
        )

        val response = client.get("/x")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("""{"chaos":true}""", response.bodyAsText())
    }

    @Test
    fun `a truncated body goes out under a Content-Length declaring the full size`() = testApplication {
        val traffic = RecordingTraffic()
        mount(
            traffic = traffic,
            snapshot = EngineFixture.snapshot(
                routes = listOf(
                    EngineFixture.route(
                        pathPattern = "/x",
                        response = MockResponse(body = "0123456789"),
                    ),
                ),
                globalChaos = ChaosProfile(
                    id = "c",
                    name = "malformed",
                    malformedBody = MalformedBodySpec(probability = 1.0, truncateAtPercent = 60),
                ),
            ),
        )

        val failure = runCatching { client.get("/x") }.exceptionOrNull()

        // The header is the lie; the body is the truth, and a strict client rejects the pair.
        // That rejection *is* the failure mode SPEC §6.2 mode 4 exists to reproduce.
        assertTrue(
            "expected a length mismatch, got: $failure",
            failure?.message.orEmpty().contains("Content-Length mismatch"),
        )
        assertTrue(failure?.message.orEmpty().contains("expected 10"))
        assertTrue(failure?.message.orEmpty().contains("received 6"))
        assertEquals(TrafficEntry.Outcome.MATCHED, traffic.logged.single().outcome)
        assertTrue(traffic.logged.single().responseTruncated)
    }

    @Test
    fun `a connection reset does not produce a clean response`() = testApplication {
        mount(
            snapshot = EngineFixture.snapshot(
                routes = listOf(
                    EngineFixture.route(pathPattern = "/x", response = MockResponse(body = "0123456789")),
                ),
                globalChaos = ChaosProfile(
                    id = "c",
                    name = "reset",
                    connectionReset = ConnectionResetSpec(probability = 1.0, dropAfterBytes = 4),
                ),
            ),
        )

        val failure = runCatching { client.get("/x").bodyAsText() }

        assertTrue(
            "the client must not see a complete body: $failure",
            failure.isFailure || failure.getOrNull() != "0123456789",
        )
    }

    @Test
    fun `chaos is recorded on the traffic entry`() = testApplication {
        val traffic = RecordingTraffic()
        mount(
            traffic = traffic,
            snapshot = EngineFixture.snapshot(
                routes = listOf(EngineFixture.route(pathPattern = "/x")),
                globalChaos = ChaosProfile(
                    id = "c",
                    name = "errors",
                    errorInjection = ErrorInjection(probability = 1.0, statuses = listOf(WeightedStatus(500))),
                ),
            ),
        )

        client.get("/x")

        // Without this an injected 500 is indistinguishable from a bug in the mock rules.
        assertNotNull(traffic.logged.single().chaosApplied?.failure)
    }

    // ---------------------------------------------------------------- hot apply

    @Test
    fun `swapping the snapshot between requests changes only the second`() = testApplication {
        val engine = EngineFixture.engine()
        engine.configure(ServerConfig())
        engine.applySnapshot(
            EngineFixture.snapshot(
                listOf(EngineFixture.route(pathPattern = "/x", response = MockResponse(body = """{"v":1}"""))),
            ),
        )
        application { engine.install(this) }

        assertEquals("""{"v":1}""", client.get("/x").bodyAsText())

        engine.applySnapshot(
            EngineFixture.snapshot(
                listOf(EngineFixture.route(pathPattern = "/x", response = MockResponse(body = """{"v":2}"""))),
            ),
        )

        // No restart, no dropped socket: the same connection now serves the new rules.
        assertEquals("""{"v":2}""", client.get("/x").bodyAsText())
    }

    @Test
    fun `the engine serves 404 from the empty snapshot before anything is applied`() = testApplication {
        val engine = EngineFixture.engine()
        engine.configure(ServerConfig())
        application { engine.install(this) }

        assertEquals(HttpStatusCode.NotFound, client.get("/anything").status)
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    fun `start binds, reports the port, and stop is idempotent`() = runTest {
        val engine = EngineFixture.engine()
        engine.applySnapshot(EngineFixture.snapshot(listOf(EngineFixture.route(pathPattern = "/x"))))

        val bound = engine.start(ServerConfig(port = 0, bindAddress = "127.0.0.1"))
        assertTrue(bound.isSuccess)
        assertTrue(engine.status.value.isRunning)

        assertTrue(engine.stop().isSuccess)
        assertTrue(engine.stop().isSuccess)
        assertEquals(com.rio.mockserver.core.domain.model.ServerStatus.Stopped, engine.status.value)
    }

    // ---------------------------------------------------------------- helpers

    private fun ApplicationTestBuilder.mount(
        snapshot: RoutingSnapshot,
        traffic: RecordingTraffic = RecordingTraffic(),
        config: ServerConfig = ServerConfig(),
    ) {
        val engine = EngineFixture.engine(traffic)
        engine.configure(config)
        engine.applySnapshot(snapshot)
        application { engine.install(this) }
    }

    private fun corsSnapshot(
        route: com.rio.mockserver.core.domain.model.MockRoute =
            EngineFixture.route(pathPattern = "/users"),
    ) = EngineFixture.snapshot(
        routes = listOf(route),
        collections = listOf(
            EngineFixture.collection(
                basePath = "/api",
                cors = CorsConfig(
                    isEnabled = true,
                    allowedOrigins = listOf("https://app.example.com"),
                ),
            ),
        ),
    )
}
