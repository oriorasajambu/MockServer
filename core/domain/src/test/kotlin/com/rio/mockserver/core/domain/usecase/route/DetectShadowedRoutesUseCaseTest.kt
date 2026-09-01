package com.rio.mockserver.core.domain.usecase.route

import com.rio.mockserver.core.domain.fixture.Fixtures
import com.rio.mockserver.core.domain.model.HttpMethodFilter
import com.rio.mockserver.core.domain.model.RequestMatcher
import com.rio.mockserver.core.domain.model.StringOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC §4.3 — the **Shadowed** chip.
 *
 * The bias throughout is towards *not* reporting: a false chip on a rule that does fire is worse
 * than a missing one, because it teaches people to ignore the chip.
 */
class DetectShadowedRoutesUseCaseTest {

    private val detect = DetectShadowedRoutesUseCase()

    @Test
    fun `an identical route with a better tie-break shadows the newer one`() {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "old", pathPattern = "/users", createdAt = 1_000L),
                Fixtures.route(id = "new", pathPattern = "/users", createdAt = 2_000L),
            ),
        )

        assertEquals(mapOf("new" to "old"), detect(snapshot))
    }

    @Test
    fun `a route with matchers never shadows, because it might not match`() {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(
                    id = "conditional",
                    pathPattern = "/users",
                    createdAt = 1_000L,
                    matchers = listOf(RequestMatcher.Header("X-A", StringOp.EXISTS)),
                ),
                Fixtures.route(id = "plain", pathPattern = "/users", createdAt = 2_000L),
            ),
        )

        assertTrue(detect(snapshot).isEmpty())
    }

    @Test
    fun `a wildcard does not shadow a more specific route`() {
        // The wildcard covers every path the literal does, but scores lower — so the literal still
        // wins and nothing is shadowed.
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "wildcard", pathPattern = "/files/**"),
                Fixtures.route(id = "literal", pathPattern = "/files/logo.png"),
            ),
        )

        assertTrue(detect(snapshot).isEmpty())
    }

    @Test
    fun `an ANY route shadows a same-path ANY route it outranks, but not a narrower method`() {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "any", method = HttpMethodFilter.ANY, pathPattern = "/x", createdAt = 1_000L),
                Fixtures.route(id = "get", method = HttpMethodFilter.GET, pathPattern = "/x", createdAt = 2_000L),
            ),
        )

        // GET scores 5 higher than ANY, so it outranks and is not shadowed.
        assertTrue(detect(snapshot).isEmpty())
    }

    @Test
    fun `a param route shadows a same-shaped param route it outranks`() {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "first", pathPattern = "/users/{id}", createdAt = 1_000L),
                Fixtures.route(id = "second", pathPattern = "/users/{userId}", createdAt = 2_000L),
            ),
        )

        assertEquals(mapOf("second" to "first"), detect(snapshot))
    }

    @Test
    fun `routes on different paths do not shadow each other`() {
        val snapshot = Fixtures.snapshot(
            listOf(
                Fixtures.route(id = "a", pathPattern = "/users"),
                Fixtures.route(id = "b", pathPattern = "/orders"),
            ),
        )

        assertTrue(detect(snapshot).isEmpty())
    }

    @Test
    fun `a route in a lower-order collection shadows an identical one in a later collection`() {
        val snapshot = Fixtures.snapshot(
            routes = listOf(
                Fixtures.route(id = "in-first", collectionId = "c1", pathPattern = "/x"),
                Fixtures.route(id = "in-second", collectionId = "c2", pathPattern = "/x"),
            ),
            collections = listOf(
                Fixtures.collection(id = "c1", order = 0),
                Fixtures.collection(id = "c2", order = 1),
            ),
        )

        assertEquals(mapOf("in-second" to "in-first"), detect(snapshot))
    }
}
