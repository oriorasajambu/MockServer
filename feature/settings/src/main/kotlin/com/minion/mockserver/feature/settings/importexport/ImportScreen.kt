package com.minion.mockserver.feature.settings.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.component.AppButton
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.ImportConflict
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.EmptyState
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.FlatDivider
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.settings.R

/**
 * Pick → parse → preview → resolve → commit (SPEC §9.1).
 *
 * The file is read through the storage-access framework, so no storage permission is involved:
 * the user picks the file and the app receives a URI it can read exactly once.
 */
@Composable
internal fun ImportScreen(
    onNavigateToCollection: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // `stringResource` wherever the string is known at composition. These are not: the resource
    // id comes from a `DomainError` that does not exist yet, or the arguments arrive with the
    // effect. `LocalResources`, not `context.getString` — a Context read is not invalidated by a
    // configuration change, so text resolved through it goes stale after a locale switch.
    val resources = LocalResources.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Read on the caller's thread only long enough to get the bytes; parsing is off-main in
        // the ViewModel. A spec file is at most a few megabytes.
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val name = uri.lastPathSegment
        if (content != null) viewModel.onIntent(ImportIntent.FileRead(content, name))
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            ImportEffect.OpenFilePicker -> picker.launch(MIME_TYPES)
            is ImportEffect.NavigateToCollection -> onNavigateToCollection(effect.collectionId)
            is ImportEffect.ShowError ->
                context.showToast(resources.getString(effect.error.toMessageRes()))

            is ImportEffect.ShowImported ->
                context.showToast(
                    resources.getQuantityString(
                        R.plurals.import_done,
                        effect.count,
                        effect.count,
                    ),
                )
        }
    }

    ImportContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun ImportContent(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val preview = state.preview

    AppScaffold(
        title = stringResource(R.string.import_title),
        modifier = modifier,
        onBack = onNavigateBack,
    ) { padding ->
        when {
            state.isParsing -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.import_parsing),
                    modifier = Modifier.padding(top = Dimens.space),
                )
            }

            preview == null -> EmptyState(
                title = stringResource(R.string.import_empty_title),
                description = stringResource(R.string.import_empty_body),
                icon = MockIcons.FileArrowDown,
                // The formats were buried in a paragraph nobody finishes. As chips they answer
                // "will it take my file" before the file picker opens rather than after it.
                chips = listOf(
                    stringResource(R.string.import_format_openapi_30),
                    stringResource(R.string.import_format_openapi_31),
                    stringResource(R.string.import_format_postman),
                    stringResource(R.string.import_format_har),
                ),
                actionLabel = stringResource(R.string.import_pick),
                actionIcon = MockIcons.FolderOpen,
                onAction = { onIntent(ImportIntent.PickFile) },
                caption = stringResource(R.string.import_limits),
                modifier = Modifier.padding(padding),
            )

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.padding(Dimens.space)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.import_found,
                            preview.routes.size,
                            preview.routes.size,
                            preview.format.name,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    if (preview.needsBodyCount > 0) {
                        // The visible gap, rather than a synthesised body full of "string" (§9.1).
                        Text(
                            text = pluralStringResource(
                                R.plurals.import_needs_body,
                                preview.needsBodyCount,
                                preview.needsBodyCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    preview.warnings.forEach { warning ->
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TargetPicker(state = state, onIntent = onIntent)

                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                        TextButton(onClick = { onIntent(ImportIntent.SelectAll) }) {
                            Text(stringResource(R.string.import_select_all))
                        }
                        TextButton(onClick = { onIntent(ImportIntent.SelectNone) }) {
                            Text(stringResource(R.string.import_select_none))
                        }
                    }
                }

                FadedDivider()

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items = preview.routes, key = { it.route.id }) { imported ->
                        val conflict = state.conflicts
                            .firstOrNull { it.incoming.route.id == imported.route.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.space, vertical = Dimens.spaceSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = imported.route.id in state.selectedRouteIds,
                                onCheckedChange = { onIntent(ImportIntent.ToggleRoute(imported.route.id)) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = imported.label,
                                    style = MaterialTheme.typography.bodyMedium
                                        .copy(fontFamily = FontFamily.Monospace),
                                )
                                if (imported.route.needsBody) {
                                    Text(
                                        text = stringResource(R.string.import_no_example),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                conflict?.let {
                                    ConflictResolver(
                                        conflict = it,
                                        onResolve = { resolution ->
                                            onIntent(
                                                ImportIntent.ResolveConflict(
                                                    imported.route.id,
                                                    resolution,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        FlatDivider()
                    }
                }

                AppButton(
                    text = pluralStringResource(
                        R.plurals.import_commit,
                        state.selectedCount,
                        state.selectedCount,
                    ),
                    icon = MockIcons.FileArrowDown,
                    onClick = { onIntent(ImportIntent.Commit) },
                    enabled = state.canCommit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.space),
                )
            }
        }
    }
}

@Composable
private fun TargetPicker(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            FilterChip(
                selected = state.targetCollectionId == null,
                onClick = { onIntent(ImportIntent.SelectTargetCollection(null)) },
                label = { Text(stringResource(R.string.import_new_collection)) },
            )
            state.collections.forEach { collection ->
                FilterChip(
                    selected = state.targetCollectionId == collection.id,
                    onClick = { onIntent(ImportIntent.SelectTargetCollection(collection.id)) },
                    label = { Text(collection.name) },
                )
            }
        }

        if (state.targetCollectionId == null) {
            OutlinedTextField(
                value = state.newCollectionName,
                onValueChange = { onIntent(ImportIntent.UpdateNewCollectionName(it)) },
                label = { Text(stringResource(R.string.import_collection_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.newCollectionBasePath,
                onValueChange = { onIntent(ImportIntent.UpdateNewCollectionBasePath(it)) },
                label = { Text(stringResource(R.string.import_collection_base_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Skip / Replace / Keep both, per route rather than once for the whole file (SPEC §9.1). */
@Composable
private fun ConflictResolver(
    conflict: ImportConflict,
    onResolve: (ImportConflict.Resolution) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.import_conflict, conflict.existing.name),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
            ImportConflict.Resolution.entries.forEach { resolution ->
                FilterChip(
                    selected = conflict.resolution == resolution,
                    onClick = { onResolve(resolution) },
                    label = { Text(resolution.label()) },
                )
            }
        }
    }
}

@Composable
private fun ImportConflict.Resolution.label(): String = when (this) {
    ImportConflict.Resolution.SKIP -> stringResource(R.string.import_skip)
    ImportConflict.Resolution.REPLACE -> stringResource(R.string.import_replace)
    ImportConflict.Resolution.KEEP_BOTH -> stringResource(R.string.import_keep_both)
}

/** Everything the three importers read arrives as one of these, often mislabelled. */
private val MIME_TYPES = arrayOf(
    "application/json",
    "application/yaml",
    "application/x-yaml",
    "text/yaml",
    "text/plain",
    "*/*",
)
