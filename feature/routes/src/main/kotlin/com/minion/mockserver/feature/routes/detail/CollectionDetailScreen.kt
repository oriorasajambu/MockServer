package com.minion.mockserver.feature.routes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.EmptyState
import com.minion.mockserver.core.ui.component.ExportActionRow
import com.minion.mockserver.core.ui.component.FlatDivider
import com.minion.mockserver.core.ui.component.RouteRow
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.rememberDocumentSaver
import com.minion.mockserver.core.ui.util.shareText
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.routes.R

/** The routes inside one collection. */
@Composable
internal fun CollectionDetailScreen(
    onNavigateToEditor: (String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // `stringResource` wherever the string is known at composition. These are not: the resource
    // id comes from a `DomainError` that does not exist yet, or the arguments arrive with the
    // effect. `LocalResources`, not `context.getString` — a Context read is not invalidated by a
    // configuration change, so text resolved through it goes stale after a locale switch.
    val resources = LocalResources.current
    var lossWarnings by remember { mutableStateOf<List<String>?>(null) }

    val saver = rememberDocumentSaver(
        onSaved = { fileName ->
            context.showToast(resources.getString(R.string.routes_export_saved, fileName))
        },
        onFailed = { error -> context.showToast(resources.getString(error.toMessageRes())) },
    )

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is CollectionDetailEffect.NavigateToEditor -> onNavigateToEditor(effect.routeId)
            is CollectionDetailEffect.ShareText -> context.shareText(effect.text, effect.subject)
            is CollectionDetailEffect.SaveToFile -> saver.save(effect.fileName, effect.content)
            is CollectionDetailEffect.ConfirmLossyExport -> lossWarnings = effect.warnings
            is CollectionDetailEffect.ShowError ->
                context.showToast(resources.getString(effect.error.toMessageRes()))
        }
    }

    CollectionDetailContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )

    lossWarnings?.let { warnings ->
        AlertDialog(
            onDismissRequest = { lossWarnings = null },
            title = { Text(stringResource(R.string.routes_postman_loss_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                    warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lossWarnings = null
                        viewModel.confirmPostmanExport()
                    },
                ) {
                    Text(stringResource(R.string.routes_export_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { lossWarnings = null }) {
                    Text(stringResource(R.string.routes_cancel))
                }
            },
        )
    }
}

@Composable
internal fun CollectionDetailContent(
    state: CollectionDetailState,
    onIntent: (CollectionDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    AppScaffold(
        // The collection's own name, so the back stack reads as a path rather than as three
        // screens all called "Routes".
        title = state.collectionName,
        modifier = modifier,
        onBack = onNavigateBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(CollectionDetailIntent.NewRoute) },
                text = { Text(stringResource(R.string.routes_new)) },
                icon = {},
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = Dimens.space, vertical = Dimens.spaceSmall)) {
                Text(
                    text = state.basePath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.needsBodyCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.routes_needs_body_banner,
                            state.needsBodyCount,
                            state.needsBodyCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                ExportActionRow(
                    label = stringResource(R.string.routes_export_json),
                    onShare = {
                        onIntent(
                            CollectionDetailIntent.Export(
                                ExportFormat.NATIVE_JSON,
                                ExportDestination.SHARE,
                            ),
                        )
                    },
                    onSave = {
                        onIntent(
                            CollectionDetailIntent.Export(
                                ExportFormat.NATIVE_JSON,
                                ExportDestination.FILE,
                            ),
                        )
                    },
                )
                ExportActionRow(
                    label = stringResource(R.string.routes_export_postman),
                    onShare = {
                        onIntent(
                            CollectionDetailIntent.Export(
                                ExportFormat.POSTMAN,
                                ExportDestination.SHARE,
                            ),
                        )
                    },
                    onSave = {
                        onIntent(
                            CollectionDetailIntent.Export(
                                ExportFormat.POSTMAN,
                                ExportDestination.FILE,
                            ),
                        )
                    },
                )
            }

            if (state.isEmpty) {
                EmptyState(
                    title = stringResource(R.string.routes_empty_title),
                    description = stringResource(R.string.routes_empty_body),
                    actionLabel = stringResource(R.string.routes_new),
                    onAction = { onIntent(CollectionDetailIntent.NewRoute) },
                )
            } else {
                LazyColumn {
                    items(items = state.routes, key = { it.id }) { route ->
                        RouteRow(
                            route = route,
                            isShadowed = state.shadowedBy.containsKey(route.id),
                            onOpen = { onIntent(CollectionDetailIntent.OpenRoute(route.id)) },
                            onToggle = {
                                onIntent(CollectionDetailIntent.ToggleRoute(route.id, it))
                            },
                            onDuplicate = {
                                onIntent(CollectionDetailIntent.DuplicateRoute(route.id))
                            },
                        )
                        FlatDivider()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
internal fun CollectionDetailContentPreview() {
    AppTheme {
        CollectionDetailContent(
            state = CollectionDetailState(
                collectionName = "Orders API",
                basePath = "/api/v1",
                routes = listOf(
                    MockRoute(id = "1", collectionId = "c", name = "Get user", pathPattern = "/users/{id}"),
                    MockRoute(id = "2", collectionId = "c", name = "List users", pathPattern = "/users", needsBody = true),
                ),
                shadowedBy = mapOf("2" to "1"),
                isLoading = false,
            ),
            onIntent = {},
        )
    }
}
