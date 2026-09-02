package com.minion.mockserver.feature.settings.chaos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.LabelledTextField
import com.minion.mockserver.core.ui.component.ProbabilitySlider
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.feature.settings.R

/** The chaos profile editor (SPEC §6). */
@Composable
internal fun ChaosEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChaosEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChaosEditorContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun ChaosEditorContent(
    state: ChaosEditorState,
    onIntent: (ChaosEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val editing = state.editing
    if (editing != null) {
        // Back out of the form returns to the list, not out of the screen: the profile being
        // edited is unsaved, and a back arrow that discarded it silently would be a trap.
        AppScaffold(
            title = stringResource(R.string.chaos_edit_title),
            modifier = modifier,
            onBack = { onIntent(ChaosEditorIntent.Cancel) },
        ) { padding ->
            ProfileForm(
                profile = editing,
                error = state.error,
                onIntent = onIntent,
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    AppScaffold(
        title = stringResource(R.string.chaos_title),
        modifier = modifier,
        onBack = onNavigateBack,
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
    error: com.minion.mockserver.core.common.error.DomainError?,
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
        LabelledTextField(
            value = profile.name,
            onValueChange = { onIntent(ChaosEditorIntent.UpdateName(it)) },
            label = stringResource(R.string.chaos_name),
        )

        LabelledTextField(
            value = (profile.delay as? DelaySpec.Fixed)?.millis?.toString().orEmpty(),
            onValueChange = { onIntent(ChaosEditorIntent.UpdateFixedDelay(it.toLongOrNull())) },
            label = stringResource(R.string.chaos_delay),
            hint = stringResource(R.string.chaos_delay_help),
            numeric = true,
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

        LabelledTextField(
            value = profile.connectionReset.dropAfterBytes.toString(),
            onValueChange = {
                it.toIntOrNull()?.let { bytes -> onIntent(ChaosEditorIntent.UpdateResetBytes(bytes)) }
            },
            label = stringResource(R.string.chaos_reset_bytes),
            numeric = true,
        )

        ProbabilitySlider(
            label = stringResource(R.string.chaos_malformed_probability),
            help = stringResource(R.string.chaos_malformed_help),
            value = profile.malformedBody.probability,
            onChange = { onIntent(ChaosEditorIntent.UpdateMalformedProbability(it)) },
        )

        // The last field in the form, so its action key closes the keyboard rather than
        // pointing at a field that is not there.
        LabelledTextField(
            value = profile.malformedBody.truncateAtPercent.toString(),
            onValueChange = {
                it.toIntOrNull()?.let { percent ->
                    onIntent(ChaosEditorIntent.UpdateMalformedPercent(percent))
                }
            },
            label = stringResource(R.string.chaos_malformed_percent),
            numeric = true,
            imeAction = ImeAction.Done,
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
