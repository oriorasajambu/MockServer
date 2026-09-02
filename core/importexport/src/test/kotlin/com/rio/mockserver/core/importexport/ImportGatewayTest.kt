package com.rio.mockserver.core.importexport

import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.HttpMethodFilter
import com.rio.mockserver.core.domain.model.ImportFormat
import com.rio.mockserver.core.domain.model.ImportPreview
import com.rio.mockserver.core.domain.util.IdGenerator
import com.rio.mockserver.core.importexport.importer.HarImporter
import com.rio.mockserver.core.importexport.importer.ImportGatewayImpl
import com.rio.mockserver.core.importexport.importer.OpenApiImporter
import com.rio.mockserver.core.importexport.importer.PostmanImporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC §9.1 — the importers, against golden files.
 *
 * Golden files rather than hand-built DTOs, because what breaks an importer is the shape real
 * exports have: Postman writing a URL two different ways in one file, a HAR repeating the same
 * request eleven times, a spec that defines an operation and gives it no example. None of that is
 * visible from a fixture someone wrote from the schema.
 */
class ImportGatewayTest {

    private var counter = 0
    private val gateway = ImportGatewayImpl(
        openApiImporter = OpenApiImporter(),
        postmanImporter = PostmanImporter(),
        harImporter = HarImporter(),
        idGenerator = IdGenerator { "id-${++counter}" },
    )

    // ---------------------------------------------------------------- detection

    @Test
    fun `detects each format by content, not by extension`() {
        // All three of these routinely arrive named export.json.
        assertEquals(ImportFormat.OPENAPI, gateway.detectFormat(golden("openapi-3.0.json"), "export.json"))
        assertEquals(ImportFormat.POSTMAN, gateway.detectFormat(golden("postman-v2.1.json"), "export.json"))
        assertEquals(ImportFormat.HAR, gateway.detectFormat(golden("har-1.2.json"), "export.json"))
        assertEquals(ImportFormat.OPENAPI, gateway.detectFormat(golden("openapi-3.1.yaml"), "spec.yaml"))
    }

    @Test
    fun `an unrecognised document is not guessed at`() {
        assertNull(gateway.detectFormat("""{"hello":"world"}""", "thing.json"))
        assertNull(gateway.detectFormat("", null))
        assertNull(gateway.detectFormat("just some prose", "notes.txt"))
    }

    // ---------------------------------------------------------------- OpenAPI

    @Test
    fun `OpenAPI 3-0 imports paths, methods and example bodies`() = runTest {
        val preview = parse(golden("openapi-3.0.json"), ImportFormat.OPENAPI)

        assertEquals("Orders API", preview.suggestedCollectionName)
        // Only the path component of the server URL: the host names a server this app replaces.
        assertEquals("/api/v1", preview.suggestedBasePath)
        assertEquals(
            listOf("GET /users/{id}", "DELETE /users/{id}", "POST /users"),
            preview.routes.map { it.label },
        )

        val getUser = preview.routes.first()
        assertEquals(HttpMethodFilter.GET, getUser.route.method)
        assertEquals(200, getUser.route.response.statusCode)
        assertTrue(getUser.route.response.body.contains("\"name\": \"Ada\""))
        assertFalse(getUser.route.needsBody)
    }

    @Test
    fun `a non-2xx response becomes an alternate, not a second route`() = runTest {
        // Two identically-shaped routes would shadow each other, so the 404 is offered in the
        // editor instead (SPEC §17.3).
        val preview = parse(golden("openapi-3.0.json"), ImportFormat.OPENAPI)
        val getUser = preview.routes.first()

        assertEquals(listOf(404), getUser.alternateResponses.map { it.statusCode })
        assertEquals("No such user", getUser.alternateResponses.single().description)
        assertTrue(getUser.alternateResponses.single().response.body.contains("not found"))
    }

    @Test
    fun `the lowest 2xx becomes the route response`() = runTest {
        val preview = parse(golden("openapi-3.0.json"), ImportFormat.OPENAPI)
        assertEquals(201, preview.routes.first { it.label == "POST /users" }.route.response.statusCode)
        assertEquals(204, preview.routes.first { it.label == "DELETE /users/{id}" }.route.response.statusCode)
    }

    @Test
    fun `an examples map is unwrapped from its value key`() = runTest {
        val preview = parse(golden("openapi-3.0.json"), ImportFormat.OPENAPI)
        val post = preview.routes.first { it.label == "POST /users" }
        assertTrue(post.route.response.body.contains("\"id\": 43"))
    }

    @Test
    fun `OpenAPI 3-1 in YAML produces the same shape as JSON`() = runTest {
        val preview = parse(golden("openapi-3.1.yaml"), ImportFormat.OPENAPI)

        assertEquals("Inventory API", preview.suggestedCollectionName)
        assertEquals("/v2", preview.suggestedBasePath)
        assertEquals(listOf("GET /items", "PUT /items/{sku}"), preview.routes.map { it.label })
        assertTrue(preview.routes.first().route.response.body.contains("\"sku\": \"A1\""))
    }

    @Test
    fun `a schema-level example is used when the media type has none`() = runTest {
        val preview = parse(golden("openapi-3.1.yaml"), ImportFormat.OPENAPI)
        val put = preview.routes.first { it.label == "PUT /items/{sku}" }
        assertTrue(put.route.response.body.contains("\"qty\": 9"))
        assertFalse(put.route.needsBody)
    }

    @Test
    fun `a schema without an example is never synthesised into a fake body`() = runTest {
        // A generated body full of "string" and 0 looks real and tests nothing, so the route is
        // created empty and flagged instead (SPEC §9.1).
        val preview = parse(golden("openapi-no-examples.json"), ImportFormat.OPENAPI)
        val route = preview.routes.single()

        assertEquals("", route.route.response.body)
        assertTrue(route.route.needsBody)
        assertEquals(1, preview.needsBodyCount)
    }

    @Test
    fun `a document with no paths fails with a pointer, not a stack trace`() = runTest {
        val result = gateway.parse("""{"openapi":"3.0.0","info":{"title":"x"}}""", ImportFormat.OPENAPI)

        val failure = result as AppResult.Failure
        val error = failure.error as com.rio.mockserver.core.common.error.DomainError.ImportParseError
        assertEquals("/paths", error.location)
    }

    // ---------------------------------------------------------------- Postman

    @Test
    fun `Postman folders are flattened and saved examples become responses`() = runTest {
        val preview = parse(golden("postman-v2.1.json"), ImportFormat.POSTMAN)

        assertEquals("Billing", preview.suggestedCollectionName)
        assertEquals(
            listOf("GET /invoices", "GET /invoices/{invoiceId}", "POST /payments"),
            preview.routes.map { it.label },
        )

        val list = preview.routes.first()
        assertEquals(200, list.route.response.statusCode)
        assertEquals("""{"invoices":[]}""", list.route.response.body)
        assertEquals("application/json", list.route.response.headers["Content-Type"])
    }

    @Test
    fun `the lowest 2xx saved example wins over an error example`() = runTest {
        val preview = parse(golden("postman-v2.1.json"), ImportFormat.POSTMAN)
        assertEquals(200, preview.routes.first().route.response.statusCode)
    }

    @Test
    fun `a string URL and an object URL both resolve, and colon params are rewritten`() = runTest {
        val preview = parse(golden("postman-v2.1.json"), ImportFormat.POSTMAN)

        // The string form, with Postman's `:invoiceId` rewritten to this app's `{invoiceId}`.
        val get = preview.routes[1]
        assertEquals("/invoices/{invoiceId}", get.route.pathPattern)

        // The object form, with `{{baseUrl}}` dropped — it names a host this app replaces.
        assertEquals("/payments", preview.routes[2].route.pathPattern)
    }

    @Test
    fun `a request with no saved example is imported and flagged`() = runTest {
        val preview = parse(golden("postman-v2.1.json"), ImportFormat.POSTMAN)
        val noExample = preview.routes[1]

        assertTrue(noExample.route.needsBody)
        assertTrue(preview.warnings.any { it.contains("Get invoice") })
    }

    // ---------------------------------------------------------------- HAR

    @Test
    fun `HAR entries become routes with their real captured responses`() = runTest {
        val preview = parse(golden("har-1.2.json"), ImportFormat.HAR)
        val config = preview.routes.first()

        assertEquals("GET /config", config.label)
        assertEquals(200, config.route.response.statusCode)
        assertEquals("""{"feature":true}""", config.route.response.body)
    }

    @Test
    fun `a repeated request is collapsed into the first occurrence`() = runTest {
        // A page load produces the same GET eleven times; eleven identical routes would shadow
        // each other and fill the list with rules that can never win.
        val preview = parse(golden("har-1.2.json"), ImportFormat.HAR)

        assertEquals(listOf("GET /config", "GET /assets/logo.png"), preview.routes.map { it.label })
        assertTrue(preview.warnings.any { it.contains("collapsed") })
    }

    @Test
    fun `transfer headers are dropped so the replayed response is readable`() = runTest {
        // Replaying a captured `Content-Encoding: gzip` against a plain-text body produces a
        // response no client can read.
        val preview = parse(golden("har-1.2.json"), ImportFormat.HAR)
        val headers = preview.routes.first().route.response.headers

        assertEquals("application/json", headers["Content-Type"])
        assertNull(headers["Content-Encoding"])
        assertNull(headers["Date"])
    }

    @Test
    fun `a binary response is imported empty and flagged`() = runTest {
        val preview = parse(golden("har-1.2.json"), ImportFormat.HAR)
        val image = preview.routes.last()

        assertEquals("", image.route.response.body)
        assertTrue(image.route.needsBody)
        assertTrue(preview.warnings.any { it.contains("binary") })
    }

    @Test
    fun `the suggested collection name comes from the captured host`() = runTest {
        assertEquals("api.example.com", parse(golden("har-1.2.json"), ImportFormat.HAR).suggestedCollectionName)
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun parse(content: String, format: ImportFormat): ImportPreview =
        when (val result = gateway.parse(content, format)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> error("parse failed: ${result.error}")
        }

    private fun golden(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("golden/$name")) { "missing golden/$name" }
            .bufferedReader()
            .use { it.readText() }
}
