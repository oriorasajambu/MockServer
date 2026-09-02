package com.minion.mockserver.core.domain.model

import com.minion.mockserver.core.common.config.AppConstants
import com.minion.mockserver.core.domain.exception.MockServerException

/**
 * Everything the request path needs, compiled, immutable, and swapped atomically (SPEC §5.5).
 *
 * Editing a route while the server is running never restarts the engine — a restart would drop
 * every keep-alive socket and break clients mid-test. Instead, saving builds a *new* snapshot off
 * the main thread and swaps one `AtomicReference`. Each incoming request reads the reference once
 * at entry and resolves entirely against that value, so an in-flight request finishes under the
 * rules it started with and the next one sees the new rules. No locks in the request path, and no
 * torn reads.
 *
 * Everything here is pre-computed. Patterns are parsed and scores summed at build time, because
 * the alternative is doing it again for every candidate on every request.
 */
data class RoutingSnapshot(
    /** Enabled routes in enabled collections, compiled and ordered by descending static score. */
    val routes: List<CompiledRoute> = emptyList(),

    /** Enabled collections with their base paths compiled, for CORS preflight resolution. */
    val collections: List<CompiledCollection> = emptyList(),

    /** Applies to every route without a [MockRoute.chaosOverride]. `null` means no chaos. */
    val globalChaos: ChaosProfile? = null,

    /** What an unmatched request is answered with (SPEC §4.5). */
    val unmatchedResponse: MockResponse = MockResponse.DEFAULT_UNMATCHED,

    /** Pause mode: refuse everything with a 503 while still logging it. */
    val pause: PauseSpec = PauseSpec.OFF,

    /**
     * Patterns that failed to compile when this snapshot was built.
     *
     * Kept rather than thrown so one broken route cannot take the whole server down with it. The
     * routes list simply excludes them and the UI flags them.
     */
    val invalidPatterns: List<InvalidPattern> = emptyList(),
) {

    /** A route that could not be compiled, and why. */
    data class InvalidPattern(val routeId: String, val pattern: String, val problem: String)

    /**
     * The collection whose base path is the longest prefix of [segments], or `null`.
     *
     * Used for CORS preflights, which have to be answered before any route is resolved — a
     * preflight is an `OPTIONS` to a path whose real handler may well be a `POST`.
     */
    fun collectionFor(segments: List<String>): MockCollection? = collections
        .filter { it.matchesPrefixOf(segments) }
        .maxByOrNull { it.baseSegments.size }
        ?.collection

    companion object {

        /** The snapshot the engine holds before anything has been loaded. Answers everything 404. */
        val EMPTY = RoutingSnapshot()

        /**
         * Compiles [collections] and [routes] into a snapshot.
         *
         * Disabled collections and disabled routes are dropped here rather than filtered per
         * request, and routes are pre-sorted by descending static score so the common case —
         * one clear winner — is found near the front of the list.
         */
        fun build(
            collections: List<MockCollection>,
            routes: List<MockRoute>,
            globalChaos: ChaosProfile? = null,
            unmatchedResponse: MockResponse = MockResponse.DEFAULT_UNMATCHED,
            pause: PauseSpec = PauseSpec.OFF,
        ): RoutingSnapshot {
            val enabledCollections = collections.filter { it.isEnabled }
            val byId = enabledCollections.associateBy { it.id }
            val compiledRoutes = ArrayList<CompiledRoute>(routes.size)
            val invalid = ArrayList<InvalidPattern>()

            for (route in routes) {
                if (!route.isEnabled) continue
                val collection = byId[route.collectionId] ?: continue
                try {
                    compiledRoutes += CompiledRoute.of(route, collection)
                } catch (e: MockServerException.InvalidPathPattern) {
                    invalid += InvalidPattern(route.id, e.pattern, e.problem.name)
                }
            }

            return RoutingSnapshot(
                routes = compiledRoutes.sortedByDescending { it.staticScore },
                collections = enabledCollections.mapNotNull(CompiledCollection::orNull),
                globalChaos = globalChaos?.takeIf { it.isActive },
                unmatchedResponse = unmatchedResponse,
                pause = pause,
                invalidPatterns = invalid,
            )
        }
    }
}

/**
 * A route paired with its collection and its compiled pattern.
 *
 * A route alone cannot be matched: its pattern is relative to a base path and its tie-break needs
 * the collection's order. Pairing them once at build time is what keeps the request path free of
 * map lookups.
 */
data class CompiledRoute(
    val route: MockRoute,
    val collection: MockCollection,

    /** `collection.basePath` joined to `route.pathPattern`, compiled. */
    val pattern: PathPattern,

    /** Path points + method points + matcher points. Independent of any particular request. */
    val staticScore: Int,
) {

    /** Builds the comparable score for this route once it is known to be a candidate. */
    fun scoreFor(): MatchScore = MatchScore(
        total = staticScore,
        matcherCount = route.matchers.size,
        segmentCount = pattern.segmentCount,
        collectionOrder = collection.order,
        createdAt = route.createdAt,
        routeId = route.id,
    )

    companion object {

        /**
         * Compiles [route] against [collection].
         *
         * Base-path segments count towards the score. A collection mounted at `/api/v1` genuinely
         * constrains two more segments than one mounted at the root, and scoring only the route's
         * own suffix would let a root-level `/{id}` outrank `/api/v1/users/{id}`.
         *
         * @throws MockServerException.InvalidPathPattern if the joined pattern will not compile.
         */
        fun of(route: MockRoute, collection: MockCollection): CompiledRoute {
            val pattern = PathPattern.parseJoined(collection.basePath, route.pathPattern)
            val methodScore =
                if (route.method.isExplicit) AppConstants.Matching.SCORE_EXPLICIT_METHOD else 0
            val matcherScore = route.matchers.sumOf { it.score }
            return CompiledRoute(
                route = route,
                collection = collection,
                pattern = pattern,
                staticScore = pattern.score + methodScore + matcherScore,
            )
        }
    }
}

/** A collection with its base path pre-split, for prefix matching on preflights. */
data class CompiledCollection(
    val collection: MockCollection,
    val baseSegments: List<String>,
) {

    /** Whether this collection's base path is a prefix of [segments]. */
    fun matchesPrefixOf(segments: List<String>): Boolean {
        if (baseSegments.size > segments.size) return false
        for (index in baseSegments.indices) {
            if (baseSegments[index] != segments[index]) return false
        }
        return true
    }

    companion object {

        /** Compiles [collection], or `null` if its base path is unusable. */
        fun orNull(collection: MockCollection): CompiledCollection? = runCatching {
            CompiledCollection(collection, PathNormaliser.segments(collection.basePath))
        }.getOrNull()
    }
}

/**
 * Pause mode: refuse every request without stopping the server (SPEC §4.1 step 1).
 *
 * Distinct from stopping, because stopping closes the socket and a client then sees a connection
 * error rather than a status it can handle. Pause is how you make a client's retry-and-backoff
 * path run without touching the client.
 */
data class PauseSpec(
    val isEnabled: Boolean = false,

    /** Status returned while paused. */
    val statusCode: Int = DEFAULT_STATUS,

    /** Value of the `Retry-After` header on a paused response. */
    val retryAfterSeconds: Int = DEFAULT_RETRY_AFTER_SECONDS,

    /** Body returned while paused. */
    val body: String = DEFAULT_BODY,
) {
    companion object {

        /** Service Unavailable — the status a client is most likely to already retry on. */
        const val DEFAULT_STATUS = 503

        /** Short enough that a client retries within one debugging attention span. */
        const val DEFAULT_RETRY_AFTER_SECONDS = 5

        /** Says which of the two 503s this is: paused, not chaos. */
        const val DEFAULT_BODY = """{"error":"Mock server is paused"}"""

        /** Not paused. */
        val OFF = PauseSpec()
    }
}
