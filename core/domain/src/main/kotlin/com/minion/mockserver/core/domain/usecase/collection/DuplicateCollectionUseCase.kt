package com.minion.mockserver.core.domain.usecase.collection

import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.repository.CollectionRepository
import com.minion.mockserver.core.domain.repository.RouteRepository
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Copies a collection and everything in it.
 *
 * The reason this exists is versioning a mock set by hand: duplicate `Api`, change three routes in
 * the copy, and switch between them with the master toggles. Rebuilding eleven routes to try one
 * variation is the work this replaces.
 *
 * **Every id is regenerated.** Routes carry their collection's id, so copying them verbatim would
 * produce two collections claiming the same rows, and deleting either would take both.
 *
 * **The copy lands disabled.** Two collections answering the same base path is exactly the
 * shadowing problem the app warns about elsewhere (SPEC §4.3), so the duplicate is inert until the
 * user says which of the two should serve rather than silently competing with the original.
 */
class DuplicateCollectionUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val routeRepository: RouteRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(id: String): AppResult<MockCollection> {
        val original = when (val result = collectionRepository.get(id)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }

        val existingNames = collectionRepository.observeCollections().first()
        val routes = routeRepository.observeRoutes(id).first()

        val now = timeProvider.nowMillis()
        val copy = original.copy(
            id = idGenerator.newId(),
            name = nextName(original.name, existingNames.map { it.name }.toSet()),
            isEnabled = false,
            order = existingNames.size,
            createdAt = now,
        )

        return when (val saved = collectionRepository.save(copy)) {
            is AppResult.Failure -> saved
            is AppResult.Success -> {
                val copied = routes.map { route ->
                    route.copy(
                        id = idGenerator.newId(),
                        collectionId = copy.id,
                        createdAt = now,
                    )
                }
                when (val savedRoutes = routeRepository.saveAll(copied)) {
                    is AppResult.Failure -> {
                        // The collection row is already written, so failing here would leave an
                        // empty duplicate behind. Undo it rather than hand back a shell.
                        collectionRepository.delete(copy.id)
                        savedRoutes
                    }

                    is AppResult.Success -> AppResult.Success(copy)
                }
            }
        }
    }

    /**
     * `Api` becomes `Api copy`, then `Api copy 2`.
     *
     * Numbered only from the second copy, because "Api copy" reads as a name and "Api copy 1"
     * reads as a mistake. Names are not unique-constrained, so this is politeness rather than
     * correctness — but two identically named collections in a list of eleven is a real cost to
     * whoever has to tell them apart.
     */
    private fun nextName(original: String, taken: Set<String>): String {
        val first = "$original $COPY_SUFFIX"
        if (first !in taken) return first
        var n = 2
        while ("$first $n" in taken) n++
        return "$first $n"
    }

    private companion object {
        const val COPY_SUFFIX = "copy"
    }
}
