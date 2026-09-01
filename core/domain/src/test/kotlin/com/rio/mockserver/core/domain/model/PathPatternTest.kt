package com.rio.mockserver.core.domain.model

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.domain.exception.MockServerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** SPEC §4.2 pattern grammar: literals, `{name}` captures and the tail wildcard. */
class PathPatternTest {

    @Test
    fun `literal segments match exactly and case-sensitively`() {
        val pattern = PathPattern.parse("/api/Users")

        assertEquals(emptyMap<String, String>(), pattern.match(segments("/api/Users")))
        assertNull(pattern.match(segments("/api/users")))
        assertNull(pattern.match(segments("/api")))
        assertNull(pattern.match(segments("/api/Users/1")))
    }

    @Test
    fun `param segments capture exactly one segment`() {
        val pattern = PathPattern.parse("/users/{id}/posts/{postId}")

        assertEquals(
            mapOf("id" to "42", "postId" to "7"),
            pattern.match(segments("/users/42/posts/7")),
        )
        assertNull("a param cannot span two segments", pattern.match(segments("/users/42/x/posts/7")))
        assertNull("a param cannot match nothing", pattern.match(segments("/users//posts/7")))
    }

    @Test
    fun `tail wildcard matches one or more remaining segments`() {
        val pattern = PathPattern.parse("/files/**")

        assertEquals(emptyMap<String, String>(), pattern.match(segments("/files/a")))
        assertEquals(emptyMap<String, String>(), pattern.match(segments("/files/a/b/c")))
        assertNull("the tail requires at least one segment", pattern.match(segments("/files")))
    }

    @Test
    fun `a tail after captures still exposes them`() {
        val pattern = PathPattern.parse("/users/{id}/files/**")
        assertEquals(mapOf("id" to "9"), pattern.match(segments("/users/9/files/a/b")))
    }

    @Test
    fun `root pattern matches only root`() {
        val pattern = PathPattern.parse("/")
        assertEquals(emptyMap<String, String>(), pattern.match(segments("/")))
        assertNull(pattern.match(segments("/anything")))
    }

    @Test
    fun `scores segments per SPEC 4-3`() {
        // 10 (literal) + 4 (param) = 14; the tail is worth 1 however much it swallows.
        assertEquals(14, PathPattern.parse("/users/{id}").score)
        assertEquals(11, PathPattern.parse("/files/**").score)
        assertEquals(30, PathPattern.parse("/api/v1/users").score)
        assertEquals(0, PathPattern.parse("/").score)
    }

    @Test
    fun `rejects a tail that is not last`() {
        assertRejects("/files/**/thumbnails", DomainError.InvalidPathPattern.Problem.TAIL_NOT_LAST)
    }

    @Test
    fun `rejects an unnamed capture`() {
        assertRejects("/users/{}", DomainError.InvalidPathPattern.Problem.UNNAMED_PARAM)
        assertRejects("/users/{ }", DomainError.InvalidPathPattern.Problem.UNNAMED_PARAM)
    }

    @Test
    fun `rejects an unbalanced brace`() {
        assertRejects("/users/{id", DomainError.InvalidPathPattern.Problem.UNBALANCED_BRACES)
        assertRejects("/users/id}", DomainError.InvalidPathPattern.Problem.UNBALANCED_BRACES)
    }

    @Test
    fun `rejects two captures with the same name`() {
        // `{{path.id}}` would be ambiguous, so this is a mistake worth refusing at compile time
        // rather than resolving arbitrarily at request time.
        assertRejects("/users/{id}/posts/{id}", DomainError.InvalidPathPattern.Problem.DUPLICATE_PARAM)
    }

    @Test
    fun `parseOrNull returns null instead of throwing`() {
        assertNull(PathPattern.parseOrNull("/files/**/x"))
        assertTrue(PathPattern.parseOrNull("/files/**") != null)
    }

    @Test
    fun `parseJoined normalises the base path and the route pattern together`() {
        val pattern = PathPattern.parseJoined("/api/v1/", "users/{id}")
        assertEquals("/api/v1/users/{id}", pattern.raw)
        assertEquals(mapOf("id" to "3"), pattern.match(segments("/api/v1/users/3")))
    }

    private fun segments(path: String) = PathNormaliser.segments(path)

    private fun assertRejects(pattern: String, problem: DomainError.InvalidPathPattern.Problem) {
        try {
            PathPattern.parse(pattern)
            fail("expected '$pattern' to be rejected")
        } catch (e: MockServerException.InvalidPathPattern) {
            assertEquals(pattern, problem, e.problem)
        }
    }
}
