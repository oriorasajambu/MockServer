package com.minion.mockserver.core.domain.usecase.route

import com.minion.mockserver.core.domain.model.CompiledRoute
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.PathPattern
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds routes that can never win, whatever the request (SPEC §4.3).
 *
 * A route is shadowed when another route matches *every* request it could match and always
 * outranks it. That is the failure mode nobody notices: the rule is right there in the list,
 * looks correct, and never fires — and the time it takes to work out why is entirely wasted.
 *
 * ### What "covers" means
 *
 * Shadowing is decided structurally, not by sampling requests. Route B shadows route A when:
 * - B's method filter accepts everything A's does,
 * - B's pattern matches every path A's does,
 * - B has no matchers (any matcher makes B conditional, so it cannot be guaranteed to win), and
 * - B outranks A on the full tie-break chain.
 *
 * Deliberately conservative: it reports only shadowing that is certain. A false "Shadowed" chip
 * on a rule that does fire would be worse than a missing one, because it teaches people to
 * ignore the chip.
 */
@Singleton
class DetectShadowedRoutesUseCase @Inject constructor() {

    /** Route ids that can never win, mapped to the id of the route that always beats them. */
    operator fun invoke(snapshot: RoutingSnapshot): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (candidate in snapshot.routes) {
            val shadower = snapshot.routes.firstOrNull { other ->
                other.route.id != candidate.route.id && shadows(other, candidate)
            }
            if (shadower != null) result[candidate.route.id] = shadower.route.id
        }
        return result
    }

    private fun shadows(shadower: CompiledRoute, candidate: CompiledRoute): Boolean =
        // An unconditional route is the only kind that is guaranteed to match; one with matchers
        // might fail them for the very request the candidate was written for.
        shadower.route.matchers.isEmpty() &&
            methodCovers(shadower.route.method, candidate.route.method) &&
            patternCovers(shadower.pattern, candidate.pattern) &&
            shadower.scoreFor() > candidate.scoreFor()

    private fun methodCovers(shadower: HttpMethodFilter, candidate: HttpMethodFilter): Boolean =
        shadower == HttpMethodFilter.ANY || shadower == candidate

    /**
     * Whether every path [candidate] matches, [shadower] also matches.
     *
     * Segment by segment: a literal covers only the identical literal, a `{param}` covers any
     * single segment, and a `**` tail covers everything from that position on. The reverse — a
     * literal being covered by a param — is what makes `/users/{id}` shadow `/users/42` only when
     * it also outranks it, which it does not, so the pairing is correctly *not* reported.
     */
    private fun patternCovers(shadower: PathPattern, candidate: PathPattern): Boolean {
        var index = 0
        while (index < shadower.segments.size) {
            when (val segment = shadower.segments[index]) {
                PathPattern.Segment.Tail ->
                    // The tail needs at least one remaining segment on the candidate side too.
                    return candidate.segments.size > index

                is PathPattern.Segment.Param ->
                    // A param covers one segment of anything, except a tail, which is one *or
                    // more* — so a param cannot promise to cover it.
                    if (index >= candidate.segments.size ||
                        candidate.segments[index] == PathPattern.Segment.Tail
                    ) {
                        return false
                    }

                is PathPattern.Segment.Literal ->
                    if (index >= candidate.segments.size ||
                        candidate.segments[index] != segment
                    ) {
                        return false
                    }
            }
            index++
        }
        return index == candidate.segments.size
    }
}
