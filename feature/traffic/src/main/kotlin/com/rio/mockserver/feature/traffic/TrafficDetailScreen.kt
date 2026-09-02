package com.rio.mockserver.feature.traffic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rio.mockserver.core.designsystem.theme.Dimens
import com.rio.mockserver.core.domain.model.TrafficEntry
import com.rio.mockserver.core.domain.util.JsonTree
import com.rio.mockserver.core.ui.component.EmptyState
import com.rio.mockserver.core.ui.component.MethodBadge
import com.rio.mockserver.core.ui.component.StatusPill
import com.rio.mockserver.core.ui.mvi.ObserveAsEvents
import com.rio.mockserver.core.ui.util.copyToClipboard
import com.rio.mockserver.core.ui.util.shareText
import com.rio.mockserver.core.ui.util.showToast

/** One traffic entry, request and response (SPEC §7.3). */
@Composable
internal fun TrafficDetailScreen(
    onNavigateToRouteEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrafficDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is TrafficDetailEffect.CopiedToClipboard -> {
                context.copyToClipboard(CLIPBOARD_LABEL, effect.text)
                context.showToast(context.getString(R.string.traffic_copied))
            }

            is TrafficDetailEffect.ShareText -> context.shareText(effect.text, effect.subject)
            is TrafficDetailEffect.NavigateToRouteEditor -> onNavigateToRouteEditor(effect.entryId)
        }
    }

    TrafficDetailContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun TrafficDetailContent(
    state: TrafficDetailState,
    onIntent: (TrafficDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = state.entry
    if (entry == null) {
        EmptyState(
            title = stringResource(R.string.traffic_detail_gone_title),
            description = stringResource(R.string.traffic_detail_gone_body),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Header(entry = entry, onIntent = onIntent)

        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            TrafficDetailState.Tab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onIntent(TrafficDetailIntent.SelectTab(tab)) },
                    text = {
                        Text(
                            when (tab) {
                                TrafficDetailState.Tab.REQUEST ->
                                    stringResource(R.string.traffic_detail_request)

                                TrafficDetailState.Tab.RESPONSE ->
                                    stringResource(R.string.traffic_detail_response)
                            },
                        )
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            when (state.selectedTab) {
                TrafficDetailState.Tab.REQUEST -> {
                    HeaderTable(
                        title = stringResource(R.string.traffic_detail_headers),
                        headers = entry.requestHeaders.mapValues { it.value.joinToString(", ") },
                    )
                    BodyBlock(
                        body = entry.requestBody,
                        byteCount = entry.requestByteCount,
                        truncated = entry.requestTruncated,
                        prettyPrint = state.isPrettyPrinted,
                    )
                }

                TrafficDetailState.Tab.RESPONSE -> {
                    HeaderTable(
                        title = stringResource(R.string.traffic_detail_headers),
                        headers = entry.responseHeaders,
                    )
                    BodyBlock(
                        body = entry.responseBody,
                        byteCount = entry.responseBody?.length ?: 0,
                        truncated = entry.responseTruncated,
                        prettyPrint = state.isPrettyPrinted,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                TextButton(onClick = { onIntent(TrafficDetailIntent.TogglePrettyPrint) }) {
                    Text(
                        stringResource(
                            if (state.isPrettyPrinted) R.string.traffic_detail_raw
                            else R.string.traffic_detail_pretty,
                        ),
                    )
                }
                TextButton(onClick = { onIntent(TrafficDetailIntent.CopyBody) }) {
                    Text(stringResource(R.string.traffic_detail_copy_body))
                }
            }
        }
    }
}

@Composable
private fun Header(
    entry: TrafficEntry,
    onIntent: (TrafficDetailIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.space),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            MethodBadge(entry.method)
            StatusPill(entry.statusCode)
            Text(
                text = stringResource(R.string.traffic_duration, entry.durationMillis),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Text(
            text = entry.path,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        )

        entry.matchedRouteName?.let {
            Text(
                text = stringResource(R.string.traffic_detail_matched, it),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // The chaos record is the whole reason an injected 503 is distinguishable from a bug in
        // the rules (SPEC §6.3).
        entry.chaosApplied?.let { chaos ->
            Text(
                text = stringResource(
                    R.string.traffic_detail_chaos,
                    chaos.delayMillis,
                    chaos.failure?.toString() ?: stringResource(R.string.traffic_detail_chaos_none),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (entry.unresolvedPlaceholders.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.traffic_detail_unresolved,
                    entry.unresolvedPlaceholders.joinToString(", "),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            TextButton(onClick = { onIntent(TrafficDetailIntent.CopyAsCurl) }) {
                Text(stringResource(R.string.traffic_detail_copy_curl))
            }
            TextButton(onClick = { onIntent(TrafficDetailIntent.ShareTranscript) }) {
                Text(stringResource(R.string.traffic_detail_share))
            }
        }

        // The prominent action on an unmatched entry: point a client at the phone, let it fail,
        // build the mock from the failure (SPEC §4.5).
        if (entry.isUnmatched) {
            Button(
                onClick = { onIntent(TrafficDetailIntent.CreateRuleFromThis) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.traffic_detail_create_rule))
            }
        }

        HorizontalDivider()
    }
}

@Composable
private fun HeaderTable(title: String, headers: Map<String, String>) {
    if (headers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        headers.forEach { (name, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(WEIGHT_NAME),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(WEIGHT_VALUE),
                )
            }
        }
    }
}

@Composable
private fun BodyBlock(
    body: String?,
    byteCount: Int,
    truncated: Boolean,
    prettyPrint: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
        Text(stringResource(R.string.traffic_detail_body), style = MaterialTheme.typography.titleSmall)
        when {
            // A binary body is reported by size, never rendered as mojibake (SPEC §5.4).
            body == null && byteCount > 0 -> Text(
                text = stringResource(R.string.traffic_detail_binary, byteCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            body.isNullOrBlank() -> Text(
                text = stringResource(R.string.traffic_detail_no_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Text(
                text = if (prettyPrint) JsonTree.prettify(body) else body,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        if (truncated) {
            Text(
                text = stringResource(R.string.traffic_detail_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val CLIPBOARD_LABEL = "Traffic entry"
private const val WEIGHT_NAME = 1f
private const val WEIGHT_VALUE = 2f
