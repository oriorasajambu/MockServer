package com.minion.mockserver.core.data.repository

import com.minion.mockserver.core.common.error.safeCall
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.data.preferences.ServerSettingsDataStore
import com.minion.mockserver.core.domain.model.ServerSettings
import com.minion.mockserver.core.domain.repository.ServerSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore-backed settings (SPEC §11.2). */
@Singleton
internal class ServerSettingsRepositoryImpl @Inject constructor(
    private val dataStore: ServerSettingsDataStore,
) : ServerSettingsRepository {

    override fun observeSettings(): Flow<ServerSettings> = dataStore.settings

    override suspend fun get(): ServerSettings = dataStore.current()

    override suspend fun update(transform: (ServerSettings) -> ServerSettings): AppResult<Unit> =
        safeCall { dataStore.update(transform) }
}
