package com.rio.mockserver.feature.settings

import androidx.lifecycle.viewModelScope
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.ServerSettings
import com.rio.mockserver.core.domain.repository.MockServerRepository
import com.rio.mockserver.core.domain.usecase.chaos.GetChaosProfilesUseCase
import com.rio.mockserver.core.domain.usecase.chaos.ResetChaosSeedUseCase
import com.rio.mockserver.core.domain.usecase.chaos.SetGlobalChaosProfileUseCase
import com.rio.mockserver.core.domain.usecase.importexport.ExportCollectionJsonUseCase
import com.rio.mockserver.core.domain.usecase.settings.GenerateApiKeyUseCase
import com.rio.mockserver.core.domain.usecase.settings.GetServerSettingsUseCase
import com.rio.mockserver.core.domain.usecase.settings.UpdateServerSettingsUseCase
import com.rio.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The Settings tab. */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val getSettings: GetServerSettingsUseCase,
    private val updateSettings: UpdateServerSettingsUseCase,
    private val getChaosProfiles: GetChaosProfilesUseCase,
    private val setGlobalChaosProfile: SetGlobalChaosProfileUseCase,
    private val resetChaosSeed: ResetChaosSeedUseCase,
    private val generateApiKey: GenerateApiKeyUseCase,
    private val exportAll: ExportCollectionJsonUseCase,
    private val serverRepository: MockServerRepository,
    private val serviceController: SettingsServiceController,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    /** What the socket was opened with, so a change can be compared against it, not against itself. */
    private var runningSettings: ServerSettings? = null

    init {
        viewModelScope.launch {
            combine(
                getSettings(),
                getChaosProfiles(),
                serverRepository.status,
            ) { settings, profiles, status ->
                if (status.isRunning && runningSettings == null) runningSettings = settings
                if (!status.isRunning) runningSettings = null
                Triple(settings, profiles, status.isRunning)
            }.collect { (settings, profiles, isRunning) ->
                reduce {
                    copy(
                        settings = settings,
                        // Only overwritten while the field is not being edited, so typing "80" on
                        // the way to "8080" does not get reverted mid-keystroke.
                        portInput = portInput.takeIf { it.isNotEmpty() } ?: settings.port.toString(),
                        logCapacityInput = logCapacityInput.takeIf { it.isNotEmpty() }
                            ?: settings.logCapacity.toString(),
                        chaosProfiles = profiles,
                        isServerRunning = isRunning,
                        restartRequired = staleFields(settings),
                    )
                }
            }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdatePort -> {
                reduce { copy(portInput = intent.port, error = null) }
                intent.port.toIntOrNull()?.let { port -> persist { it.copy(port = port) } }
            }

            is SettingsIntent.UpdateLogCapacity -> {
                reduce { copy(logCapacityInput = intent.capacity, error = null) }
                intent.capacity.toIntOrNull()?.let { capacity ->
                    persist { it.copy(logCapacity = capacity) }
                }
            }

            is SettingsIntent.SetApiKeyEnabled -> persist { settings ->
                settings.copy(
                    apiKeyEnabled = intent.enabled,
                    // Generated on enable rather than left blank: an enabled guard with an empty
                    // key silently accepts every request, which is the worst of both.
                    apiKeyValue = settings.apiKeyValue.ifBlank { generateApiKey() },
                )
            }

            is SettingsIntent.UpdateApiKeyHeader ->
                persist { it.copy(apiKeyHeaderName = intent.header) }

            SettingsIntent.GenerateApiKey -> persist { it.copy(apiKeyValue = generateApiKey()) }

            is SettingsIntent.SelectChaosProfile -> viewModelScope.launch {
                setGlobalChaosProfile(intent.profileId)
            }

            SettingsIntent.RegenerateChaosSeed -> viewModelScope.launch {
                when (val result = resetChaosSeed()) {
                    is AppResult.Success -> emitEffect(
                        SettingsEffect.ShowMessage(result.data.toString()),
                    )

                    is AppResult.Failure -> emitEffect(SettingsEffect.ShowError(result.error))
                }
            }

            is SettingsIntent.UpdateUnmatchedBody -> persist { settings ->
                settings.copy(unmatchedResponse = settings.unmatchedResponse.copy(body = intent.body))
            }

            is SettingsIntent.UpdateUnmatchedStatus -> persist { settings ->
                settings.copy(
                    unmatchedResponse = settings.unmatchedResponse.copy(statusCode = intent.status),
                )
            }

            SettingsIntent.RestartServer -> restart()
            SettingsIntent.OpenImport -> emit(SettingsEffect.NavigateToImport)
            SettingsIntent.OpenChaosEditor -> emit(SettingsEffect.NavigateToChaosEditor)
            SettingsIntent.OpenBatterySettings -> emit(SettingsEffect.OpenBatterySettings)

            SettingsIntent.ExportAll -> export { json ->
                SettingsEffect.ShareText(json, EXPORT_FILE_NAME)
            }

            SettingsIntent.SaveExportToFile -> export { json ->
                SettingsEffect.SaveToFile(EXPORT_FILE_NAME, json)
            }
        }
    }

    /**
     * Serialises everything, then hands it to whichever destination [destination] names.
     *
     * The export itself is identical whether it is shared or written to a file, so only the last
     * step differs — duplicating the read would invite the two paths to drift.
     */
    private fun export(destination: (String) -> SettingsEffect) {
        viewModelScope.launch {
            when (val result = exportAll.all()) {
                is AppResult.Success -> emitEffect(destination(result.data))
                is AppResult.Failure -> emitEffect(SettingsEffect.ShowError(result.error))
            }
        }
    }

    /**
     * Restarts the socket, explicitly and on request.
     *
     * Never automatic. A restart drops every keep-alive connection, so a client mid-test sees a
     * connection error it did not cause — which is exactly the class of confusion this app exists
     * to remove.
     */
    private fun restart() {
        viewModelScope.launch {
            serviceController.stop()
            serviceController.start()
            runningSettings = currentState.settings
            reduce { copy(restartRequired = emptySet()) }
        }
    }

    private fun persist(transform: (ServerSettings) -> ServerSettings) {
        viewModelScope.launch {
            when (val result = updateSettings(transform)) {
                is AppResult.Failure -> reduce { copy(error = result.error) }
                is AppResult.Success -> reduce { copy(error = null) }
            }
        }
    }

    private fun staleFields(current: ServerSettings): Set<SettingsState.RestartField> {
        val running = runningSettings ?: return emptySet()
        return buildSet {
            if (running.port != current.port) add(SettingsState.RestartField.PORT)
            if (running.bindAddress != current.bindAddress) {
                add(SettingsState.RestartField.BIND_ADDRESS)
            }
            if (running.apiKeyEnabled != current.apiKeyEnabled ||
                running.apiKeyValue != current.apiKeyValue ||
                running.apiKeyHeaderName != current.apiKeyHeaderName
            ) {
                add(SettingsState.RestartField.API_KEY)
            }
        }
    }

    private fun emit(effect: SettingsEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }

    private companion object {
        const val EXPORT_FILE_NAME = "mock-server-export.json"
    }
}
