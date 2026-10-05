package com.minion.mockserver.feature.settings.preferences

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.mvi.UiEffect
import com.minion.mockserver.core.common.mvi.UiIntent
import com.minion.mockserver.core.common.mvi.UiState
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.ServerSettings

/**
 * The Settings tab (SPEC §10.1).
 *
 * [restartRequired] is the one piece of state worth explaining. Editing a route hot-applies
 * (SPEC §5.5), but the port, the bind address and the API key are captured when the socket opens
 * — so changing one while the server runs leaves the UI and the socket disagreeing until a
 * restart. Naming which fields are stale, rather than restarting silently, is what keeps that
 * honest: a silent restart drops every keep-alive connection mid-test.
 */
internal data class SettingsState(
    val settings: ServerSettings = ServerSettings(),

    /** Text held separately from [settings] so a half-typed port is not a validation failure. */
    val portInput: String = "",
    val logCapacityInput: String = "",

    val chaosProfiles: List<ChaosProfile> = emptyList(),

    /** Fields whose change needs a restart to take effect. Empty when nothing is stale. */
    val restartRequired: Set<RestartField> = emptySet(),

    val isServerRunning: Boolean = false,

    /**
     * Which sections are open.
     *
     * A set rather than one id: the design opens Import and Battery together, and a screen that
     * closed one section to open another would make comparing two of them a game of tapping back
     * and forth. All start closed, so the first thing this screen shows is its own contents list.
     */
    val expandedSections: Set<Section> = emptySet(),

    val error: DomainError? = null,
) : UiState {

    /** The settings that are read once, when the socket opens. */
    enum class RestartField { PORT, BIND_ADDRESS, API_KEY }

    /** The collapsible blocks, in the order they are drawn. */
    enum class Section { SERVER, CHAOS, TRAFFIC_LOG, UNMATCHED, DATA, BATTERY, ABOUT }

    /** Whether to show the restart prompt at all. */
    val showRestartPrompt: Boolean get() = isServerRunning && restartRequired.isNotEmpty()
}

internal sealed interface SettingsIntent : UiIntent {
    data class UpdatePort(val port: String) : SettingsIntent
    data class UpdateLogCapacity(val capacity: String) : SettingsIntent
    data class SetApiKeyEnabled(val enabled: Boolean) : SettingsIntent
    data class UpdateApiKeyHeader(val header: String) : SettingsIntent
    data object GenerateApiKey : SettingsIntent
    data class SelectChaosProfile(val profileId: String?) : SettingsIntent
    data object RegenerateChaosSeed : SettingsIntent
    data class UpdateUnmatchedBody(val body: String) : SettingsIntent
    data class UpdateUnmatchedStatus(val status: Int) : SettingsIntent
    data object RestartServer : SettingsIntent
    data object OpenImport : SettingsIntent
    /** Everything, to the share sheet. */
    data object ExportAll : SettingsIntent

    /** Everything, to a file the user picks (SPEC §9.2). */
    data object SaveExportToFile : SettingsIntent
    data object OpenChaosEditor : SettingsIntent
    data class ToggleSection(val section: SettingsState.Section) : SettingsIntent
    data object OpenBatterySettings : SettingsIntent
}

internal sealed interface SettingsEffect : UiEffect {
    data class ShareText(val text: String, val subject: String) : SettingsEffect

    /**
     * Write the export to a file the user picks (SPEC §9.2).
     *
     * Separate from [ShareText] rather than a flag on it: a share hands the text to another app,
     * a save produces a file on the device. Exports are wanted both ways — one to send a
     * colleague a collection, the other to keep a backup where it can be found again.
     */
    data class SaveToFile(val fileName: String, val content: String) : SettingsEffect
    data object NavigateToImport : SettingsEffect
    data object NavigateToChaosEditor : SettingsEffect
    data object OpenBatterySettings : SettingsEffect
    data class ShowError(val error: DomainError) : SettingsEffect
    data class ShowMessage(val message: String) : SettingsEffect
}
