package com.minion.mockserver.feature.server

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
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.BoundAddress
import com.minion.mockserver.core.domain.model.NetworkAddress
import com.minion.mockserver.core.domain.model.ServerStatus
import com.minion.mockserver.core.ui.component.AddressBlock
import com.minion.mockserver.core.ui.component.ChaosBanner
import com.minion.mockserver.core.ui.component.NoticeCard
import com.minion.mockserver.core.ui.component.StatRow
import com.minion.mockserver.core.ui.component.OpenAccessNote
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.DateUtil
import com.minion.mockserver.core.ui.util.copyToClipboard
import com.minion.mockserver.core.ui.util.showToast

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
    // `stringResource` wherever the string is known at composition. These are not: the resource
    // id comes from a `DomainError` that does not exist yet, or the arguments arrive with the
    // effect. `LocalResources`, not `context.getString` — a Context read is not invalidated by a
    // configuration change, so text resolved through it goes stale after a locale switch.
    val resources = LocalResources.current

    val copiedMessage = stringResource(R.string.server_copied)

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
                context.showToast(copiedMessage)
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
        AddressBlock(
            primaryBaseUrl = state.primaryBaseUrl,
            primaryAddress = state.primaryAddress,
            loopbackUrl = state.loopbackUrl,
            extraAddresses = state.addresses.drop(1),
            boundPort = state.boundPort,
            isExpanded = state.isAddressListExpanded,
            advertisedName = state.advertisedName,
            onCopy = { onIntent(ServerIntent.CopyAddress(it)) },
            onShowQr = { onIntent(ServerIntent.ShowQr) },
            onToggleExpanded = { onIntent(ServerIntent.ToggleAddressList) },
        )

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

        // The server runs without the notification; what is lost is stated plainly (SPEC §13):
        // the ability to stop it without opening the app, and a signal to the system that the
        // process is doing something the user asked for.
        if (state.showNotificationWarning) {
            NoticeCard(
                title = stringResource(R.string.server_notification_off_title),
                body = stringResource(R.string.server_notification_off_body),
                actionLabel = stringResource(R.string.server_notification_off_action),
                onAction = { onIntent(ServerIntent.OpenNotificationSettings) },
            )
        }

        // Never on first launch: this appears only once the app has detected it was killed while
        // the server was running (SPEC §8.2).
        if (state.showBatteryOptimisationCard) {
            NoticeCard(
                title = stringResource(R.string.server_battery_title),
                body = stringResource(R.string.server_battery_body),
                actionLabel = stringResource(R.string.server_battery_action),
                onAction = { onIntent(ServerIntent.RequestBatteryExemption) },
                dismissLabel = stringResource(R.string.server_battery_dismiss),
                onDismiss = { onIntent(ServerIntent.DismissBatteryCard) },
            )
        }

        Stats(state = state)
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
