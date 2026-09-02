package com.minion.mockserver.core.domain.usecase.request

import com.minion.mockserver.core.domain.fixture.Fixtures
import com.minion.mockserver.core.domain.model.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC §5.1 — the three placeholder sources, and what happens when one has nothing to give. */
class ResolvePlaceholdersUseCaseTest {

    private val resolve = ResolvePlaceholdersUseCase()

    private val request = Fixtures.request(
        method = "POST",
        path = "/api/v1/users/42",
        query = mapOf("page" to listOf("2", "3"), "empty" to listOf("")),
        headers = mapOf("Authorization" to listOf("Bearer abc"), "X-Multi" to listOf("one", "two")),
    )

    private val captures = mapOf("id" to "42", "name" to "ada")

    @Test
    fun `resolves from all three sources`() {
        val result = resolve(
            """{"id":"{{path.id}}","page":"{{query.page}}","auth":"{{header.Authorization}}"}""",
            request,
            captures,
        )

        assertEquals("""{"id":"42","page":"2","auth":"Bearer abc"}""", result.value)
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun `header lookup is case-insensitive`() {
        assertEquals("Bearer abc", resolve("{{header.authorization}}", request, captures).value)
        assertEquals("Bearer abc", resolve("{{header.AUTHORIZATION}}", request, captures).value)
    }

    @Test
    fun `a repeated value resolves to the first`() {
        assertEquals("2", resolve("{{query.page}}", request, captures).value)
        assertEquals("one", resolve("{{header.X-Multi}}", request, captures).value)
    }

    @Test
    fun `whole path and method resolve for the unmatched fallback`() {
        val result = resolve(MockResponse.DEFAULT_UNMATCHED_BODY, request, emptyMap())
        assertEquals(
            """{"error":"No mock route matched","path":"/api/v1/users/42","method":"POST"}""",
            result.value,
        )
    }

    @Test
    fun `an unresolvable placeholder renders empty and is reported`() {
        // Failing the request would be worse: a mock server that 500s because you typo'd a
        // placeholder is hostile. The miss is recorded instead.
        val result = resolve("""{"id":"{{path.userId}}"}""", request, captures)

        assertEquals("""{"id":""}""", result.value)
        assertEquals(listOf("path.userId"), result.unresolved)
    }

    @Test
    fun `an unknown source prefix is reported rather than rendered`() {
        val result = resolve("{{random.uuid}}", request, captures)
        assertEquals("", result.value)
        assertEquals(listOf("random.uuid"), result.unresolved)
    }

    @Test
    fun `a present but empty query value resolves to empty and is not reported`() {
        val result = resolve("{{query.empty}}", request, captures)
        assertEquals("", result.value)
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun `each unresolved key is reported once`() {
        val result = resolve("{{path.x}} {{path.x}} {{path.y}}", request, captures)
        assertEquals(listOf("path.x", "path.y"), result.unresolved)
    }

    @Test
    fun `a backslash escapes a literal double brace`() {
        assertEquals("""{{path.id}}""", resolve("""\{{path.id}}""", request, captures).value)
    }

    @Test
    fun `an unterminated placeholder is rendered verbatim`() {
        // Swallowing the rest of the body because someone forgot a brace would be far worse.
        assertEquals("""{"a": {{path.id""", resolve("""{"a": {{path.id""", request, captures).value)
    }

    @Test
    fun `whitespace inside the braces is tolerated`() {
        assertEquals("42", resolve("{{ path.id }}", request, captures).value)
    }

    @Test
    fun `a value containing a quote is substituted as-is`() {
        // Substitution is string-level and post-serialisation, so this produces invalid JSON on
        // purpose — the editor warns at authoring time rather than the server escaping on the
        // author's behalf and breaking numeric ids.
        val quoted = Fixtures.request(path = "/x")
        val result = resolve("""{"n":"{{path.n}}"}""", quoted, mapOf("n" to """a"b"""))
        assertEquals("""{"n":"a"b"}""", result.value)
    }

    @Test
    fun `a template with no placeholders is returned untouched`() {
        val body = """{"static":true}"""
        assertEquals(body, resolve(body, request, captures).value)
    }

    @Test
    fun `render substitutes header values but not header names or the status`() {
        val response = MockResponse(
            statusCode = 201,
            headers = mapOf("Location" to "/users/{{path.id}}", "X-{{path.id}}" to "static"),
            body = """{"id":"{{path.id}}"}""",
        )

        val rendered = resolve.render(response, request, captures)

        assertEquals(201, rendered.statusCode)
        assertEquals("/users/42", rendered.headers["Location"])
        assertEquals("static", rendered.headers["X-{{path.id}}"])
        assertEquals("""{"id":"42"}""", rendered.body)
    }

    @Test
    fun `render collects unresolved placeholders from the body and the headers`() {
        val response = MockResponse(
            headers = mapOf("X-Trace" to "{{header.X-Trace-Id}}"),
            body = "{{path.missing}}",
        )

        val rendered = resolve.render(response, request, captures)

        assertEquals(listOf("path.missing", "header.X-Trace-Id"), rendered.unresolvedPlaceholders)
    }
}
