package com.minion.mockserver.core.domain.model

import com.minion.mockserver.core.common.config.AppConstants

/**
 * Everything persisted in DataStore (SPEC §11.2).
 *
 * One object rather than a dozen keys read separately, so a snapshot rebuild reads a consistent
 * set: the alternative is a rebuild that picks up the new chaos profile and the old pause state
 * because two flows emitted a frame apart.
 */
data class ServerSettings(
    /** The port the user asked for. */
    val port: Int = AppConstants.Server.DEFAULT_PORT,

    /** The port the last successful start actually bound, offered as "remember this?" (SPEC §8.4). */
    val lastBoundPort: Int? = null,

    val bindAddress: String = AppConstants.Server.DEFAULT_BIND_ADDRESS,

    val apiKeyEnabled: Boolean = false,
    val apiKeyHeaderName: String = AppConstants.Server.DEFAULT_API_KEY_HEADER,
    val apiKeyValue: String = "",

    /** `null` means no global chaos. A route override still applies. */
    val globalChaosProfileId: String? = null,

    /**
     * Seed for the chaos RNG (SPEC §6.3).
     *
     * Exposed and regenerable so "reproduce that flaky failure" is a thing you can actually do,
     * and so the chaos unit tests are deterministic rather than probabilistic.
     */
    val chaosSeed: Long = DEFAULT_CHAOS_SEED,

    /** Traffic ring-buffer size. */
    val logCapacity: Int = AppConstants.Traffic.DEFAULT_LOG_CAPACITY,

    /** What an unmatched request is answered with (SPEC §4.5). */
    val unmatchedResponse: MockResponse = MockResponse.DEFAULT_UNMATCHED,

    /** Pause mode and the response it returns. */
    val pause: PauseSpec = PauseSpec.OFF,

    /**
     * Set while the server is running and cleared on a clean stop.
     *
     * Still true on next launch means the process was killed — battery optimisation, or the
     * system reclaiming memory — which is what triggers the exemption card (SPEC §8.2). There is
     * no other way to detect it: a killed process gets no callback.
     */
    val wasRunning: Boolean = false,

    /** Epoch millis the battery-optimisation card was last dismissed, or `null`. */
    val batteryPromptDismissedAt: Long? = null,
) {

    /** The API key as the guard wants it, or `null` when the guard is off. */
    fun toApiKeyConfig(): ApiKeyConfig? =
        if (apiKeyEnabled && apiKeyValue.isNotBlank()) {
            ApiKeyConfig(headerName = apiKeyHeaderName, value = apiKeyValue)
        } else {
            null
        }

    /** The socket configuration these settings imply. */
    fun toServerConfig(): ServerConfig = ServerConfig(
        port = port,
        bindAddress = bindAddress,
        apiKey = toApiKeyConfig(),
    )

    companion object {

        /** A fixed default so a fresh install is reproducible before anyone touches the seed. */
        const val DEFAULT_CHAOS_SEED = 1L
    }
}
