package com.rio.mockserver.feature.server

import androidx.lifecycle.viewModelScope
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.ServerStatus
import com.rio.mockserver.core.domain.repository.MockServerRepository
import com.rio.mockserver.core.domain.usecase.chaos.GetChaosProfilesUseCase
import com.rio.mockserver.core.domain.usecase.server.ObserveServerAddressesUseCase
import com.rio.mockserver.core.domain.usecase.server.SetPauseModeUseCase
import com.rio.mockserver.core.domain.usecase.settings.GetServerSettingsUseCase
import com.rio.mockserver.core.domain.usecase.settings.UpdateServerSettingsUseCase
import com.rio.mockserver.core.domain.util.TimeProvider
import com.rio.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Server tab.
 *
 * Holds no business logic: starting and stopping go through use cases, and the screen's numbers
 * are derived from flows the domain already exposes. What lives here is the assembly of six
 * streams into one state and the uptime ticker, both of which are presentation concerns.
 *
 * Starting and stopping the *socket* is done by the service rather than by calling
 * `StartServerUseCase` from here: the engine must outlive this ViewModel, and a start issued from
 * `viewModelScope` would leave a bound socket with nothing keeping the process alive.
 */
@HiltViewModel
internal class ServerViewModel @Inject constructor(
    private val serverRepository: MockServerRepository,
    private val observeAddresses: ObserveServerAddressesUseCase,
    private val getSettings: GetServerSettingsUseCase,
    private val updateSettings: UpdateServerSettingsUseCase,
    private val getChaosProfiles: GetChaosProfilesUseCase,
    private val setPauseMode: SetPauseModeUseCase,
    private val timeProvider: TimeProvider,
    private val serviceController: ServerServiceController,
) : MviViewModel<ServerState, ServerIntent, ServerEffect>(ServerState()) {

    init {
        observeServer()
        startUptimeTicker()
    }

    override fun onIntent(intent: ServerIntent) {
        when (intent) {
            ServerIntent.StartServer -> start()
            ServerIntent.StopServer -> serviceController.stop()
            ServerIntent.TogglePause -> viewModelScope.launch { setPauseMode(!currentState.isPaused) }
            ServerIntent.ToggleAddressList ->
                reduce { copy(isAddressListExpanded = !isAddressListExpanded) }

            is ServerIntent.CopyAddress -> viewModelScope.launch {
                emitEffect(ServerEffect.CopiedToClipboard(intent.url))
            }

            ServerIntent.ShowQr -> viewModelScope.launch {
                val url = currentState.primaryBaseUrl ?: currentState.loopbackUrl
                emitEffect(ServerEffect.NavigateToQr(url))
            }

            ServerIntent.RememberFallbackPort -> rememberFallbackPort()
            ServerIntent.DismissBatteryCard -> dismissBatteryCard()
            ServerIntent.RequestBatteryExemption -> viewModelScope.launch {
                emitEffect(ServerEffect.OpenBatterySettings)
            }

            ServerIntent.OpenSettings -> viewModelScope.launch {
                emitEffect(ServerEffect.NavigateToSettings)
            }
        }
    }

    private fun start() {
        viewModelScope.launch {
            emitEffect(ServerEffect.RequestNotificationPermission)
            serviceController.start()
        }
    }

    private fun observeServer() {
        viewModelScope.launch {
            combine(
                serverRepository.status,
                serverRepository.requestCount,
                serverRepository.unmatchedCount,
                observeAddresses(),
                getSettings(),
            ) { status, requestCount, unmatchedCount, addresses, settings ->
                Snapshot(
                    status = status,
                    addresses = addresses,
                    requestedPort = settings.port,
                    requestCount = requestCount,
                    unmatchedCount = unmatchedCount,
                    isApiKeyEnabled = settings.apiKeyEnabled,
                    isPaused = settings.pause.isEnabled,
                    // Detected, never asked for on first launch: the flag is still set only if
                    // the process was killed while the server was running (SPEC §8.2).
                    showBatteryOptimisationCard = settings.wasRunning &&
                        !status.isRunning &&
                        settings.batteryPromptDismissedAt == null,
                )
            }.collect { snapshot ->
                reduce {
                    copy(
                        status = snapshot.status,
                        addresses = snapshot.addresses,
                        requestedPort = snapshot.requestedPort,
                        requestCount = snapshot.requestCount,
                        unmatchedCount = snapshot.unmatchedCount,
                        isApiKeyEnabled = snapshot.isApiKeyEnabled,
                        isPaused = snapshot.isPaused,
                        showBatteryOptimisationCard = snapshot.showBatteryOptimisationCard,
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(getSettings(), getChaosProfiles()) { settings, profiles ->
                profiles.firstOrNull { it.id == settings.globalChaosProfileId }
                    ?.takeIf { it.isActive }
                    ?.name
            }.collect { name -> reduce { copy(activeChaosProfileName = name) } }
        }
    }

    /**
     * Recomputes uptime and requests-per-minute once a second while running.
     *
     * Derived here rather than stored, because both are functions of the clock: putting them in
     * state at write time would leave uptime frozen whenever no request arrived.
     */
    private fun startUptimeTicker() {
        viewModelScope.launch {
            while (isActive) {
                val running = currentState.status as? ServerStatus.Running
                if (running != null) {
                    val elapsed = timeProvider.nowMillis() - running.startedAt
                    reduce {
                        copy(
                            uptimeMillis = elapsed,
                            requestsPerMinute = ratePerMinute(requestCount, elapsed),
                        )
                    }
                }
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    private fun ratePerMinute(count: Long, elapsedMillis: Long): Int =
        if (elapsedMillis < MILLIS_PER_MINUTE) {
            count.toInt()
        } else {
            (count * MILLIS_PER_MINUTE / elapsedMillis).toInt()
        }

    private fun rememberFallbackPort() {
        val bound = (currentState.status as? ServerStatus.Running)?.address?.port ?: return
        viewModelScope.launch {
            when (val result = updateSettings { it.copy(port = bound) }) {
                is AppResult.Failure -> emitEffect(ServerEffect.ShowError(result.error))
                is AppResult.Success -> Unit
            }
        }
    }

    private fun dismissBatteryCard() {
        reduce { copy(showBatteryOptimisationCard = false) }
        viewModelScope.launch {
            updateSettings { it.copy(batteryPromptDismissedAt = timeProvider.nowMillis()) }
        }
    }

    /**
     * The fields the five observed streams contribute, kept apart from the ones the user drives.
     *
     * Reducing straight from a `combine` would overwrite `isAddressListExpanded` every time a
     * request arrived, collapsing the expander under the user's finger.
     */
    private data class Snapshot(
        val status: ServerStatus,
        val addresses: List<com.rio.mockserver.core.domain.model.NetworkAddress>,
        val requestedPort: Int,
        val requestCount: Long,
        val unmatchedCount: Long,
        val isApiKeyEnabled: Boolean,
        val isPaused: Boolean,
        val showBatteryOptimisationCard: Boolean,
    )

    private companion object {
        const val TICK_INTERVAL_MILLIS = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
