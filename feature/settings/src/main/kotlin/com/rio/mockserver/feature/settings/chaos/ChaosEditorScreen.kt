package com.rio.mockserver.feature.settings.chaos

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rio.mockserver.core.designsystem.theme.AppTheme
import com.rio.mockserver.core.designsystem.theme.Dimens
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.DelaySpec
import com.rio.mockserver.core.ui.error.toMessageRes
import com.rio.mockserver.feature.settings.R

/** The chaos profile editor (SPEC §6). */
@Composable
internal fun ChaosEditorScreen(
    modifier: Modifier = Modifier,
    viewModel: ChaosEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChaosEditorContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun ChaosEditorContent(
    state: ChaosEditorState,
    onIntent: (ChaosEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state.editing
    if (editing != null) {
        ProfileForm(profile = editing, error = state.error, onIntent = onIntent, modifier = modifier)
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(ChaosEditorIntent.NewProfile) },
                text = { Text(stringResource(R.string.chaos_new)) },
                icon = {},
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            state.profiles.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIntent(ChaosEditorIntent.EditProfile(profile.id)) }
                        .padding(Dimens.space),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = profile.summary(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The built-in "None" is not stored and cannot be removed — it is what the
                    // absence of chaos is called, not a profile someone made.
                    if (profile.id != ChaosProfile.NONE_ID) {
                        TextButton(onClick = { onIntent(ChaosEditorIntent.DeleteProfile(profile.id)) }) {
                            Text(stringResource(R.string.chaos_delete))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProfileForm(
    profile: ChaosProfile,
    error: com.rio.mockserver.core.common.error.DomainError?,
    onIntent: (ChaosEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
    ) {
        OutlinedTextField(
            value = profile.name,
            onValueChange = { onIntent(ChaosEditorIntent.UpdateName(it)) },
            label = { Text(stringResource(R.string.chaos_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = (profile.delay as? DelaySpec.Fixed)?.millis?.toString().orEmpty(),
            onValueChange = { onIntent(ChaosEditorIntent.UpdateFixedDelay(it.toLongOrNull())) },
            label = { Text(stringResource(R.string.chaos_delay)) },
            supportingText = { Text(stringResource(R.string.chaos_delay_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        ProbabilitySlider(
            label = stringResource(R.string.chaos_error_probability),
            help = stringResource(R.string.chaos_error_help),
            value = profile.errorInjection.probability,
            onChange = { onIntent(ChaosEditorIntent.UpdateErrorProbability(it)) },
        )

        ProbabilitySlider(
            label = stringResource(R.string.chaos_reset_probability),
            help = stringResource(R.string.chaos_reset_help),
            value = profile.connectionReset.probability,
            onChange = { onIntent(ChaosEditorIntent.UpdateResetProbability(it)) },
        )

        OutlinedTextField(
            value = profile.connectionReset.dropAfterBytes.toString(),
            onValueChange = {
                it.toIntOrNull()?.let { bytes -> onIntent(ChaosEditorIntent.UpdateResetBytes(bytes)) }
            },
            label = { Text(stringResource(R.string.chaos_reset_bytes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        ProbabilitySlider(
            label = stringResource(R.string.chaos_malformed_probability),
            help = stringResource(R.string.chaos_malformed_help),
            value = profile.malformedBody.probability,
            onChange = { onIntent(ChaosEditorIntent.UpdateMalformedProbability(it)) },
        )

        OutlinedTextField(
            value = profile.malformedBody.truncateAtPercent.toString(),
            onValueChange = {
                it.toIntOrNull()?.let { percent ->
                    onIntent(ChaosEditorIntent.UpdateMalformedPercent(percent))
                }
            },
            label = { Text(stringResource(R.string.chaos_malformed_percent)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Only one failure mode fires per request, and this is the one place that ordering is
        // visible to the person choosing the probabilities.
        Text(
            text = stringResource(R.string.chaos_precedence_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        error?.let {
            Text(
                text = stringResource(it.toMessageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            Button(
                onClick = { onIntent(ChaosEditorIntent.Save) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.chaos_save))
            }
            TextButton(
                onClick = { onIntent(ChaosEditorIntent.Cancel) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.chaos_cancel))
            }
        }
    }
}

@Composable
private fun ProbabilitySlider(
    label: String,
    help: String,
    value: Double,
    onChange: (Double) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.chaos_percent, label, (value * PERCENT).toInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = 0f..1f,
        )
        Text(
            text = help,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChaosProfile.summary(): String = when {
    !isActive -> stringResource(R.string.chaos_summary_off)
    else -> stringResource(
        R.string.chaos_summary,
        (errorInjection.probability * PERCENT).toInt(),
        (connectionReset.probability * PERCENT).toInt(),
        (malformedBody.probability * PERCENT).toInt(),
    )
}

private const val PERCENT = 100

@Preview
@Composable
internal fun ChaosEditorContentPreview() {
    AppTheme {
        ChaosEditorContent(
            state = ChaosEditorState(profiles = listOf(ChaosProfile.NONE)),
            onIntent = {},
        )
    }
}
