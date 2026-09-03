package com.minion.mockserver.feature.routes.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.Callout
import com.minion.mockserver.core.ui.component.ConfirmDialog
import com.minion.mockserver.core.ui.component.EditorTab
import com.minion.mockserver.core.ui.component.EditorTabs
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.JsonEditor
import com.minion.mockserver.core.ui.component.LabelledTextField
import com.minion.mockserver.core.ui.component.MatcherAdder
import com.minion.mockserver.core.ui.component.OptionChipRow
import com.minion.mockserver.core.ui.component.SectionTitle
import com.minion.mockserver.core.ui.component.TestResultBar
import com.minion.mockserver.core.ui.component.Tone
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
        // The same dialog as delete and rename. Discarding work is destructive, and a confirm
        // that looks unlike the app's other confirms is one people dismiss without reading.
        ConfirmDialog(
            title = stringResource(R.string.editor_unsaved_title),
            body = stringResource(R.string.editor_unsaved_body),
            confirmLabel = stringResource(R.string.editor_discard),
            confirmIcon = MockIcons.Trash,
            icon = MockIcons.WarningCircle,
            tone = Tone.Error,
            onConfirm = {
                showUnsavedDialog = false
                onNavigateBack()
            },
            onDismiss = { showUnsavedDialog = false },
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
        subtitle = if (state.hasUnsavedChanges) {
            stringResource(R.string.editor_subtitle_unsaved)
        } else {
            stringResource(
                R.string.editor_subtitle_route,
                state.route.method.name,
                state.route.pathPattern,
            )
        },
        actions = {
            TextButton(
                onClick = { onIntent(RouteEditorIntent.Save) },
                enabled = state.canSave,
            ) {
                Text(
                    text = stringResource(R.string.editor_save),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            EditorTabs(
                tabs = listOf(
                    EditorTab(
                        label = stringResource(R.string.editor_tab_request),
                        hasChanges = RouteEditorState.Tab.REQUEST in state.dirtyTabs,
                    ),
                    EditorTab(
                        label = stringResource(R.string.editor_tab_response),
                        hasChanges = RouteEditorState.Tab.RESPONSE in state.dirtyTabs,
                    ),
                    EditorTab(
                        label = stringResource(R.string.editor_tab_chaos),
                        hasChanges = RouteEditorState.Tab.CHAOS in state.dirtyTabs,
                    ),
                ),
                selectedIndex = state.activeTab.ordinal,
                onSelect = { index ->
                    onIntent(RouteEditorIntent.SelectTab(RouteEditorState.Tab.entries[index]))
                },
                // A route being created has nothing worth reading under the other two tabs until
                // it has a path, so they read as secondary — but stay reachable, because someone
                // who knows they want a 201 should not have to save first to say so.
                dimmed = state.isNew,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.space),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
            ) {
                when (state.activeTab) {
                    RouteEditorState.Tab.REQUEST -> RequestTab(state = state, onIntent = onIntent)
                    RouteEditorState.Tab.RESPONSE -> ResponseTab(state = state, onIntent = onIntent)
                    RouteEditorState.Tab.CHAOS -> ChaosTab(state = state, onIntent = onIntent)
                }
                ValidationWarnings(warnings = state.validation.warnings)
            }

            // The design puts the test footer under Response and Chaos only. Request is where a
            // rule is described rather than tried, and a test button there would fire whatever
            // the response happens to be from a tab you are not looking at.
            if (state.activeTab != RouteEditorState.Tab.REQUEST) {
                EditorFooter(state = state, onIntent = onIntent)
            }
        }
    }
}

/** What the rule answers on: name, method, path, and the extra conditions that narrow it. */
@Composable
private fun RequestTab(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    if (state.isNew) {
        Callout(text = stringResource(R.string.editor_new_callout))
    }

    SpecificityRow(score = state.specificityScore)

    LabelledTextField(
        value = state.route.name,
        onValueChange = { onIntent(RouteEditorIntent.UpdateName(it)) },
        label = stringResource(R.string.editor_name_label),
        hint = stringResource(R.string.editor_name_help),
    )

    SectionTitle(stringResource(R.string.editor_method_label))
    OptionChipRow(
        options = HttpMethodFilter.entries,
        selected = state.route.method,
        onSelect = { onIntent(RouteEditorIntent.UpdateMethod(it)) },
        label = { it.name },
    )

    LabelledTextField(
        value = state.route.pathPattern,
        onValueChange = { onIntent(RouteEditorIntent.UpdatePath(it)) },
        label = stringResource(R.string.editor_path_label),
        mono = true,
    )

    // The whole URL a client would call, base path included — the thing that is otherwise
    // assembled in someone's head from three fields on two screens.
    Callout(
        lead = state.resolvedUrlPreview.takeIf { it.isNotBlank() },
        text = stringResource(R.string.editor_path_help),
    )

    PathParameterSuggestions(state = state, onIntent = onIntent)

    FadedDivider()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(stringResource(R.string.editor_section_matchers))
        Text(
            text = if (state.route.matchers.isEmpty()) {
                stringResource(R.string.editor_matchers_none)
            } else {
                stringResource(R.string.editor_matchers_help)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    state.route.matchers.forEachIndexed { index, matcher ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = matcher.describe(),
                style = MockTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onIntent(RouteEditorIntent.RemoveMatcher(index)) }) {
                Icon(
                    imageVector = MockIcons.X,
                    contentDescription = stringResource(R.string.editor_remove),
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
        }
    }
    MatcherAdder(onAdd = { onIntent(RouteEditorIntent.AddMatcher(it)) })
}

/**
 * How this rule ranks against its neighbours.
 *
 * A number on its own means nothing, so it comes with the sentence that makes it actionable: the
 * score is only ever compared, never read in isolation.
 */
@Composable
private fun SpecificityRow(score: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_specificity_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = score.toString(),
                style = MockTheme.typography.codeMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.editor_specificity_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Status, the statuses an import found alongside it, and the body. */
@Composable
private fun ResponseTab(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    SectionTitle(stringResource(R.string.editor_status_label))
    OptionChipRow(
        options = COMMON_STATUSES,
        selected = state.route.response.statusCode,
        onSelect = { onIntent(RouteEditorIntent.UpdateStatus(it)) },
        label = { it.toString() },
    )

    if (state.alternateResponses.isNotEmpty()) {
        OptionChipRow(
            options = state.alternateResponses.map { it.statusCode },
            selected = state.route.response.statusCode,
            onSelect = { onIntent(RouteEditorIntent.ApplyAlternate(it)) },
            label = { stringResource(R.string.editor_alternate, it) },
        )
    }

    JsonEditor(
        value = state.bodyText,
        onValueChange = { onIntent(RouteEditorIntent.UpdateBody(it)) },
        error = state.validation.errors
            .firstOrNull()
            ?.let { stringResource(it.toMessageRes()) },
        clipboardPreview = state.clipboardPreview,
        onPaste = { onIntent(RouteEditorIntent.PasteFromClipboard) },
        contentType = state.route.response.contentType,
        onPrettify = { onIntent(RouteEditorIntent.PrettifyBody) },
    )
}

/** This route's own delay, and a plain reading of what a call to it does. */
@Composable
private fun ChaosTab(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    val delay = state.route.response.delayMillisOverride

    // Deliberately says nothing about failures. Whether one fires depends on the active global
    // profile, which this screen does not know — and a confident "nothing fails" that turned out
    // to be wrong is worse than not mentioning it.
    Callout(
        lead = stringResource(R.string.editor_chaos_now_title),
        text = if (delay != null && delay > 0) {
            stringResource(R.string.editor_chaos_now_delay, delay, state.route.response.statusCode)
        } else {
            stringResource(R.string.editor_chaos_now_plain, state.route.response.statusCode)
        },
    )

    SectionTitle(stringResource(R.string.editor_chaos_route_only))
    Text(
        text = stringResource(R.string.editor_chaos_route_only_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    LabelledTextField(
        value = delay?.toString().orEmpty(),
        onValueChange = { onIntent(RouteEditorIntent.UpdateDelay(it.toLongOrNull())) },
        label = stringResource(R.string.editor_delay_label),
        hint = stringResource(R.string.editor_delay_help),
        numeric = true,
        imeAction = ImeAction.Done,
        unit = stringResource(R.string.editor_delay_unit),
    )

    FadedDivider()

    SectionTitle(stringResource(R.string.editor_chaos_global))
    Text(
        text = stringResource(R.string.editor_chaos_global_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The sticky footer: what the last test said, then Save and fire-again.
 *
 * Pinned below the scroll rather than sitting at the end of it. The body editor is the tallest
 * thing on this screen, and a Save that scrolls away under the keyboard is one people stop
 * finding.
 */
@Composable
private fun EditorFooter(
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Dimens.space, vertical = Dimens.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        state.testResult?.let { result ->
            TestResultBar(
                statusCode = result.statusCode,
                durationMillis = result.durationMillis,
                byteCount = result.byteCount,
                onClick = { onIntent(RouteEditorIntent.OpenTestEntry) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            OutlinedButton(
                onClick = { onIntent(RouteEditorIntent.Save) },
                enabled = state.canSave,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.touchTarget),
                border = BorderStroke(
                    Dimens.hairline,
                    if (state.canSave) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.editor_save_route),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = { onIntent(RouteEditorIntent.TestRoute) },
                modifier = Modifier.height(Dimens.touchTarget),
                border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
            ) {
                Icon(
                    imageVector = MockIcons.PlayFill,
                    contentDescription = stringResource(R.string.editor_test),
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
        }
    }
}

/** The statuses worth one tap. Anything else is typed into the body's own status field. */
private val COMMON_STATUSES = listOf(200, 201, 204, 400, 404, 500)

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
