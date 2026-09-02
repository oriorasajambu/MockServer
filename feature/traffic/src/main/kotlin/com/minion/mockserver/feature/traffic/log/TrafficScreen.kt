package com.minion.mockserver.feature.traffic.log

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.EmptyState
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
    LaunchedEffect(state.entries.firstOrNull()?.id, state.isFollowing) {
        if (state.isFollowing && state.entries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    AppScaffold(
        title = stringResource(R.string.traffic_title),
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                        // A stable key, so inserting at the top shifts positions without recomposing
                        // every row below it — which at 50 req/s is the difference between a smooth
                        // list and a stuttering one.
                        items(items = state.entries, key = { it.id }) { entry ->
                            TrafficRow(
                                entry = entry,
                                onClick = { onIntent(TrafficIntent.OpenEntry(entry.id)) },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                if (!state.isFollowing && state.newEntryCount > 0) {
                    AssistChip(
                        onClick = { onIntent(TrafficIntent.ResumeFollow) },
                        label = {
                            Text(
                                pluralStringResource(
                                    R.plurals.traffic_new_entries,
                                    state.newEntryCount,
                                    state.newEntryCount,
                                ),
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(Dimens.spaceSmall),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilters(
    state: TrafficState,
    onIntent: (TrafficIntent) -> Unit,
) {
    Column(modifier = Modifier.padding(Dimens.spaceSmall)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onIntent(TrafficIntent.UpdateSearch(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.traffic_search_label)) },
            singleLine = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = Dimens.spaceSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            METHODS.forEach { method ->
                FilterChip(
                    selected = method in state.filter.methods,
                    onClick = { onIntent(TrafficIntent.ToggleMethodFilter(method)) },
                    label = { Text(method) },
                )
            }
            STATUS_CLASSES.forEach { statusClass ->
                FilterChip(
                    selected = statusClass in state.filter.statusClasses,
                    onClick = { onIntent(TrafficIntent.ToggleStatusClassFilter(statusClass)) },
                    label = { Text(stringResource(R.string.traffic_status_class, statusClass)) },
                )
            }
            FilterChip(
                selected = state.filter.unmatchedOnly == true,
                onClick = {
                    onIntent(
                        TrafficIntent.SetUnmatchedFilter(
                            if (state.filter.unmatchedOnly == true) null else true,
                        ),
                    )
                },
                label = { Text(stringResource(R.string.traffic_unmatched_only)) },
            )

            // Tapping the selected collection clears the restriction, so the chip row needs no
            // separate "all collections" chip taking up a phone's worth of horizontal space.
            state.collections.forEach { collection ->
                val isSelected = collection.id == state.selectedCollectionId
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onIntent(
                            TrafficIntent.SetCollectionFilter(
                                collection.id.takeUnless { isSelected },
                            ),
                        )
                    },
                    label = { Text(collection.name) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            TextButton(onClick = { onIntent(TrafficIntent.SaveHarToFile) }) {
                Text(stringResource(R.string.traffic_export_har))
            }
            TextButton(onClick = { onIntent(TrafficIntent.ExportHar) }) {
                Text(stringResource(R.string.traffic_share_har))
            }
            TextButton(onClick = { onIntent(TrafficIntent.ClearLog) }) {
                Text(stringResource(R.string.traffic_clear))
            }
        }
    }
}

/**
 * One row: method, path, status, duration, and the two icons that matter.
 *
 * The path is middle-ellipsised because the distinguishing part of a mock URL is almost always
 * the end — `/api/v1/users/42` and `/api/v1/users/43` differ in the last two characters, and
 * end-ellipsis would render them identical.
 */
private val METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
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
