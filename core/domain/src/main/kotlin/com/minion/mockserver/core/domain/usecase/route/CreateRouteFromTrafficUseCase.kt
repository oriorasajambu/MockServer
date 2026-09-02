package com.minion.mockserver.core.domain.usecase.route

import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.PathNormaliser
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a request that missed into a draft route (SPEC §4.5).
 *
 * This is the deliberate replacement for traffic recording: point a client at the phone, let it
 * fail, and build the mocks from the failures. Recording other apps' traffic would need a
 * VPNService or a proxy, which is a disproportionate amount of machinery — and permission — for
 * what this achieves.
 *
 * Nothing is saved. The result pre-fills the editor, and the user decides.
 */
@Singleton
class CreateRouteFromTrafficUseCase @Inject constructor(
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {

    /** A draft route, plus what the UI needs to explain how it was derived. */
    data class Draft(
        val route: MockRoute,

        /** The collection it will belong to, or `null` when a new one has to be created first. */
        val collection: MockCollection?,

        /** Name to suggest for a new collection when [collection] is `null`. */
        val suggestedCollectionName: String,

        /**
         * Segments that look like identifiers, by index into the path.
         *
         * Offered as `{param}` with a one-tap toggle rather than applied silently: `/users/42`
         * might be a list of one user or the user with id 42, and only the author knows which.
         */
        val parameterisableSegments: Map<Int, String>,
    )

    /**
     * Builds a draft from [entry].
     *
     * The target collection is the one whose base path is the longest prefix of the request path,
     * so a request to `/api/v1/orders` lands in the existing `/api/v1` collection rather than
     * creating a third one that overlaps it.
     */
    operator fun invoke(entry: TrafficEntry, collections: List<MockCollection>): Draft {
        val segments = PathNormaliser.segments(entry.path)
        val collection = collections
            .filter { it.isEnabled }
            .filter { candidate ->
                val base = PathNormaliser.segments(candidate.basePath)
                base.size <= segments.size && segments.take(base.size) == base
            }
            .maxByOrNull { PathNormaliser.segments(it.basePath).size }

        val baseSize = collection?.let { PathNormaliser.segments(it.basePath).size } ?: 0
        val relativeSegments = segments.drop(baseSize)

        val route = MockRoute(
            id = idGenerator.newId(),
            collectionId = collection?.id.orEmpty(),
            name = "${entry.method} /${relativeSegments.joinToString(separator = "/")}",
            method = HttpMethodFilter.fromWire(entry.method),
            pathPattern = if (relativeSegments.isEmpty()) {
                PathNormaliser.ROOT
            } else {
                relativeSegments.joinToString(separator = "/", prefix = "/")
            },
            response = MockResponse(
                statusCode = SUGGESTED_STATUS,
                headers = contentTypeFrom(entry),
                body = EMPTY_JSON_BODY,
            ),
            createdAt = timeProvider.nowMillis(),
        )

        return Draft(
            route = route,
            collection = collection,
            suggestedCollectionName = suggestCollectionName(segments),
            parameterisableSegments = relativeSegments
                .withIndex()
                .filter { (_, segment) -> looksLikeIdentifier(segment) }
                .associate { (index, segment) -> index to suggestParamName(relativeSegments, index, segment) },
        )
    }

    /**
     * Whether a segment looks like an id rather than a resource name.
     *
     * Numeric or UUID-shaped only. A broader heuristic — anything with a digit in it, say —
     * would parameterise `/v1` and `/oauth2`, which is worse than offering nothing.
     */
    private fun looksLikeIdentifier(segment: String): Boolean =
        segment.isNotEmpty() && (segment.all { it.isDigit() } || UUID_SHAPE.matches(segment))

    /**
     * Names a capture after the collection it belongs to: `/users/42` becomes `/users/{userId}`.
     *
     * Falling back to `{id}` when there is no preceding segment to name it after.
     */
    private fun suggestParamName(segments: List<String>, index: Int, segment: String): String {
        val owner = segments.getOrNull(index - 1) ?: return DEFAULT_PARAM
        val singular = owner.removeSuffix("s").takeIf { it.isNotEmpty() } ?: return DEFAULT_PARAM
        return singular.replaceFirstChar { it.lowercase() } + DEFAULT_PARAM.replaceFirstChar { it.uppercase() }
    }

    private fun suggestCollectionName(segments: List<String>): String =
        segments.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Imported"

    /** Carries the request's `Content-Type` across, since a mock usually answers in kind. */
    private fun contentTypeFrom(entry: TrafficEntry): Map<String, String> {
        val contentType = entry.requestHeaders.entries
            .firstOrNull { it.key.equals(MockResponse.CONTENT_TYPE, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
        return contentType?.let { mapOf(MockResponse.CONTENT_TYPE to it) }.orEmpty()
    }

    private companion object {
        const val SUGGESTED_STATUS = 200
        const val EMPTY_JSON_BODY = "{}"
        const val DEFAULT_PARAM = "id"
        val UUID_SHAPE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
