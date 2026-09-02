package com.minion.mockserver.feature.routes.editor

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.StringOp
import com.minion.mockserver.core.domain.usecase.route.RouteValidation
import com.minion.mockserver.core.ui.component.JsonEditor
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.clipboardText
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.routes.R

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
    ) {
        Header(state = state)

        Section(
            title = stringResource(R.string.editor_section_request),
            section = RouteEditorState.Section.REQUEST,
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

            // Offered, not applied (SPEC §4.5). A request that missed usually carries one or two
            // segments that look like ids, and turning them into captures by hand on a phone
            // keyboard — brace, name, brace, in the middle of a path — is the slowest edit here.
            if (state.pathParameterSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
                ) {
                    state.pathParameterSuggestions.forEach { suggestion ->
                        AssistChip(
                            onClick = {
                                onIntent(RouteEditorIntent.ParameteriseSegment(suggestion.index))
                            },
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

            // The whole URL a client would call, base path included — the thing that is otherwise
            // assembled in someone's head from three fields on two screens.
            Text(
                text = state.resolvedUrlPreview,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(
            title = stringResource(R.string.editor_section_matchers),
            section = RouteEditorState.Section.MATCHERS,
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

        Section(
            title = stringResource(R.string.editor_section_response),
            section = RouteEditorState.Section.RESPONSE,
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
                                Text(
                                    stringResource(
                                        R.string.editor_alternate,
                                        alternate.statusCode,
                                    ),
                                )
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

        Section(
            title = stringResource(R.string.editor_section_chaos),
            section = RouteEditorState.Section.CHAOS,
            state = state,
            onIntent = onIntent,
        ) {
            OutlinedTextField(
                value = state.route.response.delayMillisOverride?.toString().orEmpty(),
                onValueChange = { onIntent(RouteEditorIntent.UpdateDelay(it.toLongOrNull())) },
                label = { Text(stringResource(R.string.editor_delay_label)) },
                supportingText = { Text(stringResource(R.string.editor_delay_help)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
            )
        }

        Warnings(warnings = state.validation.warnings)

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            Button(
                onClick = { onIntent(RouteEditorIntent.Save) },
                enabled = state.canSave,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.editor_save))
            }
            OutlinedButton(
                onClick = { onIntent(RouteEditorIntent.TestRoute) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.editor_test))
            }
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
                    label = { Text(stringResource(R.string.routes_shadowed)) },
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

@Composable
private fun Section(
    title: String,
    section: RouteEditorState.Section,
    state: RouteEditorState,
    onIntent: (RouteEditorIntent) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
        TextButton(onClick = { onIntent(RouteEditorIntent.ToggleSection(section)) }) {
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        if (section in state.expandedSections) {
            content()
        }
        HorizontalDivider()
    }
}

/** Common codes pinned, because those are the ones anyone actually mocks (SPEC §10.2). */
@Composable
private fun StatusPicker(statusCode: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            PINNED_STATUSES.forEach { code ->
                FilterChip(
                    selected = statusCode == code,
                    onClick = { onSelect(code) },
                    label = { Text(code.toString()) },
                )
            }
        }
        OutlinedTextField(
            value = statusCode.toString(),
            onValueChange = { it.toIntOrNull()?.let(onSelect) },
            label = { Text(stringResource(R.string.editor_status_label)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
        )
    }
}

@Composable
private fun MatcherAdder(onAdd: (RequestMatcher) -> Unit) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var isHeader by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            FilterChip(
                selected = isHeader,
                onClick = { isHeader = true },
                label = { Text(stringResource(R.string.editor_matcher_header)) },
            )
            FilterChip(
                selected = !isHeader,
                onClick = { isHeader = false },
                label = { Text(stringResource(R.string.editor_matcher_query)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.editor_matcher_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.editor_matcher_value)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            onClick = {
                if (name.isBlank()) return@TextButton
                onAdd(
                    if (isHeader) {
                        RequestMatcher.Header(name, StringOp.EQUALS, value)
                    } else {
                        RequestMatcher.Query(name, StringOp.EQUALS, value)
                    },
                )
                name = ""
                value = ""
            },
        ) {
            Text(stringResource(R.string.editor_add_matcher))
        }
    }
}

/** Warnings are advisory and never block Save — that distinction is the whole point of them. */
@Composable
private fun Warnings(warnings: List<RouteValidation.Warning>) {
    if (warnings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
        warnings.forEach { warning ->
            Text(
                text = warning.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun RouteValidation.Warning.describe(): String = when (this) {
    is RouteValidation.Warning.UnquotedPlaceholder ->
        stringResource(R.string.editor_warn_unquoted, placeholder)

    is RouteValidation.Warning.ReservedHeader ->
        stringResource(R.string.editor_warn_reserved_header, name)

    is RouteValidation.Warning.LongDelay -> stringResource(R.string.editor_warn_long_delay, millis)
    is RouteValidation.Warning.UnknownPathPlaceholder ->
        stringResource(R.string.editor_warn_unknown_placeholder, name)
}

private fun RequestMatcher.describe(): String = when (this) {
    is RequestMatcher.Header -> "header $name ${op.name.lowercase()} $value"
    is RequestMatcher.Query -> "query $name ${op.name.lowercase()} $value"
    is RequestMatcher.BodySubset -> "body contains $json"
}

private val PINNED_STATUSES = listOf(200, 201, 204, 400, 401, 403, 404, 409, 422, 429, 500, 503)

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
