package com.minion.mockserver.core.domain.model

import com.minion.mockserver.core.common.config.AppConstants
import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.domain.exception.MockServerException

/**
 * A compiled path pattern: the segment list a request is matched against (SPEC §4.2).
 *
 * Compiled once when a [RoutingSnapshot] is built, never per request. Parsing on the request path
 * would put string splitting and validation between a client and its response for no benefit —
 * the patterns only change when someone saves a route.
 *
 * Grammar, in full:
 *
 * | Token | Meaning |
 * |---|---|
 * | `users` | literal segment, exact and case-sensitive |
 * | `{id}` | one segment, captured and exposed as `{{path.id}}` |
 * | `**` | one *or more* remaining segments; only legal as the last segment |
 */
class PathPattern private constructor(
    /** The normalised source, e.g. `/api/v1/users/{id}`. */
    val raw: String,
    /** The parsed segments, in order. */
    val segments: List<Segment>,
) {

    /** One element of a pattern. */
    sealed interface Segment {

        /** Matches itself, exactly. */
        data class Literal(val value: String) : Segment

        /** Matches any one non-empty segment and captures it under [name]. */
        data class Param(val name: String) : Segment

        /** Matches every remaining segment, of which there must be at least one. */
        data object Tail : Segment
    }

    /** Capture names, in the order they appear. */
    val paramNames: List<String> = segments.filterIsInstance<Segment.Param>().map { it.name }

    /** Whether the pattern ends in `**`. */
    val hasTail: Boolean = segments.lastOrNull() is Segment.Tail

    /**
     * Segments counted for the tie-break "more path segments" (SPEC §4.3).
     *
     * The tail counts as one however many segments it swallows, so a `/files` route ending in `**` never out-ranks
     * `/files/a/b` on segment count — which is the whole point of the wildcard scoring less.
     */
    val segmentCount: Int = segments.size

    /**
     * The static part of this pattern's specificity score.
     *
     * Literal 10, param 4, tail 1 — a route with more concrete segments always beats a wildcard,
     * and among equally-shaped paths the one that constrains more wins (SPEC §4.3).
     */
    val score: Int = segments.sumOf { segment ->
        when (segment) {
            is Segment.Literal -> AppConstants.Matching.SCORE_LITERAL_SEGMENT
            is Segment.Param -> AppConstants.Matching.SCORE_PARAM_SEGMENT
            Segment.Tail -> AppConstants.Matching.SCORE_TAIL
        }
    }

    /**
     * Matches [requestSegments] — already normalised and decoded by [PathNormaliser].
     *
     * Returns the captures on success (empty map for a pattern with no `{name}`), or `null` when
     * the path does not fit. `null` rather than an exception because "did not match" is the
     * overwhelmingly common outcome, not an error.
     */
    fun match(requestSegments: List<String>): Map<String, String>? {
        var captures: MutableMap<String, String>? = null
        var index = 0

        for (segment in segments) {
            when (segment) {
                // Validated to be last, so consuming the remainder here is complete.
                Segment.Tail -> return if (index <= requestSegments.lastIndex) {
                    captures ?: emptyMap()
                } else {
                    null
                }

                is Segment.Literal -> {
                    if (index > requestSegments.lastIndex || requestSegments[index] != segment.value) return null
                    index++
                }

                is Segment.Param -> {
                    if (index > requestSegments.lastIndex) return null
                    val map = captures ?: LinkedHashMap<String, String>(paramNames.size).also { captures = it }
                    map[segment.name] = requestSegments[index]
                    index++
                }
            }
        }

        return if (index == requestSegments.size) captures ?: emptyMap() else null
    }

    override fun toString(): String = raw

    override fun equals(other: Any?): Boolean = other is PathPattern && other.raw == raw

    override fun hashCode(): Int = raw.hashCode()

    companion object {

        /** The root pattern, matching only `/`. */
        val ROOT: PathPattern = PathPattern("/", emptyList())

        /**
         * Compiles [raw], normalising it first.
         *
         * @throws MockServerException.InvalidPathPattern when the pattern cannot be compiled. The
         *   editor calls [parseOrNull] instead; this throwing form is for snapshot building, where
         *   an unparseable pattern is a bug that has already been through validation.
         */
        fun parse(raw: String): PathPattern {
            val normalised = PathNormaliser.normalise(raw)
            val tokens = PathNormaliser.segments(normalised)
            val segments = ArrayList<Segment>(tokens.size)
            val seenParams = HashSet<String>()

            tokens.forEachIndexed { index, token ->
                when {
                    token == AppConstants.Matching.TAIL_WILDCARD -> {
                        if (index != tokens.lastIndex) {
                            throw invalid(raw, DomainError.InvalidPathPattern.Problem.TAIL_NOT_LAST)
                        }
                        segments += Segment.Tail
                    }

                    token.startsWith('{') && token.endsWith('}') && token.length >= 2 -> {
                        val name = token.substring(1, token.length - 1).trim()
                        if (name.isEmpty() || '{' in name || '}' in name) {
                            throw invalid(raw, DomainError.InvalidPathPattern.Problem.UNNAMED_PARAM)
                        }
                        if (!seenParams.add(name)) {
                            throw invalid(raw, DomainError.InvalidPathPattern.Problem.DUPLICATE_PARAM)
                        }
                        segments += Segment.Param(name)
                    }

                    // A brace anywhere else means the author meant a capture and mistyped it.
                    // Treating it as a literal would produce a route that silently never matches.
                    '{' in token || '}' in token ->
                        throw invalid(raw, DomainError.InvalidPathPattern.Problem.UNBALANCED_BRACES)

                    else -> segments += Segment.Literal(token)
                }
            }

            return PathPattern(normalised, segments)
        }

        /** [parse], returning `null` instead of throwing. For live validation in the editor. */
        fun parseOrNull(raw: String): PathPattern? = try {
            parse(raw)
        } catch (_: MockServerException.InvalidPathPattern) {
            null
        }

        /**
         * Compiles a collection base path joined to a route pattern.
         *
         * Joining before parsing rather than composing two patterns keeps one rule for
         * normalisation and makes the scored segment list the one the client actually addresses.
         */
        fun parseJoined(basePath: String, routePattern: String): PathPattern =
            parse(PathNormaliser.join(basePath, routePattern))

        private fun invalid(raw: String, problem: DomainError.InvalidPathPattern.Problem) =
            MockServerException.InvalidPathPattern(raw, problem)
    }
}
