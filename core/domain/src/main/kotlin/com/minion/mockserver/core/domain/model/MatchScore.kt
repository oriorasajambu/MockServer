package com.minion.mockserver.core.domain.model

/**
 * A candidate route's specificity, plus every tie-break, as one comparable value (SPEC §4.3).
 *
 * The whole chain lives in [compareTo] rather than being spread across a sort comparator, so
 * there is exactly one definition of "which route wins" and the resolver is a `maxOfOrNull`.
 *
 * Ordering is *greater is better*, so the winner is the maximum. Two fields are therefore
 * inverted: a lower `collection.order` and an older `createdAt` are better, so they are compared
 * backwards.
 *
 * The chain terminates in [routeId], which is unique — so the ordering is total and resolution
 * can never be nondeterministic. Without that last step, two routes saved in the same millisecond
 * would resolve by whatever order the database happened to return them in, and the same request
 * could get different answers on different runs.
 */
data class MatchScore(
    /** The summed points: path segments, method, matchers. */
    val total: Int,

    /** Tie-break 1: more constraints wins. */
    val matcherCount: Int,

    /** Tie-break 2: a longer path wins. */
    val segmentCount: Int,

    /** Tie-break 3: a lower collection order wins. */
    val collectionOrder: Int,

    /** Tie-break 4: the older route wins, so a new duplicate never steals live traffic. */
    val createdAt: Long,

    /** Tie-break 5: lexicographically lower id wins. Unique, so the ordering is total. */
    val routeId: String,
) : Comparable<MatchScore> {

    override fun compareTo(other: MatchScore): Int {
        total.compareTo(other.total).let { if (it != 0) return it }
        matcherCount.compareTo(other.matcherCount).let { if (it != 0) return it }
        segmentCount.compareTo(other.segmentCount).let { if (it != 0) return it }
        // Inverted: lower order is higher priority.
        other.collectionOrder.compareTo(collectionOrder).let { if (it != 0) return it }
        // Inverted: older wins.
        other.createdAt.compareTo(createdAt).let { if (it != 0) return it }
        // Inverted: lexicographically lower id wins.
        return other.routeId.compareTo(routeId)
    }
}
