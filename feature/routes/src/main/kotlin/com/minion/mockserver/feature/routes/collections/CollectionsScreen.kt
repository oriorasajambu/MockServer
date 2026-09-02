package com.minion.mockserver.feature.routes.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.CollectionRow
import com.minion.mockserver.core.ui.component.EmptyState
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.routes.R

/** The Routes tab's top level. */
@Composable
internal fun CollectionsScreen(
    onNavigateToCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // `stringResource` wherever the string is known at composition. These are not: the resource
    // id comes from a `DomainError` that does not exist yet, or the arguments arrive with the
    // effect. `LocalResources`, not `context.getString` — a Context read is not invalidated by a
    // configuration change, so text resolved through it goes stale after a locale switch.
    val resources = LocalResources.current

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is CollectionsEffect.NavigateToCollection -> onNavigateToCollection(effect.id)
            is CollectionsEffect.ShowError ->
                context.showToast(resources.getString(effect.error.toMessageRes()))
        }
    }

    CollectionsContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun CollectionsContent(
    state: CollectionsState,
    onIntent: (CollectionsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = stringResource(R.string.collections_title),
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(CollectionsIntent.NewCollection) },
                text = { Text(stringResource(R.string.collections_new)) },
                icon = {},
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                title = stringResource(R.string.collections_empty_title),
                description = stringResource(R.string.collections_empty_body),
                actionLabel = stringResource(R.string.collections_new),
                onAction = { onIntent(CollectionsIntent.NewCollection) },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(items = state.collections, key = { it.id }) { collection ->
                    CollectionRow(
                        collection = collection,
                        routeCount = state.routeCounts[collection.id] ?: 0,
                        needsBodyCount = state.needsBodyCounts[collection.id] ?: 0,
                        onOpen = { onIntent(CollectionsIntent.OpenCollection(collection.id)) },
                        onToggle = { onIntent(CollectionsIntent.ToggleCollection(collection.id, it)) },
                        onEdit = { onIntent(CollectionsIntent.EditCollection(collection.id)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    state.editing?.let { draft ->
        CollectionDialog(draft = draft, onIntent = onIntent)
    }
}

@Composable
private fun CollectionDialog(
    draft: CollectionsState.Draft,
    onIntent: (CollectionsIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onIntent(CollectionsIntent.DismissDraft) },
        title = {
            Text(
                stringResource(
                    if (draft.isNew) R.string.collections_new else R.string.collections_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onIntent(CollectionsIntent.UpdateDraftName(it)) },
                    label = { Text(stringResource(R.string.collections_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.basePath,
                    onValueChange = { onIntent(CollectionsIntent.UpdateDraftBasePath(it)) },
                    label = { Text(stringResource(R.string.collections_base_path_label)) },
                    supportingText = { Text(stringResource(R.string.collections_base_path_help)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.collections_cors))
                    Switch(
                        checked = draft.corsEnabled,
                        onCheckedChange = { onIntent(CollectionsIntent.UpdateDraftCors(it)) },
                    )
                }
                if (draft.corsEnabled) {
                    OutlinedTextField(
                        value = draft.allowedOrigins,
                        onValueChange = { onIntent(CollectionsIntent.UpdateDraftOrigins(it)) },
                        label = { Text(stringResource(R.string.collections_origins_label)) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.collections_credentials))
                        Switch(
                            checked = draft.allowCredentials,
                            onCheckedChange = { onIntent(CollectionsIntent.UpdateDraftCredentials(it)) },
                        )
                    }
                }
                // The guard rail: `allowCredentials` with origin `*` is rejected by every browser,
                // and the console message says nothing about credentials (SPEC §8.7).
                draft.error?.let { error ->
                    Text(
                        text = stringResource(error.toMessageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(CollectionsIntent.SaveDraft) }) {
                Text(stringResource(R.string.collections_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(CollectionsIntent.DismissDraft) }) {
                Text(stringResource(R.string.collections_cancel))
            }
        },
    )
}

@Preview
@Composable
internal fun CollectionsContentPreview() {
    AppTheme {
        CollectionsContent(
            state = CollectionsState(
                collections = listOf(
                    MockCollection(id = "1", name = "Orders API", basePath = "/api/v1"),
                    MockCollection(id = "2", name = "Legacy", basePath = "/v0", isEnabled = false),
                ),
                routeCounts = mapOf("1" to 12, "2" to 3),
                needsBodyCounts = mapOf("1" to 2),
                isLoading = false,
            ),
            onIntent = {},
        )
    }
}
