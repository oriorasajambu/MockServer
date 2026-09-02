package com.rio.mockserver.feature.server

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rio.mockserver.core.designsystem.theme.AppTheme
import com.rio.mockserver.core.designsystem.theme.Dimens
import com.rio.mockserver.core.domain.model.BoundAddress
import com.rio.mockserver.core.domain.model.NetworkAddress
import com.rio.mockserver.core.domain.model.ServerStatus
import com.rio.mockserver.core.ui.component.ChaosBanner
import com.rio.mockserver.core.ui.component.OpenAccessNote
import com.rio.mockserver.core.ui.error.toMessageRes
import com.rio.mockserver.core.ui.mvi.ObserveAsEvents
import com.rio.mockserver.core.ui.util.DateUtil
import com.rio.mockserver.core.ui.util.copyToClipboard
import com.rio.mockserver.core.ui.util.showToast

/**
 * The Server tab (SPEC §10.3).
 *
 * Split into a connected composable that owns the ViewModel and the effect collector, and a
 * stateless [ServerContent] that takes `state` and `onIntent` — so the whole screen previews and
 * tests without Hilt.
 */
@Composable
internal fun ServerScreen(
    onNavigateToQr: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerViewModel = hiltViewModel(),
    onRequestNotificationPermission: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Resources, not `context.getString`: a Context read is not invalidated by a
    // configuration change, so every string resolved through it goes stale after a locale
    // switch and stays stale until the screen is recreated.
    val resources = LocalResources.current

    // Re-read on every resume, not once: the user can turn notifications off in system settings
    // while this screen sits in the background, and the warning has to reflect what is true now
    // rather than what was true when the screen was first composed (SPEC §13).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(
            ServerIntent.NotificationsEnabledChanged(
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            ),
        )
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is ServerEffect.CopiedToClipboard -> {
                context.copyToClipboard(CLIPBOARD_LABEL, effect.label)
                context.showToast(resources.getString(R.string.server_copied))
            }

            is ServerEffect.ShowError ->
                context.showToast(resources.getString(effect.error.toMessageRes()))

            ServerEffect.RequestNotificationPermission -> onRequestNotificationPermission()
            is ServerEffect.NavigateToQr -> onNavigateToQr(effect.url)
            ServerEffect.NavigateToSettings -> onNavigateToSettings()
            ServerEffect.OpenBatterySettings ->
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

            ServerEffect.OpenNotificationSettings -> context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    ServerContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun ServerContent(
    state: ServerState,
    onIntent: (ServerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space),
        verticalArrangement = Arrangement.spacedBy(Dimens.space),
    ) {
        AddressBlock(state = state, onIntent = onIntent)

        StartStopButton(state = state, onIntent = onIntent)

        if (state.status.isRunning) {
            OutlinedButton(
                onClick = { onIntent(ServerIntent.TogglePause) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isPaused) R.string.server_resume else R.string.server_pause,
                    ),
                )
            }
        }

        if (state.didFallBack) {
            FallbackNotice(state = state, onIntent = onIntent)
        }

        state.activeChaosProfileName?.let { ChaosBanner(profileName = it) }

        if (!state.isApiKeyEnabled) {
            OpenAccessNote()
        }

        if (state.showNotificationWarning) {
            NotificationWarning(onIntent = onIntent)
        }

        if (state.showBatteryOptimisationCard) {
            BatteryOptimisationCard(onIntent = onIntent)
        }

        Stats(state = state)
    }
}

/**
 * The hero: the winning address, large and monospaced, with a copy button and a QR.
 *
 * `localhost` gets its own permanent row rather than being one of the ranked addresses, because
 * it answers a different question — "how does another app on this phone reach it" — and it is the
 * only row that is still correct when there is no network at all.
 */
@Composable
private fun AddressBlock(
    state: ServerState,
    onIntent: (ServerIntent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.elevationCard),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            val primary = state.primaryBaseUrl
            if (primary == null) {
                Text(
                    text = stringResource(R.string.server_no_network),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.server_no_network_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = Dimens.heroAddressTextSize,
                    ),
                )
                state.primaryAddress?.let { address ->
                    Text(
                        text = stringResource(R.string.server_interface, address.interfaceName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                    TextButton(onClick = { onIntent(ServerIntent.CopyAddress(primary)) }) {
                        Text(stringResource(R.string.server_copy))
                    }
                    TextButton(onClick = { onIntent(ServerIntent.ShowQr) }) {
                        Text(stringResource(R.string.server_qr))
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = state.loopbackUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                TextButton(onClick = { onIntent(ServerIntent.CopyAddress(state.loopbackUrl)) }) {
                    Text(stringResource(R.string.server_copy))
                }
            }

            val extras = state.addresses.drop(1)
            if (extras.isNotEmpty()) {
                TextButton(onClick = { onIntent(ServerIntent.ToggleAddressList) }) {
                    Text(
                        if (state.isAddressListExpanded) {
                            stringResource(R.string.server_hide_addresses)
                        } else {
                            stringResource(R.string.server_more_addresses, extras.size)
                        },
                    )
                }
                if (state.isAddressListExpanded) {
                    extras.forEach { address ->
                        AddressRow(
                            address = address,
                            port = state.boundPort,
                            onCopy = { onIntent(ServerIntent.CopyAddress(it)) },
                        )
                    }
                }
            }

            state.advertisedName?.let { name ->
                Text(
                    text = stringResource(R.string.server_advertised_as, name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddressRow(
    address: NetworkAddress,
    port: Int,
    onCopy: (String) -> Unit,
) {
    val url = address.toBaseUrl(port)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = when (address.kind) {
                    // The VPN row carries its own explanation: it looks like a perfectly good
                    // address and no LAN client can route to it (SPEC §13).
                    NetworkAddress.Kind.VPN -> stringResource(R.string.server_vpn_note)
                    else -> address.interfaceName
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onCopy(url) }) {
            Text(stringResource(R.string.server_copy))
        }
    }
}

@Composable
private fun StartStopButton(
    state: ServerState,
    onIntent: (ServerIntent) -> Unit,
) {
    val running = state.status.isRunning
    Button(
        onClick = { onIntent(if (running) ServerIntent.StopServer else ServerIntent.StartServer) },
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.primaryActionHeight),
        colors = if (running) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        enabled = state.status !is ServerStatus.Starting && state.status !is ServerStatus.Stopping,
    ) {
        Text(
            text = when (state.status) {
                is ServerStatus.Running -> stringResource(R.string.server_stop)
                ServerStatus.Starting -> stringResource(R.string.server_starting)
                ServerStatus.Stopping -> stringResource(R.string.server_stopping)
                else -> stringResource(R.string.server_start)
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun FallbackNotice(
    state: ServerState,
    onIntent: (ServerIntent) -> Unit,
) {
    Column {
        Text(
            text = stringResource(
                R.string.server_port_fallback,
                state.requestedPort,
                state.boundPort,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { onIntent(ServerIntent.RememberFallbackPort) }) {
            Text(stringResource(R.string.server_remember_port, state.boundPort))
        }
    }
}

/**
 * The server runs without the notification; what is lost is stated plainly (SPEC §13).
 *
 * Two things go with the notification: the ability to stop the server without opening the app,
 * and a signal to the system that the process is doing something the user asked for. Neither is
 * worth a modal, and neither is worth hiding — so it is a card with a way to fix it.
 */
@Composable
private fun NotificationWarning(onIntent: (ServerIntent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            Text(
                text = stringResource(R.string.server_notification_off_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.server_notification_off_body),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { onIntent(ServerIntent.OpenNotificationSettings) }) {
                Text(stringResource(R.string.server_notification_off_action))
            }
        }
    }
}

@Composable
private fun BatteryOptimisationCard(onIntent: (ServerIntent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            Text(
                text = stringResource(R.string.server_battery_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.server_battery_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                TextButton(onClick = { onIntent(ServerIntent.RequestBatteryExemption) }) {
                    Text(stringResource(R.string.server_battery_action))
                }
                TextButton(onClick = { onIntent(ServerIntent.DismissBatteryCard) }) {
                    Text(stringResource(R.string.server_battery_dismiss))
                }
            }
        }
    }
}

@Composable
private fun Stats(state: ServerState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
        StatRow(
            label = stringResource(R.string.server_stat_uptime),
            value = DateUtil.formatUptime(state.uptimeMillis),
        )
        StatRow(
            label = stringResource(R.string.server_stat_requests),
            value = state.requestCount.toString(),
        )
        StatRow(
            label = stringResource(R.string.server_stat_rate),
            value = stringResource(R.string.server_stat_rate_value, state.requestsPerMinute),
        )
        StatRow(
            label = stringResource(R.string.server_stat_unmatched),
            value = state.unmatchedCount.toString(),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

private const val CLIPBOARD_LABEL = "Mock server address"

@Preview(name = "Server running")
@Composable
internal fun ServerContentRunningPreview() {
    AppTheme {
        ServerContent(
            state = ServerState(
                status = ServerStatus.Running(
                    address = BoundAddress("0.0.0.0", 8081),
                    requestedPort = 8080,
                    startedAt = 0L,
                ),
                addresses = listOf(
                    NetworkAddress("wlan0", "192.168.1.45", NetworkAddress.Kind.WIFI),
                    NetworkAddress("tun0", "10.8.0.2", NetworkAddress.Kind.VPN),
                ),
                requestedPort = 8080,
                requestCount = 128,
                unmatchedCount = 3,
                uptimeMillis = 754_000,
                requestsPerMinute = 10,
                activeChaosProfileName = "Flaky backend",
            ),
            onIntent = {},
        )
    }
}

@Preview(name = "Server stopped, no network")
@Composable
internal fun ServerContentStoppedPreview() {
    AppTheme {
        ServerContent(state = ServerState(requestedPort = 8080), onIntent = {})
    }
}
