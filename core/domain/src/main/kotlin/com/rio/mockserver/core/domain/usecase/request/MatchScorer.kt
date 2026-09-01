package com.rio.mockserver.core.domain.usecase.request

import com.rio.mockserver.core.domain.model.MockRequest
import com.rio.mockserver.core.domain.model.RequestMatcher
import com.rio.mockserver.core.domain.model.StringOp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates the non-path conditions on a candidate route (SPEC §4.1 step 5).
 *
 * Matchers are AND-ed and the first failure eliminates the candidate, so this short-circuits.
 * There is no OR in v1: two rules that differ only in a header are two routes, which reads better
 * in a list and makes shadowing visible instead of hidden inside a boolean expression.
 */
@Singleton
class MatchScorer @Inject constructor(
    private val bodySubsetMatcher: BodySubsetMatcher,
) {

    /** Whether every matcher on the route passes for [request]. */
    fun allPass(matchers: List<RequestMatcher>, request: MockRequest): Boolean =
        matchers.all { matcher -> passes(matcher, request) }

    /** Whether one matcher passes. Split out so the editor can show per-matcher results. */
    fun passes(matcher: RequestMatcher, request: MockRequest): Boolean = when (matcher) {
        is RequestMatcher.Header -> evaluate(
            op = matcher.op,
            expected = matcher.value,
            // Header *names* are case-insensitive per HTTP, so the lookup is. Header *values*
            // are not, so the comparison is not: `Accept: Application/JSON` is a different
            // request from `Accept: application/json` and a rule that conflated them would
            // silently match traffic it was not written for.
            actualValues = request.headerValues(matcher.name),
            isPresent = request.hasHeader(matcher.name),
        )

        is RequestMatcher.Query -> evaluate(
            op = matcher.op,
            expected = matcher.value,
            // A repeated parameter matches if *any* of its values does, so a rule written for
            // `?id=1` still matches `?id=1&id=2` (SPEC §13).
            actualValues = request.query[matcher.name].orEmpty(),
            isPresent = request.query.containsKey(matcher.name),
        )

        is RequestMatcher.BodySubset -> bodySubsetMatcher.matches(matcher.json, request.body)
    }

    private fun evaluate(
        op: StringOp,
        expected: String,
        actualValues: List<String>,
        isPresent: Boolean,
    ): Boolean = when (op) {
        StringOp.EQUALS -> actualValues.any { it == expected }
        StringOp.CONTAINS -> actualValues.any { it.contains(expected) }
        StringOp.EXISTS -> isPresent
        StringOp.ABSENT -> !isPresent
    }
}
