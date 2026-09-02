package com.minion.mockserver.core.domain.usecase.route

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.domain.fixture.Fixtures
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.StringOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC §10.2 — what blocks Save, and what merely warns. */
class ValidateRouteUseCaseTest {

    private val validate = ValidateRouteUseCase()

    @Test
    fun `a well-formed route is saveable`() {
        val validation = validate(
            Fixtures.route(pathPattern = "/users/{id}", response = MockResponse(body = """{"ok":true}""")),
        )
        assertTrue(validation.errors.toString(), validation.canSave)
    }

    @Test
    fun `invalid JSON blocks saving only when Content-Type says JSON`() {
        val asJson = Fixtures.route(response = MockResponse(body = "{not json"))
        val asText = Fixtures.route(
            response = MockResponse(headers = mapOf("Content-Type" to "text/plain"), body = "{not json"),
        )

        assertFalse(validate(asJson).canSave)
        assertTrue(validate(asText).canSave)
    }

    @Test
    fun `a body whose only irregularity is a placeholder still validates`() {
        // `{"id": {{path.id}}}` is not valid JSON as written but is exactly right once resolved
        // for a numeric id, so it must not block Save.
        val route = Fixtures.route(
            pathPattern = "/users/{id}",
            response = MockResponse(body = """{"id": {{path.id}}}"""),
        )
        assertTrue(validate(route).canSave)
    }

    @Test
    fun `an unquoted placeholder warns without blocking`() {
        val route = Fixtures.route(
            pathPattern = "/users/{id}",
            response = MockResponse(body = """{"id": {{path.id}}}"""),
        )
        val validation = validate(route)

        assertTrue(validation.canSave)
        assertTrue(
            validation.warnings.contains(RouteValidation.Warning.UnquotedPlaceholder("path.id")),
        )
    }

    @Test
    fun `a placeholder naming a capture the pattern does not define warns`() {
        val route = Fixtures.route(
            pathPattern = "/users/{id}",
            response = MockResponse(body = """{"who":"{{path.userId}}"}"""),
        )
        val validation = validate(route)

        assertTrue(validation.canSave)
        assertTrue(
            validation.warnings.contains(RouteValidation.Warning.UnknownPathPlaceholder("userId")),
        )
    }

    @Test
    fun `an uncompilable pattern blocks saving`() {
        val validation = validate(Fixtures.route(pathPattern = "/files/**/x"))
        assertFalse(validation.canSave)
        assertTrue(validation.errors.any { it is DomainError.InvalidPathPattern })
    }

    @Test
    fun `a status outside 100-599 blocks saving`() {
        assertFalse(validate(Fixtures.route(response = MockResponse(statusCode = 99))).canSave)
        assertFalse(validate(Fixtures.route(response = MockResponse(statusCode = 600))).canSave)
        assertTrue(validate(Fixtures.route(response = MockResponse(statusCode = 204))).canSave)
    }

    @Test
    fun `a blank name blocks saving`() {
        val route = Fixtures.route().copy(name = "  ")
        assertEquals(
            listOf(DomainError.Validation(DomainError.Validation.Field.ROUTE_NAME)),
            validate(route).errors,
        )
    }

    @Test
    fun `a matcher with a blank name or unparseable JSON blocks saving`() {
        assertFalse(
            validate(
                Fixtures.route(matchers = listOf(RequestMatcher.Header("", StringOp.EQUALS, "x"))),
            ).canSave,
        )
        assertFalse(
            validate(Fixtures.route(matchers = listOf(RequestMatcher.BodySubset("{oops")))).canSave,
        )
    }

    @Test
    fun `EXISTS and ABSENT matchers do not need a value`() {
        val route = Fixtures.route(
            matchers = listOf(
                RequestMatcher.Header("X-A", StringOp.EXISTS),
                RequestMatcher.Query("b", StringOp.ABSENT),
            ),
        )
        assertTrue(validate(route).canSave)
    }

    @Test
    fun `a reserved response header warns without blocking`() {
        val route = Fixtures.route(
            response = MockResponse(headers = mapOf("Content-Length" to "10"), body = "{}"),
        )
        val validation = validate(route)

        assertTrue(validation.canSave)
        assertTrue(validation.warnings.contains(RouteValidation.Warning.ReservedHeader("Content-Length")))
    }

    @Test
    fun `a long delay warns without blocking`() {
        val route = Fixtures.route(response = MockResponse(body = "{}", delayMillisOverride = 45_000))
        val validation = validate(route)

        assertTrue(validation.canSave)
        assertTrue(validation.warnings.contains(RouteValidation.Warning.LongDelay(45_000)))
    }
}
