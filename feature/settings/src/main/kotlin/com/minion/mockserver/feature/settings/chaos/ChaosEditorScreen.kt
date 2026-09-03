package com.minion.mockserver.feature.settings.chaos

import androidx.annotation.PluralsRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.Callout
import com.minion.mockserver.core.ui.component.ExplainerPoint
import com.minion.mockserver.core.ui.component.ExplainerSheet
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.LabelledTextField
import com.minion.mockserver.core.ui.component.PercentSlider
import com.minion.mockserver.core.ui.component.ProfileRow
import com.minion.mockserver.core.ui.component.SectionTitle
import com.minion.mockserver.core.ui.component.chaosDescription
import com.minion.mockserver.core.ui.component.chaosOdds
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
        ProfileForm(profile = editing, onIntent = onIntent, modifier = modifier)
        return
    }

    AppScaffold(
        title = stringResource(R.string.chaos_title),
        modifier = modifier,
        onBack = onNavigateBack,
        subtitle = stringResource(R.string.chaos_subtitle),
        actions = {
            IconButton(onClick = { onIntent(ChaosEditorIntent.ShowExplainer) }) {
                Icon(
                    imageVector = MockIcons.Question,
                    contentDescription = stringResource(R.string.chaos_explain),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = SEED_KEY) {
                Callout(
                    text = stringResource(R.string.chaos_seed_note, state.chaosSeed),
                    modifier = Modifier.padding(Dimens.space),
                )
            }

            items(items = state.profiles, key = { it.id }) { profile ->
                val isNone = profile.id == ChaosProfile.NONE_ID
                ProfileRow(
                    name = profile.name,
                    description = chaosDescription(profile),
                    // A null active id means None, so that row has to answer for it.
                    selected = state.activeProfileId == profile.id ||
                        (state.activeProfileId == null && isNone),
                    onSelect = {
                        onIntent(ChaosEditorIntent.SelectProfile(profile.id.takeIf { !isNone }))
                    },
                    // None is the absence of chaos rather than a profile someone made, so there
                    // is nothing in it to edit.
                    onEdit = if (isNone) {
                        null
                    } else {
                        { onIntent(ChaosEditorIntent.EditProfile(profile.id)) }
                    },
                )
            }

            item(key = NEW_PROFILE_KEY) {
                NewProfileButton(
                    onClick = { onIntent(ChaosEditorIntent.NewProfile) },
                    modifier = Modifier.padding(Dimens.space),
                )
            }
        }
    }

    if (state.isExplainerOpen) {
        ChaosExplainer(onDismiss = { onIntent(ChaosEditorIntent.DismissExplainer) })
    }
}

/** Screen 1s. What any of this does, once, in full. */
@Composable
private fun ChaosExplainer(onDismiss: () -> Unit) {
    ExplainerSheet(
        title = stringResource(R.string.chaos_explain_title),
        lead = stringResource(R.string.chaos_explain_lead),
        points = listOf(
            ExplainerPoint(
                icon = MockIcons.WarningOctagon,
                title = stringResource(R.string.chaos_explain_error_title),
                body = stringResource(R.string.chaos_explain_error_body),
            ),
            ExplainerPoint(
                icon = MockIcons.Plugs,
                title = stringResource(R.string.chaos_explain_reset_title),
                body = stringResource(R.string.chaos_explain_reset_body),
            ),
            ExplainerPoint(
                icon = MockIcons.Scissors,
                title = stringResource(R.string.chaos_explain_malformed_title),
                body = stringResource(R.string.chaos_explain_malformed_body),
            ),
            ExplainerPoint(
                icon = MockIcons.DiceFive,
                title = stringResource(R.string.chaos_explain_seed_title),
                body = stringResource(R.string.chaos_explain_seed_body),
            ),
        ),
        closing = stringResource(R.string.chaos_explain_closing),
        dismissLabel = stringResource(R.string.chaos_explain_dismiss),
        onDismiss = onDismiss,
    )
}

/** Screen 1m. Every percentage restated as what it will actually do. */
@Composable
private fun ProfileForm(
    profile: ChaosProfile,
    onIntent: (ChaosEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = profile.name.ifBlank { stringResource(R.string.chaos_new) },
        modifier = modifier,
        onBack = { onIntent(ChaosEditorIntent.Cancel) },
        subtitle = stringResource(R.string.chaos_profile_subtitle),
        actions = {
            TextButton(onClick = { onIntent(ChaosEditorIntent.Save) }) {
                Text(
                    text = stringResource(R.string.chaos_save),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            // The same sentence the list shows, so what you are editing and what you will read
            // afterwards cannot disagree.
            Callout(
                lead = stringResource(R.string.chaos_profile_summary_title),
                text = chaosDescription(profile),
            )

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
                unit = stringResource(R.string.chaos_delay_unit),
            )

            FadedDivider()

            SectionTitle(stringResource(R.string.chaos_failures_title))
            Text(
                text = stringResource(R.string.chaos_precedence_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PercentSlider(
                label = stringResource(R.string.chaos_error_probability),
                help = failureHelp(
                    probability = profile.errorInjection.probability,
                    offHelp = stringResource(R.string.chaos_error_help),
                    onHelp = R.plurals.chaos_slider_error_on,
                ),
                value = profile.errorInjection.probability,
                onChange = { onIntent(ChaosEditorIntent.UpdateErrorProbability(it)) },
            )

            PercentSlider(
                label = stringResource(R.string.chaos_reset_probability),
                help = failureHelp(
                    probability = profile.connectionReset.probability,
                    offHelp = stringResource(R.string.chaos_reset_help),
                    onHelp = R.plurals.chaos_slider_reset_on,
                ),
                value = profile.connectionReset.probability,
                onChange = { onIntent(ChaosEditorIntent.UpdateResetProbability(it)) },
            )

            PercentSlider(
                label = stringResource(R.string.chaos_malformed_probability),
                help = failureHelp(
                    probability = profile.malformedBody.probability,
                    offHelp = stringResource(R.string.chaos_malformed_help),
                    onHelp = R.plurals.chaos_slider_malformed_on,
                ),
                value = profile.malformedBody.probability,
                onChange = { onIntent(ChaosEditorIntent.UpdateMalformedProbability(it)) },
            )
        }
    }
}

/**
 * The line under a slider, which has to read differently at zero.
 *
 * At any other value it says what will happen. At zero there is nothing to describe, so it says
 * so and then describes what turning it up *would* do — a slider whose explanation vanishes when
 * it is off is one you have to move to find out what it is for.
 */
@Composable
private fun failureHelp(
    probability: Double,
    offHelp: String,
    @PluralsRes onHelp: Int,
): String = if (probability <= 0.0) {
    stringResource(R.string.chaos_slider_off, offHelp)
} else {
    val (n, d) = chaosOdds(probability)
    pluralStringResource(onHelp, n, n, d)
}

/** The design's end-of-list action, matching the Routes tab and the collection detail. */
@Composable
private fun NewProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.touchTarget),
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(
            imageVector = MockIcons.Plus,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Text(
            text = stringResource(R.string.chaos_new),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(start = Dimens.spaceSmall),
        )
    }
}

private const val SEED_KEY = "seed"
private const val NEW_PROFILE_KEY = "new-profile"

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
