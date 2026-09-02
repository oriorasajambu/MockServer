package com.minion.mockserver.feature.routes.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.CollapsibleSection
import com.minion.mockserver.core.ui.component.JsonEditor
import com.minion.mockserver.core.ui.component.LabelledTextField
import com.minion.mockserver.core.ui.component.MatcherAdder
import com.minion.mockserver.core.ui.component.StatusPicker
import com.minion.mockserver.core.ui.component.ValidationWarnings
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.clipboardText
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.routes.R
import com.minion.mockserver.core.ui.R as UiR

/**
 * The route editor (SPEC §10.2).
 *
 * Collapsible sections in one scroll rather than a wizard, so the whole rule stays reviewable —
 * on a phone the temptation is to page it, and paging is exactly what stops anyone noticing that
 * the matcher they added contradicts the path they wrote.
 */
@Composable
internal fun RouteEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrafficEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // `stringResource` wherever the string is known at composition. These are not: the resource
    // id comes from a `DomainError` that does not exist yet, or the arguments arrive with the
    // effect. `LocalResources`, not `context.getString` — a Context read is not invalidated by a
    // configuration change, so text resolved through it goes stale after a locale switch.
    val resources = LocalResources.current
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Read once, on entry, so the Paste button can say what it would paste. Reading it on every
    // recomposition would fire the system's clipboard-access toast repeatedly.
    LaunchedEffect(Unit) { viewModel.onClipboardPreview(context.clipboardText()) }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            RouteEditorEffect.NavigateBack -> onNavigateBack()
            RouteEditorEffect.ShowUnsavedDialog -> showUnsavedDialog = true
            is RouteEditorEffect.NavigateToTrafficEntry -> onNavigateToTrafficEntry(effect.entryId)
            RouteEditorEffect.ReadClipboard -> viewModel.onClipboardRead(context.clipboardText())
            is RouteEditorEffect.ShowError ->
                context.showToast(resources.getString(effect.error.toMessageRes()))
        }
    }

    BackHandler { viewModel.onIntent(RouteEditorIntent.AttemptBack) }

    RouteEditorContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.editor_unsaved_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        onNavigateBack()
                    },
                ) {
                    Text(stringResource(R.string.editor_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text(stringResource(R.string.editor_keep_editing))
                }
            },
        )
    }
}

@Composable
internal fun RouteEditorContent(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = stringResource(
            if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit,
        ),
        modifier = modifier,
        // Through the intent, not straight out: the editor asks about unsaved changes first, and
        // a toolbar arrow that bypassed that would discard work the system back button protects.
        onBack = { onIntent(RouteEditorIntent.AttemptBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            Header(state = state)
            RequestSection(state = state, onIntent = onIntent)
            MatchersSection(state = state, onIntent = onIntent)
            ResponseSection(state = state, onIntent = onIntent)
            ChaosSection(state = state, onIntent = onIntent)
            ValidationWarnings(warnings = state.validation.warnings)
            EditorActions(state = state, onIntent = onIntent)
        }
    }
}

/**
 * One of the editor's four sections, wired to the expanded set in state.
 *
 * A wrapper because the three lines connecting a section to its [RouteEditorState.Section] are
 * identical for all four, and a section that toggles a *different* one than it displays is a bug
 * that reads correctly right up until someone taps it.
 */
@Composable
private fun EditorSection(
    section: RouteEditorState.Section,
    @StringRes titleRes: Int,
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
    content: @Composable () -> Unit,
) {
    CollapsibleSection(
        title = stringResource(titleRes),
        isExpanded = section in state.expandedSections,
        onToggle = { onIntent(RouteEditorIntent.ToggleSection(section)) },
        content = content,
    )
}

/** What the rule answers on: name, method, path — and what URL that adds up to. */
@Composable
private fun RequestSection(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    EditorSection(
        section = RouteEditorState.Section.REQUEST,
        titleRes = R.string.editor_section_request,
        state = state,
        onIntent = onIntent,
    ) {
        OutlinedTextField(
            value = state.route.name,
            onValueChange = { onIntent(RouteEditorIntent.UpdateName(it)) },
            label = { Text(stringResource(R.string.editor_name_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            HttpMethodFilter.entries.forEach { method ->
                FilterChip(
                    selected = state.route.method == method,
                    onClick = { onIntent(RouteEditorIntent.UpdateMethod(method)) },
                    label = { Text(method.name) },
                )
            }
        }

        OutlinedTextField(
            value = state.route.pathPattern,
            onValueChange = { onIntent(RouteEditorIntent.UpdatePath(it)) },
            label = { Text(stringResource(R.string.editor_path_label)) },
            supportingText = { Text(stringResource(R.string.editor_path_help)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )

        PathParameterSuggestions(state = state, onIntent = onIntent)

        // The whole URL a client would call, base path included — the thing that is otherwise
        // assembled in someone's head from three fields on two screens.
        Text(
            text = state.resolvedUrlPreview,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Offered, not applied (SPEC §4.5).
 *
 * A request that missed usually carries one or two segments that look like ids, and turning them
 * into captures by hand on a phone keyboard — brace, name, brace, mid-path — is the slowest edit
 * on this screen.
 */
@Composable
private fun PathParameterSuggestions(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    if (state.pathParameterSuggestions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        state.pathParameterSuggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onIntent(RouteEditorIntent.ParameteriseSegment(suggestion.index)) },
                label = {
                    Text(
                        stringResource(
                            R.string.editor_parameterise,
                            suggestion.segment,
                            suggestion.name,
                        ),
                    )
                },
            )
        }
    }
}

/** The extra conditions, AND-ed. Each one raises the route's specificity. */
@Composable
private fun MatchersSection(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    EditorSection(
        section = RouteEditorState.Section.MATCHERS,
        titleRes = R.string.editor_section_matchers,
        state = state,
        onIntent = onIntent,
    ) {
        state.route.matchers.forEachIndexed { index, matcher ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = matcher.describe(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                TextButton(onClick = { onIntent(RouteEditorIntent.RemoveMatcher(index)) }) {
                    Text(stringResource(R.string.editor_remove))
                }
            }
        }
        MatcherAdder(onAdd = { onIntent(RouteEditorIntent.AddMatcher(it)) })
    }
}

/** Status, the statuses an import found alongside it, and the body. */
@Composable
private fun ResponseSection(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    EditorSection(
        section = RouteEditorState.Section.RESPONSE,
        titleRes = R.string.editor_section_response,
        state = state,
        onIntent = onIntent,
    ) {
        StatusPicker(
            statusCode = state.route.response.statusCode,
            onSelect = { onIntent(RouteEditorIntent.UpdateStatus(it)) },
        )

        if (state.alternateResponses.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
            ) {
                state.alternateResponses.forEach { alternate ->
                    AssistChip(
                        onClick = { onIntent(RouteEditorIntent.ApplyAlternate(alternate.statusCode)) },
                        label = {
                            Text(stringResource(R.string.editor_alternate, alternate.statusCode))
                        },
                    )
                }
            }
        }

        JsonEditor(
            value = state.bodyText,
            onValueChange = { onIntent(RouteEditorIntent.UpdateBody(it)) },
            error = state.validation.errors
                .firstOrNull()
                ?.let { stringResource(it.toMessageRes()) },
            clipboardPreview = state.clipboardPreview,
            onPaste = { onIntent(RouteEditorIntent.PasteFromClipboard) },
            onPrettify = { onIntent(RouteEditorIntent.PrettifyBody) },
        )
    }
}

/** This route's own delay, which applies whether or not a global profile is active. */
@Composable
private fun ChaosSection(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    EditorSection(
        section = RouteEditorState.Section.CHAOS,
        titleRes = R.string.editor_section_chaos,
        state = state,
        onIntent = onIntent,
    ) {
        LabelledTextField(
            value = state.route.response.delayMillisOverride?.toString().orEmpty(),
            onValueChange = { onIntent(RouteEditorIntent.UpdateDelay(it.toLongOrNull())) },
            label = stringResource(R.string.editor_delay_label),
            hint = stringResource(R.string.editor_delay_help),
            numeric = true,
            imeAction = ImeAction.Done,
        )
    }
}

/** Save and the loopback test, side by side — the two ways to leave this screen satisfied. */
@Composable
private fun EditorActions(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
        Button(
            onClick = { onIntent(RouteEditorIntent.Save) },
            enabled = state.canSave,
            modifier = Modifier.weight(ACTION_WEIGHT),
        ) {
            Text(stringResource(R.string.editor_save))
        }
        OutlinedButton(
            onClick = { onIntent(RouteEditorIntent.TestRoute) },
            modifier = Modifier.weight(ACTION_WEIGHT),
        ) {
            Text(stringResource(R.string.editor_test))
        }
    }
}

@Composable
private fun Header(state: RouteEditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            AssistChip(
                onClick = {},
                label = {
                    Text(stringResource(R.string.editor_specificity, state.specificityScore))
                },
            )
            if (state.isShadowed) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(UiR.string.routes_shadowed)) },
                )
            }
        }
        if (state.isShadowed) {
            Text(
                text = stringResource(R.string.editor_shadowed_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun RequestMatcher.describe(): String = when (this) {
    is RequestMatcher.Header -> "header $name ${op.name.lowercase()} $value"
    is RequestMatcher.Query -> "query $name ${op.name.lowercase()} $value"
    is RequestMatcher.BodySubset -> "body contains $json"
}


@Preview
@Composable
internal fun RouteEditorContentPreview() {
    AppTheme {
        RouteEditorContent(
            state = RouteEditorState(
                specificityScore = 34,
                resolvedUrlPreview = "http://192.168.1.45:8080/api/v1/users/{id}",
            ),
            onIntent = {},
        )
    }
}

/** Save and Test split the action row evenly. */
private const val ACTION_WEIGHT = 1f
