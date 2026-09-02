package com.minion.mockserver.feature.server

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.mvi.UiEffect
import com.minion.mockserver.core.common.mvi.UiIntent
import com.minion.mockserver.core.common.mvi.UiState
import com.minion.mockserver.core.domain.model.NetworkAddress
import com.minion.mockserver.core.domain.model.ServerStatus

/**
 * The Server tab's contract (SPEC §10.3).
 *
 * The address block is the hero: whichever interface ranked first, large and copyable, with the
 * rest behind an expander and `localhost` always on its own row for on-device callers.
 */
internal data class ServerState(
    val status: ServerStatus = ServerStatus.Stopped,

    /** Ranked best-first. Empty means no usable interface — localhost only (SPEC §8.5). */
    val addresses: List<NetworkAddress> = emptyList(),

    /** Whether the "N more addresses" expander is open. */
    val isAddressListExpanded: Boolean = false,

    /** The port the user asked for, which may differ from the one bound (SPEC §8.4). */
    val requestedPort: Int = 0,

    val requestCount: Long = 0,
    val unmatchedCount: Long = 0,

    /** Milliseconds since the current run started. Recomputed on a ticker while running. */
    val uptimeMillis: Long = 0,

    /** Requests per minute over the current run. Zero before the first one. */
    val requestsPerMinute: Int = 0,

    /** Name of the active global chaos profile, or `null` when chaos is off. */
    val activeChaosProfileName: String? = null,

    /** Whether the API-key guard is on. Drives the permanent open-access note (SPEC §8.6). */
    val isApiKeyEnabled: Boolean = false,

    /** True while pause mode is refusing requests. */
    val isPaused: Boolean = false,

    /** The mDNS name the system actually registered, which may carry a collision suffix. */
    val advertisedName: String? = null,

    /** Shown after the app detects it was killed while running (SPEC §8.2). */
    val showBatteryOptimisationCard: Boolean = false,

    /**
     * Whether the system will show this app's notifications.
     *
     * Read from the platform on every resume rather than from the permission result, because the
     * user can revoke it in system settings while the app sits in the background, and a state
     * that only ever updates on a grant would keep claiming the notification is there.
     */
    val areNotificationsEnabled: Boolean = true,
) : UiState {

    /** The address the hero block shows, or `null` when there is no usable interface. */
    val primaryAddress: NetworkAddress? get() = addresses.firstOrNull()

    /**
     * Whether to warn that the server is running without its notification (SPEC §13).
     *
     * Only while running: with the socket closed there is nothing to protect, and a permanent
     * warning about a consequence that has not happened yet is a warning people learn to skip.
     */
    val showNotificationWarning: Boolean get() = status.isRunning && !areNotificationsEnabled

    /** The port actually bound, falling back to the requested one before the socket is open. */
    val boundPort: Int
        get() = (status as? ServerStatus.Running)?.address?.port ?: requestedPort

    /** `http://192.168.1.45:8080`, or `null` when nothing is reachable over the LAN. */
    val primaryBaseUrl: String? get() = primaryAddress?.toBaseUrl(boundPort)

    /** Always offered, because another app on this device can always reach the server. */
    val loopbackUrl: String get() = "http://localhost:$boundPort"

    /** Whether to show "port 8080 was in use — running on 8081". */
    val didFallBack: Boolean get() = (status as? ServerStatus.Running)?.didFallBack == true
}

internal sealed interface ServerIntent : UiIntent {
    data object StartServer : ServerIntent
    data object StopServer : ServerIntent
    data object TogglePause : ServerIntent
    data object ToggleAddressList : ServerIntent
    data class CopyAddress(val url: String) : ServerIntent
    data object ShowQr : ServerIntent
    data object RememberFallbackPort : ServerIntent
    data object DismissBatteryCard : ServerIntent
    data object RequestBatteryExemption : ServerIntent
    data object OpenSettings : ServerIntent
    data object OpenNotificationSettings : ServerIntent

    /** Reported by the screen on every resume, since only it can ask the platform. */
    data class NotificationsEnabledChanged(val enabled: Boolean) : ServerIntent
}

internal sealed interface ServerEffect : UiEffect {
    data class CopiedToClipboard(val label: String) : ServerEffect
    data class ShowError(val error: DomainError) : ServerEffect

    /**
     * Asked before starting, not on first launch.
     *
     * The server runs without the permission; what is lost is the notification, and with it the
     * user's ability to stop the server without opening the app (SPEC §13).
     */
    data object RequestNotificationPermission : ServerEffect

    data class NavigateToQr(val url: String) : ServerEffect
    data object NavigateToSettings : ServerEffect
    data object OpenBatterySettings : ServerEffect

    /** Opens this app's notification settings, so the warning has somewhere to lead. */
    data object OpenNotificationSettings : ServerEffect
}
