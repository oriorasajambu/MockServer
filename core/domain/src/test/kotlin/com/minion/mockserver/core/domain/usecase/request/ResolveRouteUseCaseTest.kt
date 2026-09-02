package com.minion.mockserver.core.domain.usecase.request

import com.minion.mockserver.core.domain.fixture.Fixtures
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.StringOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SPEC §4 — the whole matching pipeline.
 *
 * Table-driven wherever the cases differ only in data, because the arithmetic in §4.3 is the kind
 * of thing that is right in three cases and wrong in the fourth, and only a table makes the
 * fourth cheap enough to write.
 */
class ResolveRouteUseCaseTest {

    private val resolve = ResolveRouteUseCase(MatchScorer(BodySubsetMatcher()))

    // ---------------------------------------------------------------- precedence

    @Test
    fun `a literal segment beats a param, which beats a wildcard`() {
        val literal = Fixtures.route(id = "literal", pathPattern = "/users/me")
        val param = Fixtures.route(id = "param", pathPattern = "/users/{id}")
        val wildcard = Fixtures.route(id = "wildcard", pathPattern = "/users/**")
        val snapshot = Fixtures.snapshot(listOf(wildcard, param, literal))

        assertEquals("literal", winner(snapshot, path = "/users/me"))
        assertEquals("param", winner(snapshot, path = "/users/42"))
        // Only the wildcard can span two segments.
        assertEquals("wildcard", winner(snapshot, path = "/users/42/avatar"))
    }

    @Test
    fun `an explicit method beats ANY on the same path`() {
        val any = Fixtures.route(id = "any", method = HttpMethodFilter.ANY, pathPattern = "/users")
        val get = Fixtures.route(id = "get", method = HttpMethodFilter.GET, pathPattern = "/users")
        val snapshot = Fixtures.snapshot(listOf(any, get))

        assertEquals("get", winner(snapshot, method = "GET", path = "/users"))
        assertEquals("any", winner(snapshot, method = "POST", path = "/users"))
    }

    @Test
    fun `a method filter that does not accept the request eliminates the route`() {
        val snapshot = Fixtures.snapshot(
            listOf(Fixtures.route(method = HttpMethodFilter.POST, pathPattern = "/users")),
        )
        assertNull(resolve(Fixtures.request(method = "GET", path = "/users"), snapshot))
    }

    @Test
    fun `HEAD matches a GET route but not a POST one`() {
        // SPEC §5.3: a client probing for Content-Length should get the real headers, not a 404.
        val get = Fixtures.snapshot(
            listOf(Fixtures.route(method = HttpMethodFilter.GET, pathPattern = "/users")),
        )
        val post = Fixtures.snapshot(
            listOf(Fixtures.route(method = HttpMethodFilter.POST, pathPattern = "/users")),
        )

        assertEquals("route-1", winner(get, method = "HEAD", path = "/users"))
        assertNull(resolve(Fixtures.request(method = "HEAD", path = "/users"), post))
    }

    // ---------------------------------------------------------------- matchers

    @Test
    fun `matchers are AND-ed and one failure eliminates the candidate`() {
        val route = Fixtures.route(
            pathPattern = "/users",
            matchers = listOf(
                RequestMatcher.Header("X-Tenant", StringOp.EQUALS, "acme"),
                RequestMatcher.Query("page", StringOp.EXISTS),
            ),
        )
        val snapshot = Fixtures.snapshot(listOf(route))

        val bothPass = Fixtures.request(
            path = "/users",
            headers = mapOf("X-Tenant" to listOf("acme")),
            query = mapOf("page" to listOf("1")),
        )
        val headerOnly = Fixtures.request(
            path = "/users",
            headers = mapOf("X-Tenant" to listOf("acme")),
        )

        assertEquals("route-1", resolve(bothPass, snapshot)?.compiled?.route?.id)
        assertNull(resolve(headerOnly, snapshot))
    }

    @Test
    fun `header lookup is case-insensitive on the name and sensitive on the value`() {
        val route = Fixtures.route(
            pathPattern = "/users",
            matchers = listOf(RequestMatcher.Header("Content-Type", StringOp.EQUALS, "application/json")),
        )
        val snapshot = Fixtures.snapshot(listOf(route))

        val lowercaseName = Fixtures.request(
            path = "/users",
            headers = mapOf("content-type" to listOf("application/json")),
        )
        val differentCaseValue = Fixtures.request(
            path = "/users",
            headers = mapOf("Content-Type" to listOf("Application/JSON")),
        )

        assertEquals("route-1", resolve(lowercaseName, snapshot)?.compiled?.route?.id)
        assertNull(resolve(differentCaseValue, snapshot))
    }

    @Test
    fun `a repeated query parameter matches if any value matches`() {
        // SPEC §13: `?id=1&id=2` must still satisfy a rule written for `id=1`.
        val route = Fixtures.route(
            pathPattern = "/users",
            matchers = listOf(RequestMatcher.Query("id", StringOp.EQUALS, "2")),
        )
        val snapshot = Fixtures.snapshot(listOf(route))
        val request = Fixtures.request(path = "/users", query = mapOf("id" to listOf("1", "2")))

        assertEquals("route-1", resolve(request, snapshot)?.compiled?.route?.id)
    }

    @Test
    fun `ABSENT passes only when the header is missing`() {
        val route = Fixtures.route(
            pathPattern = "/users",
            matchers = listOf(RequestMatcher.Header("Authorization", StringOp.ABSENT)),
        )
        val snapshot = Fixtures.snapshot(listOf(route))

        assertEquals("route-1", resolve(Fixtures.request(path = "/users"), snapshot)?.compiled?.route?.id)
        assertNull(
            resolve(
                Fixtures.request(path = "/users", headers = mapOf("Authorization" to listOf(""))),
                snapshot,
            ),
        )
    }

    // ---------------------------------------------------------------- scoring

    @Test
    fun `scoring arithmetic follows the SPEC 4-3 table`() {
        val cases = listOf(
            // description, route, expected total
            Triple(
                "two literals",
                Fixtures.route(pathPattern = "/api/users"),
                20,
            ),
            Triple(
                "literal + param",
                Fixtures.route(pathPattern = "/users/{id}"),
                14,
            ),
            Triple(
                "literal + tail",
                Fixtures.route(pathPattern = "/files/**"),
                11,
            ),
            Triple(
                "literal + explicit method",
                Fixtures.route(pathPattern = "/users", method = HttpMethodFilter.GET),
                15,
            ),
            Triple(
                "literal + header matcher",
                Fixtures.route(
                    pathPattern = "/users",
                    matchers = listOf(RequestMatcher.Header("A", StringOp.EXISTS)),
                ),
                13,
            ),
            Triple(
                "literal + query matcher",
                Fixtures.route(
                    pathPattern = "/users",
                    matchers = listOf(RequestMatcher.Query("a", StringOp.EXISTS)),
                ),
                13,
            ),
            Triple(
                "literal + body matcher",
                Fixtures.route(
                    pathPattern = "/users",
                    matchers = listOf(RequestMatcher.BodySubset("{}")),
                ),
                16,
            ),
        )

        cases.forEach { (label, route, expected) ->
            val snapshot = Fixtures.snapshot(listOf(route))
            assertEquals(label, expected, snapshot.routes.single().staticScore)
        }
    }

    @Test
    fun `base path segments count towards the score`() {
        // A collection mounted at /api/v1 genuinely constrains two more segments, so a root-level
        // catch-all must not outrank it.
        val scoped = Fixtures.snapshot(
            routes = listOf(Fixtures.route(pathPattern = "/users")),
            collections = listOf(Fixtures.collection(basePath = "/api/v1")),
        )
        assertEquals(30, scoped.routes.single().staticScore)
    }

    @Test
    fun `a body matcher outranks a header matcher on the same path`() {
        val header = Fixtures.route(
            id = "header",
            pathPattern = "/orders",
            matchers = listOf(RequestMatcher.Header("X-A", StringOp.EXISTS)),
        )
        val body = Fixtures.route(
            id = "body",
            pathPattern = "/orders",
            matchers = listOf(RequestMatcher.BodySubset("""{"type":"express"}""")),
        )
        val snapshot = Fixtures.snapshot(listOf(header, body))
        val request = Fixtures.request(
            method = "POST",
            path = "/orders",
            headers = mapOf("X-A" to listOf("1")),
            body = """{"type":"express","weight":3}""",
        )

        assertEquals("body", resolve(request, snapshot)?.compiled?.route?.id)
    }

    // ---------------------------------------------------------------- tie-breaks

    @Test
    fun `tie-break 1 - more matchers wins at equal total`() {
        // Two query matchers (13 + 3 = 16) against one body matcher (16): equal totals, so the
        // matcher count decides.
        val twoMatchers = Fixtures.route(
            id = "two",
            pathPattern = "/x",
            matchers = listOf(
                RequestMatcher.Query("a", StringOp.EXISTS),
                RequestMatcher.Query("b", StringOp.EXISTS),
            ),
        )
        val oneMatcher = Fixtures.route(
            id = "one",
            pathPattern = "/x",
            matchers = listOf(RequestMatcher.BodySubset("{}")),
        )
        val snapshot = Fixtures.snapshot(listOf(oneMatcher, twoMatchers))
        val request = Fixtures.request(
            method = "POST",
            path = "/x",
            query = mapOf("a" to listOf("1"), "b" to listOf("2")),
            body = "{}",
        )

        assertEquals(16, snapshot.routes.map { it.staticScore }.distinct().single())
        assertEquals("two", resolve(request, snapshot)?.compiled?.route?.id)
    }

    @Test
    fun `tie-break 3 - lower collection order wins`() {
        val first = Fixtures.collection(id = "c-first", order = 0)
        val second = Fixtures.collection(id = "c-second", order = 5)
        val snapshot = Fixtures.snapshot(
            routes = listOf(
                Fixtures.route(id = "in-second", collectionId = "c-second", pathPattern = "/x"),
                Fixtures.route(id = "in-first", collectionId = "c-first", pathPattern = "/x"),
            ),
            collections = listOf(second, first),
        )

        assertEquals("in-first", winner(snapshot, path = "/x"))
    }

    @Test
    fun `tie-break 4 - the older route wins`() {
        // A duplicate must never silently steal traffic from the rule already in use.
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "new", pathPattern = "/x", createdAt = 2_000L),
                Fixtures.route(id = "old", pathPattern = "/x", createdAt = 1_000L),
            ),
        )
        assertEquals("old", winner(snapshot, path = "/x"))
    }

    @Test
    fun `tie-break 5 - route id makes the ordering total`() {
        // Identical in every other respect: without the id step this would resolve by whatever
        // order the database happened to return, and the same request could get different answers
        // on different runs.
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "bbb", pathPattern = "/x", createdAt = 1_000L),
                Fixtures.route(id = "aaa", pathPattern = "/x", createdAt = 1_000L),
            ),
        )
        assertEquals("aaa", winner(snapshot, path = "/x"))
        // And the reverse insertion order resolves identically.
        val reversed = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "aaa", pathPattern = "/x", createdAt = 1_000L),
                Fixtures.route(id = "bbb", pathPattern = "/x", createdAt = 1_000L),
            ),
        )
        assertEquals("aaa", winner(reversed, path = "/x"))
    }

    // ---------------------------------------------------------------- snapshot filtering

    @Test
    fun `disabled routes and disabled collections contribute no candidates`() {
        val disabledRoute = Fixtures.route(id = "off", pathPattern = "/x", isEnabled = false)
        val inDisabledCollection = Fixtures.route(id = "orphan", collectionId = "c-off", pathPattern = "/x")
        val snapshot = Fixtures.snapshot(
            routes = listOf(disabledRoute, inDisabledCollection),
            collections = listOf(Fixtures.collection(), Fixtures.collection(id = "c-off", isEnabled = false)),
        )

        assertEquals(0, snapshot.routes.size)
        assertNull(resolve(Fixtures.request(path = "/x"), snapshot))
    }

    @Test
    fun `a route with an uncompilable pattern is reported, not thrown`() {
        // One broken route must not be able to take the whole server down with it.
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "broken", pathPattern = "/files/**/x"),
                Fixtures.route(id = "fine", pathPattern = "/ok"),
            ),
        )

        assertEquals(listOf("broken"), snapshot.invalidPatterns.map { it.routeId })
        assertEquals("fine", winner(snapshot, path = "/ok"))
    }

    @Test
    fun `candidates lists every possible match, best first`() {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "wildcard", pathPattern = "/users/**"),
                Fixtures.route(id = "param", pathPattern = "/users/{id}"),
                Fixtures.route(id = "literal", pathPattern = "/users/me"),
            ),
        )

        assertEquals(
            listOf("literal", "param", "wildcard"),
            resolve.candidates(Fixtures.request(path = "/users/me"), snapshot)
                .map { it.compiled.route.id },
        )
    }

    private fun winner(
        snapshot: RoutingSnapshot,
        method: String = "GET",
        path: String,
    ): String? = resolve(Fixtures.request(method = method, path = path), snapshot)?.compiled?.route?.id
}
