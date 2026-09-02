package com.rio.mockserver.core.data.repository

import com.rio.mockserver.core.common.error.safeCall
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.data.preferences.ServerSettingsDataStore
import com.rio.mockserver.core.domain.model.ServerSettings
import com.rio.mockserver.core.domain.repository.ServerSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

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
