package com.minion.mockserver.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.minion.mockserver.core.common.config.AppConstants
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.PauseSpec
import com.minion.mockserver.core.domain.model.ServerSettings
import com.minion.mockserver.core.domain.util.JsonTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server settings in a Preferences DataStore (SPEC §11.2).
 *
 * The two composite values — the unmatched response and the pause spec — are stored as JSON in a
 * single key each rather than spread over four primitive keys. Spreading them would make a
 * partial write representable: a status updated and a body not, observed by a snapshot rebuild in
 * between.
 */
@Singleton
class ServerSettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Re-emits on every write. What the snapshot builder and the settings screen both read. */
    val settings: Flow<ServerSettings> = dataStore.data.map { it.toSettings() }

    /** A single read, for the paths that cannot wait on a flow — service start, for one. */
    suspend fun current(): ServerSettings = settings.first()

    /**
     * Applies [transform] atomically.
     *
     * `edit` gives read-modify-write in one operation. Reading and then writing through two calls
     * would lose an update whenever two screens change different fields at once — which is exactly
     * what happens when the notification's Pause action fires while the settings screen is open.
     */
    suspend fun update(transform: (ServerSettings) -> ServerSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toSettings())
            preferences[Keys.PORT] = updated.port
            updated.lastBoundPort?.let { preferences[Keys.LAST_BOUND_PORT] = it }
                ?: preferences.remove(Keys.LAST_BOUND_PORT)
            preferences[Keys.BIND_ADDRESS] = updated.bindAddress
            preferences[Keys.API_KEY_ENABLED] = updated.apiKeyEnabled
            preferences[Keys.API_KEY_HEADER] = updated.apiKeyHeaderName
            preferences[Keys.API_KEY_VALUE] = updated.apiKeyValue
            updated.globalChaosProfileId?.let { preferences[Keys.GLOBAL_CHAOS_ID] = it }
                ?: preferences.remove(Keys.GLOBAL_CHAOS_ID)
            preferences[Keys.CHAOS_SEED] = updated.chaosSeed
            preferences[Keys.LOG_CAPACITY] = updated.logCapacity
            preferences[Keys.UNMATCHED_RESPONSE] =
                JsonTree.Permissive.encodeToString(MockResponse.serializer(), updated.unmatchedResponse)
            preferences[Keys.PAUSE] =
                JsonTree.Permissive.encodeToString(PAUSE_SERIALIZER, updated.pause.toStored())
            preferences[Keys.WAS_RUNNING] = updated.wasRunning
            updated.batteryPromptDismissedAt?.let { preferences[Keys.BATTERY_PROMPT_DISMISSED] = it }
                ?: preferences.remove(Keys.BATTERY_PROMPT_DISMISSED)
        }
    }

    private fun Preferences.toSettings(): ServerSettings = ServerSettings(
        port = this[Keys.PORT] ?: AppConstants.Server.DEFAULT_PORT,
        lastBoundPort = this[Keys.LAST_BOUND_PORT],
        bindAddress = this[Keys.BIND_ADDRESS] ?: AppConstants.Server.DEFAULT_BIND_ADDRESS,
        apiKeyEnabled = this[Keys.API_KEY_ENABLED] ?: false,
        apiKeyHeaderName = this[Keys.API_KEY_HEADER] ?: AppConstants.Server.DEFAULT_API_KEY_HEADER,
        apiKeyValue = this[Keys.API_KEY_VALUE].orEmpty(),
        globalChaosProfileId = this[Keys.GLOBAL_CHAOS_ID],
        chaosSeed = this[Keys.CHAOS_SEED] ?: ServerSettings.DEFAULT_CHAOS_SEED,
        logCapacity = this[Keys.LOG_CAPACITY] ?: AppConstants.Traffic.DEFAULT_LOG_CAPACITY,
        unmatchedResponse = this[Keys.UNMATCHED_RESPONSE]
            ?.let { decodeUnmatched(it) }
            ?: MockResponse.DEFAULT_UNMATCHED,
        pause = this[Keys.PAUSE]?.let { decodePause(it) } ?: PauseSpec.OFF,
        wasRunning = this[Keys.WAS_RUNNING] ?: false,
        batteryPromptDismissedAt = this[Keys.BATTERY_PROMPT_DISMISSED],
    )

    /** A malformed value falls back to the default rather than making settings unreadable. */
    private fun decodeUnmatched(json: String): MockResponse = try {
        JsonTree.Permissive.decodeFromString(MockResponse.serializer(), json)
    } catch (_: Exception) {
        MockResponse.DEFAULT_UNMATCHED
    }

    private fun decodePause(json: String): PauseSpec = try {
        JsonTree.Permissive.decodeFromString(PAUSE_SERIALIZER, json).toDomain()
    } catch (_: Exception) {
        PauseSpec.OFF
    }

    /**
     * The stored shape of [PauseSpec].
     *
     * A mirror rather than making the domain type `@Serializable`: [PauseSpec] is runtime state
     * the request path reads, and adding a serialization annotation to it would invite it into an
     * export file, where a paused server is not a thing anyone would want to import.
     */
    @kotlinx.serialization.Serializable
    private data class StoredPause(
        val isEnabled: Boolean = false,
        val statusCode: Int = PauseSpec.DEFAULT_STATUS,
        val retryAfterSeconds: Int = PauseSpec.DEFAULT_RETRY_AFTER_SECONDS,
        val body: String = PauseSpec.DEFAULT_BODY,
    ) {
        fun toDomain() = PauseSpec(isEnabled, statusCode, retryAfterSeconds, body)
    }

    private fun PauseSpec.toStored() = StoredPause(isEnabled, statusCode, retryAfterSeconds, body)


    /** DataStore keys. Names are stable: renaming one silently resets that setting. */
    private object Keys {
        val PORT = intPreferencesKey("port")
        val LAST_BOUND_PORT = intPreferencesKey("last_bound_port")
        val BIND_ADDRESS = stringPreferencesKey("bind_address")
        val API_KEY_ENABLED = booleanPreferencesKey("api_key_enabled")
        val API_KEY_HEADER = stringPreferencesKey("api_key_header")
        val API_KEY_VALUE = stringPreferencesKey("api_key_value")
        val GLOBAL_CHAOS_ID = stringPreferencesKey("global_chaos_profile_id")
        val CHAOS_SEED = longPreferencesKey("chaos_seed")
        val LOG_CAPACITY = intPreferencesKey("log_capacity")
        val UNMATCHED_RESPONSE = stringPreferencesKey("unmatched_response")
        val PAUSE = stringPreferencesKey("pause")
        val WAS_RUNNING = booleanPreferencesKey("was_running")
        val BATTERY_PROMPT_DISMISSED = longPreferencesKey("battery_prompt_dismissed_at")
    }

    companion object {
        /** The DataStore file name. */
        const val FILE_NAME = "server_settings"

        private val PAUSE_SERIALIZER = StoredPause.serializer()
    }
}
