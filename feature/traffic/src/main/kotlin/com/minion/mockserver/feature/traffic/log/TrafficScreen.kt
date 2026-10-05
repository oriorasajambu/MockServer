package com.minion.mockserver.feature.traffic.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.designsystem.theme.PillShape
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.EmptyState
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.FlatDivider
import com.minion.mockserver.core.ui.component.SearchBar
import com.minion.mockserver.core.ui.component.SectionTitle
import com.minion.mockserver.core.ui.component.TrafficRow
import com.minion.mockserver.core.ui.error.toMessageRes
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.copyToClipboard
import com.minion.mockserver.core.ui.util.rememberDocumentSaver
import com.minion.mockserver.core.ui.util.shareText
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.traffic.R

/** The Traffic tab (SPEC §7.3). */
@Composable
internal fun TrafficScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToRouteEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrafficViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // `stringResource` wherever the string is known at composition. These are not: the resource
    // id comes from a `DomainError` that does not exist yet, or the arguments arrive with the
    // effect. `LocalResources`, not `context.getString` — a Context read is not invalidated by a
    // configuration change, so text resolved through it goes stale after a locale switch.
    val resources = LocalResources.current

    val copiedMessage = stringResource(R.string.traffic_copied)

    val saver = rememberDocumentSaver(
        onSaved = { fileName ->
            context.showToast(resources.getString(R.string.traffic_har_saved, fileName))
        },
        onFailed = { error -> context.showToast(resources.getString(error.toMessageRes())) },
    )

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is TrafficEffect.CopiedToClipboard -> {
                context.copyToClipboard(CLIPBOARD_LABEL, effect.text)
                context.showToast(copiedMessage)
            }

            is TrafficEffect.ShareText -> context.shareText(effect.text, effect.subject)
            is TrafficEffect.SaveToFile -> saver.save(effect.fileName, effect.content)
            is TrafficEffect.NavigateToDetail -> onNavigateToDetail(effect.entryId)
            is TrafficEffect.NavigateToRouteEditor -> onNavigateToRouteEditor(effect.entryId)
            is TrafficEffect.ShowError ->
                context.showToast(resources.getString(effect.error.toMessageRes()))
        }
    }

    TrafficContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TrafficContent(
    state: TrafficState,
    onIntent: (TrafficIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Following pauses the moment the user scrolls away from the top, and the pill offers the way
    // back. Snapping to the newest entry under a moving finger is the behaviour this avoids.
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(isAtTop) {
        onIntent(if (isAtTop) TrafficIntent.ResumeFollow else TrafficIntent.PauseFollow)
    }
    LaunchedEffect(state.groups.firstOrNull()?.entry?.id, state.isFollowing) {
        if (state.isFollowing && state.groups.isNotEmpty()) listState.animateScrollToItem(0)
    }

    AppScaffold(
        title = stringResource(R.string.traffic_title),
        modifier = modifier,
        actions = {
            if (state.totalCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.traffic_request_count,
                        state.totalCount,
                        state.totalCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                IconButton(onClick = { onIntent(TrafficIntent.ClearLog) }) {
                    Icon(
                        imageVector = MockIcons.Trash,
                        contentDescription = stringResource(R.string.traffic_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSmall),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchAndFilters(state = state, onIntent = onIntent)

            if (state.droppedCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.traffic_dropped,
                        state.droppedCount,
                        state.droppedCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Dimens.space),
                )
            }

            FadedDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isEmpty -> EmptyState(
                        title = stringResource(R.string.traffic_empty_title),
                        description = stringResource(R.string.traffic_empty_body),
                    )

                    state.isFilteredEmpty -> EmptyState(
                        title = stringResource(R.string.traffic_filtered_empty_title),
                        description = stringResource(R.string.traffic_filtered_empty_body),
                        actionLabel = stringResource(R.string.traffic_clear_filters),
                        onAction = { onIntent(TrafficIntent.ClearFilters) },
                    )

                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        // A stable key, so inserting at the top shifts positions without
                        // recomposing every row below it — which at 50 req/s is the difference
                        // between a smooth list and a stuttering one.
                        items(items = state.groups, key = { it.entry.id }) { group ->
                            TrafficRow(
                                entry = group.entry,
                                repeatCount = group.repeatCount,
                                onClick = { onIntent(TrafficIntent.OpenEntry(group.entry.id)) },
                            )
                            FlatDivider()
                        }
                    }
                }

                if (!state.isFollowing && state.newEntryCount > 0) {
                    NewEntriesPill(count = state.newEntryCount, onClick = {
                        onIntent(TrafficIntent.ResumeFollow)
                    })
                }
            }
        }
    }

    if (state.isFilterSheetOpen) {
        FilterSheet(state = state, onIntent = onIntent)
    }
}

/**
 * The search field and the four chips above the log.
 *
 * Unmatched and Chaos are the two questions this screen is opened to answer, so they are chips
 * with their counts on them — the number is the reason to tap. Method and status are refinements
 * with a dozen values between them, so they open a sheet instead of a chip row that scrolls
 * sideways off the edge of a phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchAndFilters(
    state: TrafficState,
    onIntent: (TrafficIntent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            start = Dimens.space,
            end = Dimens.space,
            top = Dimens.spaceSmall,
            bottom = Dimens.spaceSmall,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        SearchBar(
            query = state.searchQuery,
            onQueryChange = { onIntent(TrafficIntent.UpdateSearch(it)) },
            hint = stringResource(R.string.traffic_search_label),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            CountChip(
                label = stringResource(R.string.traffic_unmatched_only),
                count = state.unmatchedCount,
                icon = MockIcons.WarningCircle,
                selected = state.filter.unmatchedOnly == true,
                onClick = {
                    onIntent(
                        TrafficIntent.SetUnmatchedFilter(
                            true.takeIf { state.filter.unmatchedOnly != true },
                        ),
                    )
                },
            )
            CountChip(
                label = stringResource(R.string.traffic_chaos),
                count = state.chaosCount,
                icon = MockIcons.Lightning,
                selected = state.filter.chaosOnly == true,
                onClick = {
                    onIntent(
                        TrafficIntent.SetChaosFilter(
                            true.takeIf { state.filter.chaosOnly != true },
                        ),
                    )
                },
            )
            CountChip(
                label = stringResource(R.string.traffic_filters),
                count = state.filter.methods.size + state.filter.statusClasses.size,
                icon = MockIcons.SlidersHorizontal,
                selected = state.filter.methods.isNotEmpty() ||
                    state.filter.statusClasses.isNotEmpty() ||
                    state.selectedCollectionId != null,
                onClick = { onIntent(TrafficIntent.ToggleFilterSheet) },
            )
        }
    }
}

/** A filter chip that carries how much it would show. Zero hides the number, not the chip. */
@Composable
private fun CountChip(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .background(
                if (selected) accent.copy(alpha = TINT_CONTAINER_ALPHA) else Color.Transparent,
                PillShape,
            )
            .border(
                BorderStroke(
                    Dimens.hairline,
                    if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                ),
                PillShape,
            )
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        Icon(
            imageVector = icon,
            // The label is right beside it.
            contentDescription = null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MockTheme.typography.codeSmall,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Method, status class and collection, behind one chip.
 *
 * These used to be a horizontally scrolling row of a dozen chips above the log, which cost the
 * list a third of the screen and hid whichever filters had scrolled off the right-hand edge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: TrafficState,
    onIntent: (TrafficIntent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(TrafficIntent.ToggleFilterSheet) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.space, end = Dimens.space, bottom = Dimens.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            SectionTitle(stringResource(R.string.traffic_filter_method))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                METHODS.forEach { method ->
                    ToggleChip(
                        label = method,
                        selected = method in state.filter.methods,
                        onClick = { onIntent(TrafficIntent.ToggleMethodFilter(method)) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.traffic_filter_status))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                STATUS_CLASSES.forEach { statusClass ->
                    ToggleChip(
                        label = stringResource(R.string.traffic_status_class, statusClass),
                        selected = statusClass in state.filter.statusClasses,
                        onClick = { onIntent(TrafficIntent.ToggleStatusClassFilter(statusClass)) },
                    )
                }
            }

            if (state.collections.isNotEmpty()) {
                SectionTitle(stringResource(R.string.traffic_filter_collection))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                    // Tapping the selected collection clears the restriction, so there is no
                    // separate "all collections" chip taking up a row of its own.
                    state.collections.forEach { collection ->
                        val isSelected = collection.id == state.selectedCollectionId
                        ToggleChip(
                            label = collection.name,
                            selected = isSelected,
                            onClick = {
                                onIntent(
                                    TrafficIntent.SetCollectionFilter(
                                        collection.id.takeUnless { isSelected },
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            FadedDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                TextButton(onClick = { onIntent(TrafficIntent.ClearFilters) }) {
                    Text(stringResource(R.string.traffic_clear_filters))
                }
                TextButton(onClick = { onIntent(TrafficIntent.SaveHarToFile) }) {
                    Text(stringResource(R.string.traffic_export_har))
                }
                TextButton(onClick = { onIntent(TrafficIntent.ExportHar) }) {
                    Text(stringResource(R.string.traffic_share_har))
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        style = MockTheme.typography.codeSmall,
        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                if (selected) accent.copy(alpha = TINT_CONTAINER_ALPHA) else Color.Transparent,
                PillShape,
            )
            .border(
                BorderStroke(
                    Dimens.hairline,
                    if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                ),
                PillShape,
            )
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall),
    )
}

/** The way back to the top when following is paused and the log has moved on without you. */
@Composable
private fun BoxScope.NewEntriesPill(count: Int, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(pluralStringResource(R.plurals.traffic_new_entries, count, count)) },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(Dimens.spaceSmall),
    )
}

/** The methods worth a filter chip, named by the domain rather than respelled here. */
private val METHODS = listOf(
    HttpMethodFilter.GET,
    HttpMethodFilter.POST,
    HttpMethodFilter.PUT,
    HttpMethodFilter.PATCH,
    HttpMethodFilter.DELETE,
).map { it.name }
private val STATUS_CLASSES = listOf(2, 3, 4, 5)
private const val CLIPBOARD_LABEL = "cURL"

@Preview
@Composable
internal fun TrafficContentPreview() {
    AppTheme {
        TrafficContent(
            state = TrafficState(
                entries = listOf(
                    TrafficEntry(
                        id = "1",
                        receivedAt = 1_767_312_245_000L,
                        clientIp = "192.168.1.22",
                        method = "GET",
                        path = "/api/v1/users/42",
                        statusCode = 200,
                        durationMillis = 8,
                        matchedRouteId = "r1",
                    ),
                    TrafficEntry(
                        id = "2",
                        receivedAt = 1_767_312_246_000L,
                        clientIp = "192.168.1.22",
                        method = "POST",
                        path = "/api/v1/orders",
                        statusCode = 404,
                        durationMillis = 3,
                        outcome = TrafficEntry.Outcome.UNMATCHED,
                    ),
                ),
            ),
            onIntent = {},
        )
    }
}
