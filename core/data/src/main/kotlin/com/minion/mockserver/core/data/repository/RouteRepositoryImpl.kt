package com.minion.mockserver.core.data.repository

import com.minion.mockserver.core.common.error.safeCall
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.data.local.dao.RouteDao
import com.minion.mockserver.core.data.mapper.Mappers.toDomain
import com.minion.mockserver.core.data.mapper.Mappers.toEntity
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed routes (SPEC §11.1). */
@Singleton
internal class RouteRepositoryImpl @Inject constructor(
    private val dao: RouteDao,
) : RouteRepository {

    override fun observeAllRoutes(): Flow<List<MockRoute>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeRoutes(collectionId: String): Flow<List<MockRoute>> =
        dao.observeByCollection(collectionId).map { entities -> entities.map { it.toDomain() } }

    override fun observeRoute(id: String): Flow<MockRoute?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun get(id: String): AppResult<MockRoute> = safeCall {
        dao.getById(id)?.toDomain() ?: throw NoSuchElementException(id)
    }.recoverNotFound()

    override suspend fun save(route: MockRoute): AppResult<Unit> = safeCall {
        dao.upsert(route.toEntity())
    }

    override suspend fun saveAll(routes: List<MockRoute>): AppResult<Unit> = safeCall {
        dao.upsertAll(routes.map { it.toEntity() })
    }

    override suspend fun delete(id: String): AppResult<Unit> = safeCall { dao.deleteById(id) }

    override suspend fun setEnabled(id: String, isEnabled: Boolean): AppResult<Unit> =
        safeCall { dao.setEnabled(id, isEnabled) }
}
