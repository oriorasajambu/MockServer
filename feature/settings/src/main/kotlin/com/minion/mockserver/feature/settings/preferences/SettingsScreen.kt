package com.minion.mockserver.feature.settings.preferences

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.ExportActionRow
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.LabelledTextField
import com.minion.mockserver.core.ui.component.SectionTitle
import com.minion.mockserver.core.ui.component.ToggleRow
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.rememberDocumentSaver
import com.minion.mockserver.core.ui.util.shareText
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.settings.R
import android.provider.Settings as AndroidSettings

/** The Settings tab (SPEC §10.1). */
@Composable
internal fun SettingsScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToChaosEditor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // `stringResource` wherever the string is known at composition. These are not: the resource
    // id comes from a `DomainError` that does not exist yet, or the arguments arrive with the
    // effect. `LocalResources`, not `context.getString` — a Context read is not invalidated by a
    // configuration change, so text resolved through it goes stale after a locale switch.
    val resources = LocalResources.current

    val saver = rememberDocumentSaver(
        onSaved = { fileName ->
            context.showToast(resources.getString(R.string.settings_export_saved, fileName))
        },
        onFailed = { error -> context.showToast(resources.getString(error.toMessageRes())) },
    )

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is SettingsEffect.ShareText -> context.shareText(effect.text, effect.subject)
            is SettingsEffect.SaveToFile -> saver.save(effect.fileName, effect.content)
            SettingsEffect.NavigateToImport -> onNavigateToImport()
            SettingsEffect.NavigateToChaosEditor -> onNavigateToChaosEditor()
            SettingsEffect.OpenBatterySettings -> context.startActivity(
                Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            is SettingsEffect.ShowError ->
                context.showToast(resources.getString(effect.error.toMessageRes()))

            is SettingsEffect.ShowMessage -> context.showToast(
                resources.getString(R.string.settings_seed_regenerated, effect.message),
            )
        }
    }

    SettingsContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            if (state.showRestartPrompt) {
                RestartPrompt(state = state, onIntent = onIntent)
            }

            ServerSection(state = state, onIntent = onIntent)
            FadedDivider()
            AccessSection(state = state, onIntent = onIntent)
            FadedDivider()
            ChaosSection(state = state, onIntent = onIntent)
            FadedDivider()
            TrafficLogSection(state = state, onIntent = onIntent)
            FadedDivider()
            UnmatchedSection(state = state, onIntent = onIntent)
            FadedDivider()
            DataSection(onIntent = onIntent)
            FadedDivider()
            BatterySection(onIntent = onIntent)
        }
    }
}

/** The port. Stale until a restart, which is what [RestartPrompt] is for. */
@Composable
private fun ServerSection(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_section_server))

    LabelledTextField(
        value = state.portInput,
        onValueChange = { onIntent(SettingsIntent.UpdatePort(it)) },
        label = stringResource(R.string.settings_port),
        hint = stringResource(R.string.settings_port_help),
        isError = state.error != null,
        numeric = true,
    )
}

/** The optional API key: the switch, the header it travels in, and the token itself. */
@Composable
private fun AccessSection(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_section_access))

    ToggleRow(
        label = stringResource(R.string.settings_api_key),
        checked = state.settings.apiKeyEnabled,
        onCheckedChange = { onIntent(SettingsIntent.SetApiKeyEnabled(it)) },
    )
    if (state.settings.apiKeyEnabled) {
        LabelledTextField(
            value = state.settings.apiKeyHeaderName,
            onValueChange = { onIntent(SettingsIntent.UpdateApiKeyHeader(it)) },
            label = stringResource(R.string.settings_api_key_header),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.settings.apiKeyValue,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            TextButton(onClick = { onIntent(SettingsIntent.GenerateApiKey) }) {
                Text(stringResource(R.string.settings_regenerate))
            }
        }
    }
}

/** Which profile is global, and the seed that makes its failures reproducible (SPEC §6.3). */
@Composable
private fun ChaosSection(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_section_chaos))

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        state.chaosProfiles.forEach { profile ->
            FilterChip(
                selected = state.settings.globalChaosProfileId == profile.id ||
                    (state.settings.globalChaosProfileId == null && profile.id == ChaosProfile.NONE_ID),
                onClick = {
                    onIntent(
                        SettingsIntent.SelectChaosProfile(
                            profile.id.takeIf { it != ChaosProfile.NONE_ID },
                        ),
                    )
                },
                label = { Text(profile.name) },
            )
        }
    }
    TextButton(onClick = { onIntent(SettingsIntent.OpenChaosEditor) }) {
        Text(stringResource(R.string.settings_edit_profiles))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(stringResource(R.string.settings_seed), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = state.settings.chaosSeed.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onIntent(SettingsIntent.RegenerateChaosSeed) }) {
            Text(stringResource(R.string.settings_regenerate))
        }
    }
    Text(
        text = stringResource(R.string.settings_seed_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** How many entries the in-memory ring buffer keeps (SPEC §7.1). */
@Composable
private fun TrafficLogSection(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_section_traffic))

    LabelledTextField(
        value = state.logCapacityInput,
        onValueChange = { onIntent(SettingsIntent.UpdateLogCapacity(it)) },
        label = stringResource(R.string.settings_log_capacity),
        hint = stringResource(R.string.settings_log_capacity_help),
        numeric = true,
    )
}

/** The fallback a request that matched nothing gets (SPEC §4.5). */
@Composable
private fun UnmatchedSection(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_section_unmatched))

    LabelledTextField(
        value = state.settings.unmatchedResponse.statusCode.toString(),
        onValueChange = {
            it.toIntOrNull()?.let { status -> onIntent(SettingsIntent.UpdateUnmatchedStatus(status)) }
        },
        label = stringResource(R.string.settings_unmatched_status),
        numeric = true,
    )
    // Not a LabelledTextField: the body is the one multi-line field on the screen, and it is
    // monospaced because it is JSON someone will compare against what their client received.
    OutlinedTextField(
        value = state.settings.unmatchedResponse.body,
        onValueChange = { onIntent(SettingsIntent.UpdateUnmatchedBody(it)) },
        label = { Text(stringResource(R.string.settings_unmatched_body)) },
        supportingText = { Text(stringResource(R.string.settings_unmatched_help)) },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Import and export. Takes no state — every control here is a plain action. */
@Composable
private fun DataSection(onIntent: (SettingsIntent) -> Unit) {
    SectionTitle(stringResource(R.string.settings_section_data))

    // Import is the section's primary action and gets its own line; export is two destinations
    // for one thing and reads as a row.
    Button(onClick = { onIntent(SettingsIntent.OpenImport) }) {
        Text(stringResource(R.string.settings_import))
    }

    ExportActionRow(
        label = stringResource(R.string.settings_export_everything),
        onShare = { onIntent(SettingsIntent.ExportAll) },
        onSave = { onIntent(SettingsIntent.SaveExportToFile) },
    )
}

/** The always-available route to the exemption, distinct from the card the server screen shows. */
@Composable
private fun BatterySection(onIntent: (SettingsIntent) -> Unit) {
    SectionTitle(stringResource(R.string.settings_section_battery))

    Text(
        text = stringResource(R.string.settings_battery_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { onIntent(SettingsIntent.OpenBatterySettings) }) {
        Text(stringResource(R.string.settings_battery_action))
    }
}

/**
 * The restart prompt, naming the fields that are stale.
 *
 * Explicit rather than automatic: a restart drops every keep-alive connection, so a client
 * mid-test would see a connection error it did not cause.
 */
@Composable
private fun RestartPrompt(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    // Resolved through `map` — an inline lambda, so the composable `label()` is legal inside it —
    // before the join, which takes a non-inline transform and would not be.
    val staleFields = state.restartRequired.map { it.label() }.joinToString(FIELD_SEPARATOR)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
        Text(
            text = stringResource(R.string.settings_restart_required, staleFields),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Button(onClick = { onIntent(SettingsIntent.RestartServer) }) {
            Text(stringResource(R.string.settings_restart))
        }
    }
}

@Composable
private fun SettingsState.RestartField.label(): String = when (this) {
    SettingsState.RestartField.PORT -> stringResource(R.string.settings_field_port)
    SettingsState.RestartField.BIND_ADDRESS -> stringResource(R.string.settings_field_bind)
    SettingsState.RestartField.API_KEY -> stringResource(R.string.settings_field_api_key)
}

@Preview
@Composable
internal fun SettingsContentPreview() {
    AppTheme {
        SettingsContent(
            state = SettingsState(
                portInput = "8080",
                logCapacityInput = "500",
                chaosProfiles = listOf(ChaosProfile.NONE),
            ),
            onIntent = {},
        )
    }
}

/** Between the names of the settings that need a restart. */
private const val FIELD_SEPARATOR = ", "
