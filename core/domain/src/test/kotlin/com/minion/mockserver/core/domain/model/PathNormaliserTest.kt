package com.minion.mockserver.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SPEC §4.2 normalisation, and the §13 edge cases that depend on it.
 *
 * Table-driven, because the value of these rules is entirely in the corners — a single-case test
 * for each would be six times the code and cover less.
 */
class PathNormaliserTest {

    @Test
    fun `normalises paths per SPEC 4-2`() {
        val cases = listOf(
            "empty becomes root" to ("" to "/"),
            "root stays root" to ("/" to "/"),
            "trailing slash is stripped" to ("/users/" to "/users"),
            "many trailing slashes are stripped" to ("/users///" to "/users"),
            "duplicate slashes collapse" to ("//users" to "/users"),
            "interior duplicate slashes collapse" to ("/api//v1///users" to "/api/v1/users"),
            "missing leading slash is added" to ("users/1" to "/users/1"),
            "query string is dropped" to ("/users?page=2" to "/users"),
            "fragment is dropped" to ("/users#top" to "/users"),
            "query and trailing slash together" to ("/users/?a=1&b=2" to "/users"),
            "percent-encoded space decodes" to ("/users/john%20doe" to "/users/john doe"),
            "percent-encoded slash stays inside its segment" to ("/files/a%2Fb" to "/files/a/b"),
            "multi-byte utf8 escape decodes to one character" to ("/tags/%E2%9C%93" to "/tags/✓"),
            "plus is literal, not a space" to ("/a+b" to "/a+b"),
            "malformed escape is left alone" to ("/100%25/x" to "/100%/x"),
            "lone percent is left alone" to ("/a%" to "/a%"),
            "unicode segment survives" to ("/café" to "/café"),
        )

        cases.forEach { (label, io) ->
            val (input, expected) = io
            assertEquals(label, expected, PathNormaliser.normalise(input))
        }
    }

    @Test
    fun `percent-decoded slash does not create a new segment`() {
        // The distinction the split-then-decode order exists for: `%2F` is a slash *in* a
        // segment, not a segment boundary the client never sent.
        assertEquals(listOf("files", "a/b"), PathNormaliser.segments("/files/a%2Fb"))
        assertEquals(listOf("files", "a", "b"), PathNormaliser.segments("/files/a/b"))
    }

    @Test
    fun `joins a base path to a route pattern regardless of slashes`() {
        val expected = "/api/v1/users"
        listOf(
            "/api/v1" to "/users",
            "/api/v1/" to "users",
            "api/v1" to "/users",
            "//api//v1//" to "//users//",
        ).forEach { (base, route) ->
            assertEquals("$base + $route", expected, PathNormaliser.join(base, route))
        }
    }

    @Test
    fun `fromUrl keeps only the path component`() {
        // Import formats record full URLs; the scheme and host name a server this app replaces,
        // and carrying them into a pattern produces a route nothing can address.
        val cases = listOf(
            "https://api.example.com/config?v=2" to "/config",
            "http://10.0.0.5:8080/api/v1/users/" to "/api/v1/users",
            "https://api.example.com" to "/",
            "https://api.example.com/" to "/",
            "/already/a/path" to "/already/a/path",
            "/invoices/:id" to "/invoices/:id",
        )
        cases.forEach { (input, expected) ->
            assertEquals(input, expected, PathNormaliser.fromUrl(input))
        }
    }

    @Test
    fun `an empty base path mounts routes at the root`() {
        assertEquals("/users", PathNormaliser.join("/", "/users"))
        assertEquals("/", PathNormaliser.join("/", "/"))
    }
}
