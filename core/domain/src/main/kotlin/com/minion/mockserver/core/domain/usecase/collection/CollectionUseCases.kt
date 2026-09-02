package com.minion.mockserver.core.domain.usecase.collection

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.PathNormaliser
import com.minion.mockserver.core.domain.repository.CollectionRepository
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Every collection, for the Routes tab's top level. */
class GetCollectionsUseCase @Inject constructor(
    private val repository: CollectionRepository,
) {
    operator fun invoke(): Flow<List<MockCollection>> = repository.observeCollections()
}

/**
 * One collection, observed.
 *
 * Emits `null` once it is deleted, which is what lets a detail screen close itself instead of
 * showing the last state it happened to hold.
 */
class GetCollectionUseCase @Inject constructor(
    private val repository: CollectionRepository,
) {
    operator fun invoke(id: String): Flow<MockCollection?> = repository.observeCollection(id)
}

/**
 * Validates and saves a collection.
 *
 * The base path is normalised here rather than in the editor, so `api/v1/`, `/api/v1` and
 * `//api//v1` all become the same stored value — and two collections that look identical to a
 * user cannot end up matching differently.
 */
class SaveCollectionUseCase @Inject constructor(
    private val repository: CollectionRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(collection: MockCollection): AppResult<MockCollection> {
        if (collection.name.isBlank()) {
            return AppResult.Failure(DomainError.Validation(DomainError.Validation.Field.COLLECTION_NAME))
        }
        if (collection.cors.hasCredentialsWildcardConflict) {
            return AppResult.Failure(
                DomainError.Validation(DomainError.Validation.Field.CORS_CREDENTIALS_WILDCARD),
            )
        }

        val prepared = collection.copy(
            id = collection.id.ifBlank { idGenerator.newId() },
            name = collection.name.trim(),
            basePath = PathNormaliser.normalise(collection.basePath),
            createdAt = if (collection.createdAt > 0L) collection.createdAt else timeProvider.nowMillis(),
        )

        return when (val result = repository.save(prepared)) {
            is AppResult.Success -> AppResult.Success(prepared)
            is AppResult.Failure -> result
        }
    }
}

/** Deletes a collection and, by foreign-key cascade, every route in it. */
class DeleteCollectionUseCase @Inject constructor(
    private val repository: CollectionRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.delete(id)
}

/**
 * Enables or disables a collection.
 *
 * A disabled collection contributes no candidates at all, whatever its routes say — which is how
 * you park a whole API version without deleting it.
 */
class ToggleCollectionUseCase @Inject constructor(
    private val repository: CollectionRepository,
) {
    suspend operator fun invoke(id: String, isEnabled: Boolean): AppResult<Unit> =
        repository.setEnabled(id, isEnabled)
}
