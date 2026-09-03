package com.minion.mockserver.core.domain.usecase.route

import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.repository.RouteRepository
import javax.inject.Inject

/**
 * Moves one route up or down within its collection.
 *
 * **This does not change which route answers.** Matching is decided by specificity (SPEC §4.3),
 * not by position, so the order is purely how the list reads — a way to put the four routes you
 * are working on above the twenty you are not. Anyone expecting first-match-wins would be misled
 * by a list they can arrange, which is why the screen says so where the reordering happens.
 *
 * Rewrites the whole collection's order rather than swapping two rows. A list persisted from
 * imports and hand edits can hold duplicate or sparse order values, and swapping inside that
 * preserves the mess; assigning 0..n-1 over the sorted list makes every later move well defined.
 */
class ReorderRoutesUseCase @Inject constructor(
    private val repository: RouteRepository,
) {

    /**
     * @param routeIdsInOrder every route in the collection, in the order they should appear.
     *   Partial lists are rejected rather than half-applied: a caller that dropped a route would
     *   otherwise leave it at whatever index it happened to hold.
     */
    suspend operator fun invoke(routeIdsInOrder: List<String>): AppResult<Unit> {
        if (routeIdsInOrder.isEmpty()) return AppResult.Success(Unit)
        return repository.reorder(routeIdsInOrder)
    }

    /**
     * Moves [routeId] one place toward the start or end, and returns the resulting order.
     *
     * Returns the input unchanged when the route is already at the end it is moving toward, so a
     * caller can apply the result without checking bounds and a button at the edge of the list
     * simply does nothing rather than wrapping around.
     */
    fun moved(routeIdsInOrder: List<String>, routeId: String, up: Boolean): List<String> {
        val index = routeIdsInOrder.indexOf(routeId)
        if (index < 0) return routeIdsInOrder
        val target = if (up) index - 1 else index + 1
        if (target !in routeIdsInOrder.indices) return routeIdsInOrder
        return routeIdsInOrder.toMutableList().apply {
            val moving = removeAt(index)
            add(target, moving)
        }
    }
}
