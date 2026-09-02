package com.minion.mockserver.feature.server.status

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.domain.model.BoundAddress
import com.minion.mockserver.core.domain.model.NetworkAddress
import com.minion.mockserver.core.domain.model.ServerStatus
import com.minion.mockserver.core.ui.component.AddressBlock
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.ChaosBanner
import com.minion.mockserver.core.ui.component.NoticeCard
import com.minion.mockserver.core.ui.component.OpenAccessNote
import com.minion.mockserver.core.ui.component.MetricRow
import com.minion.mockserver.core.ui.component.StateDot
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.DateUtil
import com.minion.mockserver.core.ui.util.copyToClipboard
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.server.R

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
    onNavigateToUnmatched: () -> Unit,
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
            ServerEffect.NavigateToUnmatched -> onNavigateToUnmatched()
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
    AppScaffold(
        title = stringResource(R.string.server_title),
        modifier = modifier,
        actions = { StatusChip(state = state) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                isRunning = state.status.isRunning,
                onCopy = { onIntent(ServerIntent.CopyAddress(it)) },
                onShowQr = { onIntent(ServerIntent.ShowQr) },
                onToggleExpanded = { onIntent(ServerIntent.ToggleAddressList) },
            )

            ServerActions(state = state, onIntent = onIntent)

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

            // Never on first launch: this appears only once the app has detected it was killed
            // while the server was running (SPEC §8.2).
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

            SessionCounters(state = state, onIntent = onIntent)
        }
    }
}

/**
 * The tab bar's status slot: a dot and the state in words.
 *
 * Both, not either. The glow ring is what separates running from stopped at a glance, but a ring
 * is invisible to a screen reader and easy to miss beside a colour a user may not distinguish —
 * so the words carry the same fact, and the dot is left decorative.
 */
@Composable
private fun StatusChip(state: ServerState) {
    val running = state.status.isRunning
    val label = when (state.status) {
        is ServerStatus.Running ->
            stringResource(R.string.server_state_running, DateUtil.formatUptime(state.uptimeMillis))

        ServerStatus.Starting -> stringResource(R.string.server_starting)
        ServerStatus.Stopping -> stringResource(R.string.server_stopping)
        else -> stringResource(R.string.server_state_stopped)
    }
    val tint = if (running) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        modifier = Modifier.padding(end = Dimens.space),
    ) {
        StateDot(color = tint, glowing = running)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = tint, maxLines = 1)
    }
}

/**
 * Start, or stop and pause, plus the line that says what the button will do.
 *
 * Stopped is one full-width action because there is only one thing to do. Running is two side by
 * side, because stopping and pausing are alternatives and stacking them implies a sequence. The
 * explanatory line lives under the buttons rather than inside a label, which is what lets Stop
 * and Pause stay one word each.
 */
@Composable
private fun ServerActions(
    state: ServerState,
    onIntent: (ServerIntent) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val transitioning =
        state.status is ServerStatus.Starting || state.status is ServerStatus.Stopping
    val running = state.status.isRunning

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
        if (running) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                ActionButton(
                    label = stringResource(R.string.server_stop),
                    icon = MockIcons.StopFill,
                    tint = scheme.error,
                    container = scheme.errorContainer,
                    enabled = !transitioning,
                    onClick = { onIntent(ServerIntent.StopServer) },
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    label = stringResource(
                        if (state.isPaused) R.string.server_resume else R.string.server_pause,
                    ),
                    icon = MockIcons.Pause,
                    tint = scheme.onSurface,
                    container = Color.Transparent,
                    enabled = !transitioning,
                    onClick = { onIntent(ServerIntent.TogglePause) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ActionButton(
                label = when (state.status) {
                    ServerStatus.Starting -> stringResource(R.string.server_starting)
                    ServerStatus.Stopping -> stringResource(R.string.server_stopping)
                    else -> stringResource(R.string.server_start)
                },
                icon = MockIcons.PlayFill,
                tint = scheme.primary,
                container = scheme.primaryContainer,
                enabled = !transitioning,
                onClick = { onIntent(ServerIntent.StartServer) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = if (running) {
                stringResource(R.string.server_pause_note)
            } else {
                val interfaceName = state.primaryAddress?.interfaceName
                if (interfaceName == null) {
                    stringResource(R.string.server_start_note_local, state.boundPort)
                } else {
                    stringResource(R.string.server_start_note, state.boundPort, interfaceName)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * One button in the design's outlined style: a tinted fill, a border of the same tint, and a
 * label and icon that share it.
 *
 * Nocturne outlines its primary action rather than flooding it, so every state here is one colour
 * used three ways instead of a filled surface with a contrasting label on top.
 */
@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dimens.touchTarget),
        enabled = enabled,
        border = BorderStroke(
            width = Dimens.hairline,
            color = if (enabled) tint else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = tint,
        ),
    ) {
        Icon(
            imageVector = icon,
            // The label beside it says the same thing.
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(start = Dimens.spaceSmall),
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
 * The four session counters, under a "this session" eyebrow.
 *
 * Every counter reads as an em dash until the server has actually run, rather than as a measured
 * zero — "0 requests" and "no session yet" are different facts, and the dash is the only one of
 * the two that is true on first launch.
 *
 * Unmatched is the only row that can be tapped, and only when it has something to show. A count
 * of failures with no way to reach them is a dead end; the row is the shortest path from noticing
 * the number to reading the requests behind it.
 */
@Composable
private fun SessionCounters(
    state: ServerState,
    onIntent: (ServerIntent) -> Unit,
) {
    val hasSession = state.uptimeMillis > 0 || state.requestCount > 0
    val none = stringResource(R.string.server_stat_none)
    val hasUnmatched = hasSession && state.unmatchedCount > 0

    Column {
        Text(
            // Uppercased here rather than in the resource so translators write the phrase
            // naturally and each locale keeps its own casing rules.
            text = stringResource(R.string.server_session).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spaceSmall),
        )
        MetricRow(
            label = stringResource(R.string.server_stat_uptime),
            value = if (hasSession) DateUtil.formatUptime(state.uptimeMillis) else none,
        )
        MetricRow(
            label = stringResource(R.string.server_stat_requests),
            value = if (hasSession) state.requestCount.toString() else none,
        )
        MetricRow(
            label = stringResource(R.string.server_stat_rate),
            value = if (hasSession) {
                stringResource(R.string.server_stat_rate_value, state.requestsPerMinute)
            } else {
                none
            },
        )
        MetricRow(
            label = stringResource(R.string.server_stat_unmatched),
            value = if (hasSession) state.unmatchedCount.toString() else none,
            emphasised = hasUnmatched,
            onClick = if (hasUnmatched) {
                { onIntent(ServerIntent.ViewUnmatched) }
            } else {
                null
            },
        )
        if (hasUnmatched) {
            val count = state.unmatchedCount.toInt()
            Text(
                text = pluralStringResource(R.plurals.server_unmatched_note, count, count),
                style = MockTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.spaceSmall),
            )
        }
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
