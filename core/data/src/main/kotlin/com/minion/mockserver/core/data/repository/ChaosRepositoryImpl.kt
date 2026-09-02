package com.minion.mockserver.core.data.repository

import com.minion.mockserver.core.common.error.safeCall
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.data.local.dao.ChaosProfileDao
import com.minion.mockserver.core.data.mapper.Mappers.toDomain
import com.minion.mockserver.core.data.mapper.Mappers.toEntity
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.repository.ChaosRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed chaos profiles.
 *
 * The built-in "None" profile is prepended rather than stored. Keeping it out of the table means
 * it cannot be edited into something that is not "none", cannot be deleted, and does not have to
 * be seeded on first launch — three failure modes removed by not writing a row.
 */
@Singleton
internal class ChaosRepositoryImpl @Inject constructor(
    private val dao: ChaosProfileDao,
) : ChaosRepository {

    override fun observeProfiles(): Flow<List<ChaosProfile>> =
        dao.observeAll().map { entities -> listOf(ChaosProfile.NONE) + entities.map { it.toDomain() } }

    override suspend fun get(id: String): AppResult<ChaosProfile> = safeCall {
        if (id == ChaosProfile.NONE_ID) {
            ChaosProfile.NONE
        } else {
            dao.getById(id)?.toDomain() ?: throw NoSuchElementException(id)
        }
    }.recoverNotFound()

    override suspend fun save(profile: ChaosProfile): AppResult<Unit> = safeCall {
        dao.upsert(profile.toEntity())
    }

    override suspend fun delete(id: String): AppResult<Unit> = safeCall { dao.deleteById(id) }
}
