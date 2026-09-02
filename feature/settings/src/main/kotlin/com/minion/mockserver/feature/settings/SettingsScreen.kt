package com.minion.mockserver.feature.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.shareText
import com.minion.mockserver.core.ui.util.rememberDocumentSaver
import com.minion.mockserver.core.ui.util.showToast

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
    // Resources, not `context.getString`: a Context read is not invalidated by a
    // configuration change, so every string resolved through it goes stale after a locale
    // switch and stays stale until the screen is recreated.
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
    ) {
        if (state.showRestartPrompt) {
            RestartPrompt(state = state, onIntent = onIntent)
        }

        SectionTitle(stringResource(R.string.settings_section_server))

        OutlinedTextField(
            value = state.portInput,
            onValueChange = { onIntent(SettingsIntent.UpdatePort(it)) },
            label = { Text(stringResource(R.string.settings_port)) },
            supportingText = { Text(stringResource(R.string.settings_port_help)) },
            isError = state.error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_access))

        ToggleRow(
            label = stringResource(R.string.settings_api_key),
            checked = state.settings.apiKeyEnabled,
            onCheckedChange = { onIntent(SettingsIntent.SetApiKeyEnabled(it)) },
        )
        if (state.settings.apiKeyEnabled) {
            OutlinedTextField(
                value = state.settings.apiKeyHeaderName,
                onValueChange = { onIntent(SettingsIntent.UpdateApiKeyHeader(it)) },
                label = { Text(stringResource(R.string.settings_api_key_header)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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

        HorizontalDivider()
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

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_traffic))

        OutlinedTextField(
            value = state.logCapacityInput,
            onValueChange = { onIntent(SettingsIntent.UpdateLogCapacity(it)) },
            label = { Text(stringResource(R.string.settings_log_capacity)) },
            supportingText = { Text(stringResource(R.string.settings_log_capacity_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_unmatched))

        OutlinedTextField(
            value = state.settings.unmatchedResponse.statusCode.toString(),
            onValueChange = {
                it.toIntOrNull()?.let { status -> onIntent(SettingsIntent.UpdateUnmatchedStatus(status)) }
            },
            label = { Text(stringResource(R.string.settings_unmatched_status)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.settings.unmatchedResponse.body,
            onValueChange = { onIntent(SettingsIntent.UpdateUnmatchedBody(it)) },
            label = { Text(stringResource(R.string.settings_unmatched_body)) },
            supportingText = { Text(stringResource(R.string.settings_unmatched_help)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_data))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            Button(onClick = { onIntent(SettingsIntent.OpenImport) }) {
                Text(stringResource(R.string.settings_import))
            }
            TextButton(onClick = { onIntent(SettingsIntent.ExportAll) }) {
                Text(stringResource(R.string.settings_export_share))
            }
            TextButton(onClick = { onIntent(SettingsIntent.SaveExportToFile) }) {
                Text(stringResource(R.string.settings_export_save))
            }
        }

        HorizontalDivider()
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

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
