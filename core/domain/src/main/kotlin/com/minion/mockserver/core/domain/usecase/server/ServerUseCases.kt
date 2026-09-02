package com.minion.mockserver.core.domain.usecase.server

import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.BoundAddress
import com.minion.mockserver.core.domain.model.NetworkAddress
import com.minion.mockserver.core.domain.model.ServerStatus
import com.minion.mockserver.core.domain.repository.MockServerRepository
import com.minion.mockserver.core.domain.repository.NetworkAddressRepository
import com.minion.mockserver.core.domain.repository.ServerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Binds the socket, then records what actually happened (SPEC §8.3, §8.4).
 *
 * Two writes follow a successful start, and both matter:
 * - `lastBoundPort`, so the UI can offer "port 8080 was in use — remember 8081?".
 * - `wasRunning`, which is the *only* way to detect being killed. A process the system reclaims
 *   gets no callback, so the flag being still set on next launch is the signal that triggers the
 *   battery-optimisation card (SPEC §8.2).
 */
class StartServerUseCase @Inject constructor(
    private val serverRepository: MockServerRepository,
    private val settingsRepository: ServerSettingsRepository,
) {

    suspend operator fun invoke(): AppResult<BoundAddress> {
        val settings = settingsRepository.get()
        return when (val result = serverRepository.start(settings.toServerConfig())) {
            is AppResult.Success -> {
                settingsRepository.update {
                    it.copy(lastBoundPort = result.data.port, wasRunning = true)
                }
                result
            }

            is AppResult.Failure -> result
        }
    }
}

/**
 * Stops the socket and clears the was-running flag.
 *
 * Clearing the flag is what distinguishes a clean stop from being killed, so it happens only
 * here — never in `onDestroy`, which a killed process does not reach.
 */
class StopServerUseCase @Inject constructor(
    private val serverRepository: MockServerRepository,
    private val settingsRepository: ServerSettingsRepository,
) {

    suspend operator fun invoke(): AppResult<Unit> {
        val result = serverRepository.stop()
        settingsRepository.update { it.copy(wasRunning = false) }
        return result
    }
}

/** The engine's lifecycle, for whichever screens are watching. */
class ObserveServerStatusUseCase @Inject constructor(
    private val serverRepository: MockServerRepository,
) {
    operator fun invoke(): StateFlow<ServerStatus> = serverRepository.status
}

/**
 * The two counters the server screen shows, since the current run started.
 *
 * Exposed as a use case rather than letting the screen reach for [MockServerRepository], which is
 * the rule SPEC §2.2 states: a ViewModel sees `Start`, `Stop` and the observers, never the engine
 * or the repository that fronts it.
 */
class ObserveServerCountersUseCase @Inject constructor(
    private val serverRepository: MockServerRepository,
) {

    /** Requests served. */
    fun requests(): StateFlow<Long> = serverRepository.requestCount

    /** Of those, the ones that matched no route. */
    fun unmatched(): StateFlow<Long> = serverRepository.unmatchedCount
}

/**
 * The addresses a client could use, best first (SPEC §8.5).
 *
 * Re-emits on every connectivity change, because the IP changes the instant Wi-Fi flips or a
 * hotspot comes up — and a stale address on this screen costs someone ten minutes of typing it
 * into a laptop and wondering why nothing connects.
 */
class ObserveServerAddressesUseCase @Inject constructor(
    private val addressRepository: NetworkAddressRepository,
) {
    operator fun invoke(): Flow<List<NetworkAddress>> = addressRepository.observeAddresses()
}

/**
 * Turns pause mode on or off (SPEC §4.1 step 1).
 *
 * A settings write rather than an engine call, so it flows back through the snapshot rebuild and
 * takes effect on the next request without touching the socket. Pausing is not stopping: the
 * socket stays open and clients get a 503 they can handle, which is the point — it is how you
 * exercise a client's retry path without touching the client.
 */
class SetPauseModeUseCase @Inject constructor(
    private val settingsRepository: ServerSettingsRepository,
) {
    suspend operator fun invoke(isPaused: Boolean): AppResult<Unit> =
        settingsRepository.update { it.copy(pause = it.pause.copy(isEnabled = isPaused)) }
}
