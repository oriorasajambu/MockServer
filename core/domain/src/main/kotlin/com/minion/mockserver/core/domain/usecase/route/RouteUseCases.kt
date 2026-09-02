package com.minion.mockserver.core.domain.usecase.route

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.PathNormaliser
import com.minion.mockserver.core.domain.repository.RouteRepository
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The routes in one collection, for the collection detail list. */
class GetRoutesUseCase @Inject constructor(
    private val repository: RouteRepository,
) {
    operator fun invoke(collectionId: String): Flow<List<MockRoute>> =
        repository.observeRoutes(collectionId)

    /** One route, for the editor. Emits `null` once it is deleted. */
    fun single(id: String): Flow<MockRoute?> = repository.observeRoute(id)

    /**
     * Every route in every collection.
     *
     * For the callers that work across collections rather than inside one — the traffic log's
     * collection filter, which has to turn a collection into the set of route ids that answered
     * for it (SPEC §7.3).
     */
    fun all(): Flow<List<MockRoute>> = repository.observeAllRoutes()
}

/**
 * Validates and saves a route.
 *
 * Validation runs again here even though the editor already ran it, because Save is reachable
 * from places the editor is not — an import commit, a duplicate, a "create rule from traffic".
 * A route that cannot compile must not reach the snapshot builder from any of them.
 */
class SaveRouteUseCase @Inject constructor(
    private val repository: RouteRepository,
    private val validateRoute: ValidateRouteUseCase,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(route: MockRoute): AppResult<MockRoute> {
        val validation = validateRoute(route)
        validation.errors.firstOrNull()?.let { return AppResult.Failure(it) }

        val prepared = route.copy(
            id = route.id.ifBlank { idGenerator.newId() },
            name = route.name.trim(),
            pathPattern = PathNormaliser.normalise(route.pathPattern),
            createdAt = if (route.createdAt > 0L) route.createdAt else timeProvider.nowMillis(),
            // Saving a body is what clears the import flag. Doing it here rather than in the
            // editor means an import commit that fills bodies in bulk clears them too.
            needsBody = route.needsBody && route.response.body.isBlank(),
        )

        return when (val result = repository.save(prepared)) {
            is AppResult.Success -> AppResult.Success(prepared)
            is AppResult.Failure -> result
        }
    }
}

/** Deletes one route. */
class DeleteRouteUseCase @Inject constructor(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.delete(id)
}

/**
 * Copies a route within its collection.
 *
 * The copy gets a new id and *now* as its creation time, so the original keeps the tie-break: a
 * duplicate must never silently steal traffic from the rule already in use (SPEC §4.3).
 */
class DuplicateRouteUseCase @Inject constructor(
    private val repository: RouteRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(id: String): AppResult<MockRoute> {
        val original = when (val result = repository.get(id)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }

        val copy = original.copy(
            id = idGenerator.newId(),
            name = "${original.name} (copy)",
            createdAt = timeProvider.nowMillis(),
        )

        return when (val result = repository.save(copy)) {
            is AppResult.Success -> AppResult.Success(copy)
            is AppResult.Failure -> result
        }
    }
}

/** Enables or disables one route without deleting it. */
class ToggleRouteUseCase @Inject constructor(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(id: String, isEnabled: Boolean): AppResult<Unit> =
        repository.setEnabled(id, isEnabled)
}

/** Saves many routes as one transaction. What an import commit uses (SPEC §9.1). */
class SaveRoutesUseCase @Inject constructor(
    private val repository: RouteRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(routes: List<MockRoute>): AppResult<Unit> {
        if (routes.isEmpty()) return AppResult.Success(Unit)
        val now = timeProvider.nowMillis()
        val prepared = routes.mapIndexed { index, route ->
            route.copy(
                id = route.id.ifBlank { idGenerator.newId() },
                pathPattern = PathNormaliser.normalise(route.pathPattern),
                // Staggered by index so an import's routes keep their document order in the
                // createdAt tie-break instead of all colliding on one millisecond.
                createdAt = if (route.createdAt > 0L) route.createdAt else now + index,
            )
        }
        return repository.saveAll(prepared)
    }
}

/** Reads one route, failing with [DomainError.NotFound] rather than returning null. */
class GetRouteUseCase @Inject constructor(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(id: String): AppResult<MockRoute> = repository.get(id)
}
