package com.minion.mockserver.feature.routes.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.Callout
import com.minion.mockserver.core.ui.component.ConfirmDialog
import com.minion.mockserver.core.ui.component.DialogHeader
import com.minion.mockserver.core.ui.component.EmptyState
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.LabelledTextField
import com.minion.mockserver.core.ui.component.RouteRow
import com.minion.mockserver.core.ui.component.Tone
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.rememberDocumentSaver
import com.minion.mockserver.core.ui.util.shareText
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.routes.R

/** The routes inside one collection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionDetailScreen(
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToImport: (String) -> Unit,
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

            is CollectionDetailEffect.NavigateToImport -> onNavigateToImport(effect.collectionId)
            CollectionDetailEffect.NavigateBack -> onNavigateBack()
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

@OptIn(ExperimentalMaterial3Api::class)
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
        subtitle = stringResource(
            R.string.routes_detail_subtitle,
            state.basePath,
            pluralStringResource(R.plurals.routes_count, state.routes.size, state.routes.size),
        ),
        actions = { CollectionMenu(state = state, onIntent = onIntent) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.space,
                    vertical = Dimens.spaceSmall,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
            ) {
                ExportMenuButton(onIntent = onIntent)

                Text(
                    text = stringResource(R.string.routes_export_note),
                    style = MaterialTheme.typography.bodySmall,
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
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            FadedDivider()

            if (state.isEmpty) {
                EmptyState(
                    title = stringResource(R.string.routes_empty_title),
                    description = stringResource(R.string.routes_empty_body),
                    actionLabel = stringResource(R.string.routes_new),
                    onAction = { onIntent(CollectionDetailIntent.NewRoute) },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = Dimens.space)) {
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
                    }

                    // Ends the scroll rather than floating over the last row, matching the Routes
                    // tab. On a long collection the FAB used to cover a switch.
                    item(key = NEW_ROUTE_KEY) {
                        NewRouteButton(
                            onClick = { onIntent(CollectionDetailIntent.NewRoute) },
                            modifier = Modifier.padding(
                                horizontal = Dimens.space,
                                vertical = Dimens.spaceSmall,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (state.renameDraft != null) {
        RenameDialog(
            draft = state.renameDraft,
            basePath = state.basePath,
            error = state.renameError,
            onIntent = onIntent,
        )
    }

    if (state.isConfirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.routes_delete_title, state.collectionName),
            body = pluralStringResource(
                R.plurals.routes_delete_body,
                state.routes.size,
                state.routes.size,
                state.basePath,
            ),
            confirmLabel = stringResource(R.string.routes_delete_confirm),
            confirmIcon = MockIcons.Trash,
            icon = MockIcons.Trash,
            tone = Tone.Error,
            calloutText = stringResource(R.string.routes_delete_callout),
            onConfirm = { onIntent(CollectionDetailIntent.ConfirmDelete) },
            onDismiss = { onIntent(CollectionDetailIntent.DismissDelete) },
        )
    }
}

/**
 * The overflow in the top bar: everything that acts on the collection rather than on one route.
 *
 * Grouped by how much they undo. Rename and import change what is here; the bulk toggle changes
 * what answers; delete ends it. The dividers are what stop a thumb sliding from "turn all routes
 * off" into "delete collection".
 */
@Composable
private fun CollectionMenu(
    state: CollectionDetailState,
    onIntent: (CollectionDetailIntent) -> Unit,
) {
    IconButton(onClick = { onIntent(CollectionDetailIntent.OpenMenu) }) {
        Icon(
            imageVector = MockIcons.DotsThreeVertical,
            contentDescription = stringResource(R.string.routes_more_actions),
        )
    }
    DropdownMenu(
        expanded = state.isMenuOpen,
        onDismissRequest = { onIntent(CollectionDetailIntent.DismissMenu) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        MenuItem(
            label = stringResource(R.string.routes_menu_rename),
            icon = MockIcons.PencilSimple,
            onClick = { onIntent(CollectionDetailIntent.StartRename) },
        )
        MenuItem(
            label = stringResource(R.string.routes_menu_import),
            icon = MockIcons.FileArrowDown,
            onClick = { onIntent(CollectionDetailIntent.ImportIntoCollection) },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        MenuItem(
            label = pluralStringResource(
                if (state.willEnableAll) R.plurals.routes_menu_enable_all else R.plurals.routes_menu_disable_all,
                state.routes.size,
                state.routes.size,
            ),
            icon = MockIcons.ToggleLeft,
            onClick = {
                onIntent(CollectionDetailIntent.SetAllRoutesEnabled(state.willEnableAll))
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        MenuItem(
            label = stringResource(R.string.routes_menu_delete),
            icon = MockIcons.Trash,
            tint = MaterialTheme.colorScheme.error,
            onClick = { onIntent(CollectionDetailIntent.StartDelete) },
        )
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    DropdownMenuItem(
        text = { Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                // The label is the item; the glyph is decoration beside it.
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        },
    )
}

/**
 * One Export button over the four things exporting can mean.
 *
 * The screen used to carry two share/save pairs stacked above the routes, which put four buttons
 * of equal weight above the content they were about. Format and destination are both choices, so
 * they belong in a menu rather than in the layout.
 */
@Composable
private fun ExportMenuButton(onIntent: (CollectionDetailIntent) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(Dimens.touchTarget),
            border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
        ) {
            Icon(
                imageVector = MockIcons.Export,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSmall),
            )
            Text(
                text = stringResource(R.string.routes_export),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Dimens.spaceSmall),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ExportFormat.entries.forEach { format ->
                ExportDestination.entries.forEach { destination ->
                    MenuItem(
                        label = stringResource(
                            if (destination == ExportDestination.SHARE) {
                                R.string.routes_export_share
                            } else {
                                R.string.routes_export_save
                            },
                            stringResource(
                                if (format == ExportFormat.NATIVE_JSON) {
                                    R.string.routes_format_json
                                } else {
                                    R.string.routes_format_postman
                                },
                            ),
                        ),
                        icon = if (destination == ExportDestination.SHARE) {
                            MockIcons.ShareNetwork
                        } else {
                            MockIcons.FloppyDisk
                        },
                        onClick = {
                            expanded = false
                            onIntent(CollectionDetailIntent.Export(format, destination))
                        },
                    )
                }
            }
        }
    }
}

/** Screen 1r. A name, what it is for, and what renaming will *not* change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDialog(
    draft: String,
    basePath: String,
    error: DomainError?,
    onIntent: (CollectionDetailIntent) -> Unit,
) {
    BasicAlertDialog(onDismissRequest = { onIntent(CollectionDetailIntent.DismissRename) }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(Dimens.space),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
            ) {
                DialogHeader(
                    title = stringResource(R.string.routes_rename_title),
                    icon = MockIcons.PencilSimple,
                    tone = Tone.Accent,
                )

                LabelledTextField(
                    value = draft,
                    onValueChange = {
                        onIntent(CollectionDetailIntent.UpdateRenameDraft(it))
                    },
                    label = stringResource(R.string.routes_rename_label),
                    hint = stringResource(R.string.routes_rename_help),
                )

                error?.let {
                    Text(
                        text = stringResource(it.toMessageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // The one thing a rename dialog is most likely to be misunderstood as doing.
                Callout(
                    text = stringResource(R.string.routes_rename_callout, basePath),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
                ) {
                    OutlinedButton(
                        onClick = { onIntent(CollectionDetailIntent.DismissRename) },
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.touchTarget),
                        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = stringResource(R.string.collections_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                    OutlinedButton(
                        onClick = { onIntent(CollectionDetailIntent.ConfirmRename) },
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.touchTarget),
                        enabled = draft.isNotBlank(),
                        border = BorderStroke(
                            Dimens.hairline,
                            MaterialTheme.colorScheme.primary,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.collections_save),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The design's end-of-list action, in the same outlined style as the Routes tab's. */
@Composable
private fun NewRouteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            text = stringResource(R.string.routes_new),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(start = Dimens.spaceSmall),
        )
    }
}

/** Stable, so the button does not remount as routes change. */
private const val NEW_ROUTE_KEY = "new-route"

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
