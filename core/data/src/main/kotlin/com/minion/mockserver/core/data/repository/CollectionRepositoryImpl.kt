package com.minion.mockserver.core.data.repository

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.error.safeCall
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.data.local.dao.CollectionDao
import com.minion.mockserver.core.data.mapper.Mappers.toDomain
import com.minion.mockserver.core.data.mapper.Mappers.toEntity
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.repository.CollectionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed collections (SPEC §11.1). */
@Singleton
internal class CollectionRepositoryImpl @Inject constructor(
    private val dao: CollectionDao,
) : CollectionRepository {

    override fun observeCollections(): Flow<List<MockCollection>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCollection(id: String): Flow<MockCollection?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: String): AppResult<MockCollection> = safeCall {
        dao.getById(id)?.toDomain() ?: throw NoSuchElementException(id)
    }.recoverNotFound()

    override suspend fun save(collection: MockCollection): AppResult<Unit> = safeCall {
        dao.upsert(collection.toEntity())
    }

    override suspend fun delete(id: String): AppResult<Unit> = safeCall { dao.deleteById(id) }

    override suspend fun setEnabled(id: String, isEnabled: Boolean): AppResult<Unit> =
        safeCall { dao.setEnabled(id, isEnabled) }

    override suspend fun reorder(idsInOrder: List<String>): AppResult<Unit> =
        safeCall { dao.reorder(idsInOrder) }
}

/**
 * Turns the "row is gone" case into [DomainError.NotFound].
 *
 * `safeCall` maps an unrecognised throwable to `Unknown`, which would show the user "something
 * went wrong" for the one failure that has a precise and actionable meaning: the thing they were
 * looking at has been deleted.
 */
internal fun <T> AppResult<T>.recoverNotFound(): AppResult<T> =
    if (this is AppResult.Failure && error == DomainError.Unknown) {
        AppResult.Failure(DomainError.NotFound)
    } else {
        this
    }
