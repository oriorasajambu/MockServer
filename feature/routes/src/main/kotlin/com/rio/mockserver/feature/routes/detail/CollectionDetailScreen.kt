package com.rio.mockserver.feature.routes.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rio.mockserver.core.designsystem.theme.AppTheme
import com.rio.mockserver.core.designsystem.theme.Dimens
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.ui.component.EmptyState
import com.rio.mockserver.core.ui.component.MethodBadge
import com.rio.mockserver.core.ui.component.StatusPill
import com.rio.mockserver.core.ui.error.toMessageRes
import com.rio.mockserver.core.ui.mvi.ObserveAsEvents
import com.rio.mockserver.core.ui.util.shareText
import com.rio.mockserver.core.ui.util.showToast
import com.rio.mockserver.feature.routes.R

/** The routes inside one collection. */
@Composable
internal fun CollectionDetailScreen(
    onNavigateToEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var lossWarnings by remember { mutableStateOf<List<String>?>(null) }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is CollectionDetailEffect.NavigateToEditor -> onNavigateToEditor(effect.routeId)
            is CollectionDetailEffect.ShareText -> context.shareText(effect.text, effect.subject)
            is CollectionDetailEffect.ConfirmLossyExport -> lossWarnings = effect.warnings
            is CollectionDetailEffect.ShowError ->
                context.showToast(context.getString(effect.error.toMessageRes()))
        }
    }

    CollectionDetailContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)

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
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                        text = stringResource(R.string.routes_needs_body_banner, state.needsBodyCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                    TextButton(onClick = { onIntent(CollectionDetailIntent.ExportJson) }) {
                        Text(stringResource(R.string.routes_export_json))
                    }
                    TextButton(onClick = { onIntent(CollectionDetailIntent.ExportPostman) }) {
                        Text(stringResource(R.string.routes_export_postman))
                    }
                }
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
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteRow(
    route: MockRoute,
    isShadowed: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(Dimens.space),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        MethodBadge(route.method.name)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.pathPattern,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = route.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
                if (isShadowed) {
                    AssistChip(
                        onClick = onOpen,
                        label = { Text(stringResource(R.string.routes_shadowed)) },
                    )
                }
                if (route.needsBody) {
                    AssistChip(
                        onClick = onOpen,
                        label = { Text(stringResource(R.string.routes_needs_body)) },
                    )
                }
                if (route.hasChaosOverride) {
                    AssistChip(
                        onClick = onOpen,
                        label = { Text(stringResource(R.string.routes_chaos)) },
                    )
                }
            }
        }

        StatusPill(route.response.statusCode)
        TextButton(onClick = onDuplicate) { Text(stringResource(R.string.routes_duplicate)) }
        Switch(checked = route.isEnabled, onCheckedChange = onToggle)
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
