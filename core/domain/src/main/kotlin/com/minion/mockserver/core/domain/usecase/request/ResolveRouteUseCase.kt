package com.minion.mockserver.core.domain.usecase.request

import com.minion.mockserver.core.domain.model.CompiledRoute
import com.minion.mockserver.core.domain.model.MatchScore
import com.minion.mockserver.core.domain.model.MockRequest
import com.minion.mockserver.core.domain.model.PathNormaliser
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the one route that answers a request, or `null` (SPEC §4).
 *
 * The pipeline, in order:
 * 1. **Candidate filter** — the method filter accepts the request's method, and the compiled
 *    pattern structurally matches the normalised path.
 * 2. **Matcher evaluation** — every matcher must pass. One failure eliminates the candidate.
 * 3. **Scoring** — the highest [MatchScore] wins, with the full tie-break chain behind it.
 *
 * Pure, allocation-light, and holding no state, so a table-driven test can put a hundred cases
 * through it in milliseconds — which is the point of keeping it out of the Ktor layer.
 */
@Singleton
class ResolveRouteUseCase @Inject constructor(
    private val matchScorer: MatchScorer,
) {

    /** A route that matched, with the captures its pattern produced. */
    data class Match(
        val compiled: CompiledRoute,
        val score: MatchScore,

        /** `{name}` captures, exposed to the response as `{{path.name}}`. */
        val pathCaptures: Map<String, String>,
    )

    /** The winning route for [request] under [snapshot], or `null` if nothing matched. */
    operator fun invoke(request: MockRequest, snapshot: RoutingSnapshot): Match? {
        val segments = PathNormaliser.segments(request.path)
        var best: Match? = null

        for (compiled in snapshot.routes) {
            // The snapshot is sorted by descending static score, and MatchScore compares `total`
            // first — so once the remaining candidates all score strictly lower than the current
            // best, none of them can win and the tie-breaks cannot change that. On a snapshot
            // with a clear winner this stops after a handful of routes instead of scanning all.
            val current = best
            if (current != null && compiled.staticScore < current.score.total) break

            if (!compiled.route.method.accepts(request.method)) continue
            val captures = compiled.pattern.match(segments) ?: continue
            if (!matchScorer.allPass(compiled.route.matchers, request)) continue

            val score = compiled.scoreFor()
            if (current == null || score > current.score) {
                best = Match(compiled = compiled, score = score, pathCaptures = captures)
            }
        }

        return best
    }

    /**
     * Every route that *could* match [request], best first.
     *
     * Not used on the request path. The route editor's "why did this not win?" explanation and
     * the shadowing detector both need the full candidate list, and duplicating the filter in
     * two places is how the explanation ends up disagreeing with the behaviour.
     */
    fun candidates(request: MockRequest, snapshot: RoutingSnapshot): List<Match> {
        val segments = PathNormaliser.segments(request.path)
        return snapshot.routes.mapNotNull { compiled ->
            if (!compiled.route.method.accepts(request.method)) return@mapNotNull null
            val captures = compiled.pattern.match(segments) ?: return@mapNotNull null
            if (!matchScorer.allPass(compiled.route.matchers, request)) return@mapNotNull null
            Match(compiled = compiled, score = compiled.scoreFor(), pathCaptures = captures)
        }.sortedByDescending { it.score }
    }
}
