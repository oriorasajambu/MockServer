package com.minion.mockserver.core.importexport

import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.ChaosApplied
import com.minion.mockserver.core.domain.model.ChaosFailure
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.ConnectionResetSpec
import com.minion.mockserver.core.domain.model.CorsConfig
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.domain.model.ErrorInjection
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.StringOp
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.JsonTree
import com.minion.mockserver.core.domain.util.TimeProvider
import com.minion.mockserver.core.importexport.exporter.ExportGatewayImpl
import com.minion.mockserver.core.importexport.importer.HarImporter
import com.minion.mockserver.core.importexport.importer.ImportGatewayImpl
import com.minion.mockserver.core.importexport.importer.OpenApiImporter
import com.minion.mockserver.core.importexport.importer.PostmanImporter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC §7.4 and §9.2 — what leaves the app, and whether it can come back. */
class ExportGatewayTest {

    private val time = object : TimeProvider {
        override fun nowMillis(): Long = 1_767_312_245_000L
        override fun monotonicNanos(): Long = 0L
    }
    private val exporter = ExportGatewayImpl(time)

    private val collection = MockCollection(
        id = "c1",
        name = "Orders",
        basePath = "/api/v1",
        order = 2,
        cors = CorsConfig(isEnabled = true, allowedOrigins = listOf("https://app.example.com")),
        createdAt = 1_000L,
    )

    private val chaos = ChaosProfile(
        id = "p1",
        name = "Flaky",
        delay = DelaySpec.Random(100, 400),
        errorInjection = ErrorInjection(probability = 0.25),
        connectionReset = ConnectionResetSpec(probability = 0.1, dropAfterBytes = 64),
    )

    private val routes = listOf(
        MockRoute(
            id = "r1",
            collectionId = "c1",
            name = "Get user",
            method = HttpMethodFilter.GET,
            pathPattern = "/users/{id}",
            matchers = listOf(
                RequestMatcher.Header("X-Tenant", StringOp.EQUALS, "acme"),
                RequestMatcher.BodySubset("""{"a":1}"""),
            ),
            response = MockResponse(statusCode = 200, body = """{"id":"{{path.id}}"}"""),
            chaosOverride = chaos,
            createdAt = 2_000L,
        ),
        MockRoute(
            id = "r2",
            collectionId = "c1",
            name = "Slow list",
            pathPattern = "/users",
            response = MockResponse(body = "[]", delayMillisOverride = 250),
            isEnabled = false,
            createdAt = 3_000L,
        ),
    )

    // ---------------------------------------------------------------- native

    @Test
    fun `a native export round-trips with full fidelity`() = runTest {
        // The one guard that matters for this format: everything a Postman export drops —
        // matchers, chaos overrides, CORS, ids, creation times — has to survive.
        val json = exporter.toNativeJson(listOf(collection), routes, listOf(chaos))

        val restored = when (val result = gateway().parseNative(json)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> error("round trip failed: ${result.error}")
        }

        assertEquals(1, restored.schemaVersion)
        assertEquals(listOf(collection), restored.collections)
        assertEquals(routes, restored.routes)
        assertEquals(listOf(chaos), restored.chaosProfiles)
    }

    @Test
    fun `a native export is detected as native rather than as another format`() {
        val json = exporter.toNativeJson(listOf(collection), routes, listOf(chaos))
        assertEquals(
            com.minion.mockserver.core.domain.model.ImportFormat.NATIVE,
            gateway().detectFormat(json, "export.json"),
        )
    }

    @Test
    fun `a v1 file written by an earlier build still parses`() = runTest {
        // The compatibility guard the format's design leans on: the payload is the domain models
        // themselves, so renaming a domain field must fail here rather than silently breaking
        // every file a user has already exported.
        val result = gateway().parseNative(golden("native-export-v1.json"))

        val restored = (result as AppResult.Success).data
        assertEquals(1, restored.schemaVersion)
        assertEquals("Legacy", restored.collections.single().name)

        val route = restored.routes.single()
        assertEquals(HttpMethodFilter.POST, route.method)
        assertEquals("/legacy/{id}", route.pathPattern)
        assertEquals(2, route.matchers.size)
        assertNotNull(route.chaosOverride)
        assertEquals(DelaySpec.Fixed(750), route.chaosOverride?.delay)
    }

    // ---------------------------------------------------------------- Postman

    @Test
    fun `a Postman export produces an importable collection`() = runTest {
        val json = exporter.toPostmanCollection(collection, routes)

        // The strongest available check: it comes back through this app's own Postman importer.
        val preview = when (val result = gateway().parse(json, com.minion.mockserver.core.domain.model.ImportFormat.POSTMAN)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> error("re-import failed: ${result.error}")
        }

        assertEquals("Orders", preview.suggestedCollectionName)
        assertEquals(listOf("/api/v1/users/{id}", "/api/v1/users"), preview.routes.map { it.route.pathPattern })
        assertEquals("""{"id":"{{path.id}}"}""", preview.routes.first().route.response.body)
    }

    @Test
    fun `the Postman loss warning names what is actually at stake`() {
        val warnings = exporter.postmanLossWarnings(routes)

        assertTrue(warnings.any { it.contains("2 matcher(s)") && it.contains("body subset") && it.contains("header") })
        assertTrue(warnings.any { it.contains("1 route(s) have chaos overrides") })
        assertTrue(warnings.any { it.contains("1 route(s) have a fixed delay") })
        assertTrue(warnings.any { it.contains("1 disabled route(s)") })
    }

    @Test
    fun `nothing is warned about when nothing is lost`() {
        val plain = MockRoute(id = "r", collectionId = "c1", name = "Plain", pathPattern = "/x")
        assertEquals(emptyList<String>(), exporter.postmanLossWarnings(listOf(plain)))
    }

    // ---------------------------------------------------------------- HAR

    @Test
    fun `a HAR export round-trips through this app's own HAR importer`() = runTest {
        val json = exporter.toHar(listOf(entry()), "http://192.168.1.45:8080")

        val preview = when (val result = gateway().parse(json, com.minion.mockserver.core.domain.model.ImportFormat.HAR)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> error("re-import failed: ${result.error}")
        }

        val route = preview.routes.single()
        assertEquals("/api/v1/users/7", route.route.pathPattern)
        assertEquals(200, route.route.response.statusCode)
        assertEquals("""{"id":7}""", route.route.response.body)
    }

    @Test
    fun `a HAR export carries the fields a viewer requires`() {
        val json = exporter.toHar(listOf(entry()), "http://192.168.1.45:8080")
        val log = (JsonTree.parseOrNull(json) as JsonObject)["log"]!!.jsonObject
        val harEntry = log["entries"]!!.jsonArray.single().jsonObject

        assertEquals("1.2", log["version"]!!.jsonPrimitive.content)
        assertTrue(harEntry["startedDateTime"]!!.jsonPrimitive.content.startsWith("2026-"))
        assertNotNull(harEntry["timings"])
        assertNotNull(harEntry["cache"])
        assertEquals(
            "http://192.168.1.45:8080/api/v1/users/7?expand=orders",
            harEntry["request"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    // ---------------------------------------------------------------- cURL

    @Test
    fun `a cURL reproduction includes method, headers, body and the full URL`() {
        val curl = exporter.toCurl(entry(), "http://192.168.1.45:8080")

        assertTrue(curl.startsWith("curl -X POST"))
        assertTrue(curl.contains("-H 'Content-Type: application/json'"))
        assertTrue(curl.contains("""--data-raw '{"name":"Ada"}'"""))
        assertTrue(curl.endsWith("'http://192.168.1.45:8080/api/v1/users/7?expand=orders'"))
    }

    @Test
    fun `headers cURL sets itself are not repeated`() {
        val curl = exporter.toCurl(
            entry().copy(requestHeaders = mapOf("Host" to listOf("phone"), "Accept" to listOf("*/*"))),
            "http://x",
        )

        assertTrue(curl.contains("Accept"))
        assertTrue("Host must not be repeated: $curl", !curl.contains("Host:"))
    }

    @Test
    fun `an apostrophe in the body survives shell quoting`() {
        // Without the '\'' dance the command either fails to parse or, worse, runs with the wrong
        // argument boundaries.
        val curl = exporter.toCurl(entry().copy(requestBody = """{"name":"O'Neill"}"""), "http://x")
        assertTrue(curl, curl.contains("""'{"name":"O'\''Neill"}'"""))
    }

    // ---------------------------------------------------------------- transcript

    @Test
    fun `a transcript shows the request, the outcome and the response`() {
        val transcript = exporter.toTranscript(
            entry().copy(
                matchedRouteName = "Get user",
                chaosApplied = ChaosApplied(delayMillis = 250, failure = ChaosFailure.ErrorInjected(503)),
            ),
            "http://192.168.1.45:8080",
        )

        assertTrue(transcript.contains("POST http://192.168.1.45:8080/api/v1/users/7?expand=orders"))
        assertTrue(transcript.contains("Matched: Get user"))
        assertTrue(transcript.contains("Chaos:"))
        assertTrue(transcript.contains("200 in 12 ms"))
    }

    @Test
    fun `an unmatched entry says so`() {
        assertTrue(exporter.toTranscript(entry(), "http://x").contains("Unmatched"))
    }

    // ---------------------------------------------------------------- helpers

    private fun entry() = TrafficEntry(
        id = "e1",
        receivedAt = 1_767_312_245_000L,
        clientIp = "192.168.1.22",
        method = "POST",
        path = "/api/v1/users/7",
        query = mapOf("expand" to listOf("orders")),
        requestHeaders = mapOf("Content-Type" to listOf("application/json")),
        requestBody = """{"name":"Ada"}""",
        requestByteCount = 15,
        statusCode = 200,
        responseHeaders = mapOf("Content-Type" to "application/json"),
        responseBody = """{"id":7}""",
        durationMillis = 12,
    )

    private fun gateway(): ImportGatewayImpl {
        var counter = 0
        return ImportGatewayImpl(
            openApiImporter = OpenApiImporter(),
            postmanImporter = PostmanImporter(),
            harImporter = HarImporter(),
            idGenerator = IdGenerator { "id-${++counter}" },
        )
    }

    private fun golden(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("golden/$name")) { "missing golden/$name" }
            .bufferedReader()
            .use { it.readText() }
}
