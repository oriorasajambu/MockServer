package com.minion.mockserver.core.domain.model

import com.minion.mockserver.core.common.config.AppConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An extra condition a request must satisfy for a route to be a candidate (SPEC §4.1 step 5).
 *
 * Matchers are AND-ed: one failure eliminates the candidate. There is no OR in v1 — two rules
 * that differ only in a header are two routes, which is both easier to read in a list and easier
 * to reason about when one of them shadows the other.
 *
 * A sealed interface so v2 can add `BodyJsonPath` and `BodyRegex` without touching the scorer
 * beyond a point value (SPEC §15).
 *
 * Each case carries an explicit `@SerialName`. Without one the discriminator in an exported file
 * would be the fully-qualified class name, so moving this file to another package would silently
 * stop every already-exported collection from importing.
 */
@Serializable
sealed interface RequestMatcher {

    /** The points this matcher contributes to a route's specificity (SPEC §4.3). */
    val score: Int

    /** Constrains a request header. Name lookup is case-insensitive throughout (SPEC §13). */
    @Serializable
    @SerialName("header")
    data class Header(
        val name: String,
        val op: StringOp,
        val value: String = "",
    ) : RequestMatcher {
        override val score: Int
            get() = AppConstants.Matching.SCORE_HEADER_MATCHER
    }

    /**
     * Constrains a query parameter.
     *
     * A repeated parameter (`?id=1&id=2`) is held as a list, and [StringOp.EQUALS] passes if *any*
     * value matches — the alternative would make `?id=1&id=2` fail a rule written for `id=1`,
     * which is not what anyone means by it (SPEC §13).
     */
    @Serializable
    @SerialName("query")
    data class Query(
        val name: String,
        val op: StringOp,
        val value: String = "",
    ) : RequestMatcher {
        override val score: Int
            get() = AppConstants.Matching.SCORE_QUERY_MATCHER
    }

    /**
     * Asserts that [json] is a deep subset of the request body (SPEC §4.4).
     *
     * Subset, not equality, so a rule can be authored by pasting the one fragment that matters
     * out of a real payload. Worth the most points of any matcher because it is the most specific
     * thing a route can say about a request.
     */
    @Serializable
    @SerialName("bodySubset")
    data class BodySubset(
        val json: String,
    ) : RequestMatcher {
        override val score: Int
            get() = AppConstants.Matching.SCORE_BODY_MATCHER
    }
}

/**
 * How a [RequestMatcher.Header] or [RequestMatcher.Query] compares.
 *
 * [EXISTS] and [ABSENT] ignore the matcher's value, which is why it defaults to empty rather than
 * being a separate type per operator.
 */
@Serializable
enum class StringOp {
    /** Exact match, case-sensitive on the value. */
    EQUALS,

    /** Substring, case-sensitive. */
    CONTAINS,

    /** Present with any value, including empty. */
    EXISTS,

    /** Not present at all. */
    ABSENT,
}
