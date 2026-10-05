package com.minion.mockserver.feature.traffic.detail

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.BodyBlock
import com.minion.mockserver.core.ui.component.Callout
import com.minion.mockserver.core.ui.component.EditorTab
import com.minion.mockserver.core.ui.component.EditorTabs
import com.minion.mockserver.core.ui.component.EmptyState
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.HeaderTable
import com.minion.mockserver.core.ui.component.MethodPill
import com.minion.mockserver.core.ui.component.SectionTitle
import com.minion.mockserver.core.ui.component.StatusBadge
import com.minion.mockserver.core.ui.component.Tone
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
import com.minion.mockserver.core.ui.util.DateUtil
import com.minion.mockserver.core.ui.util.copyToClipboard
import com.minion.mockserver.core.ui.util.shareText
import com.minion.mockserver.core.ui.util.showToast
import com.minion.mockserver.feature.traffic.R
import com.minion.mockserver.core.ui.R as UiR

/** One traffic entry, request and response (SPEC §7.3). */
@Composable
internal fun TrafficDetailScreen(
    onNavigateToRouteEditor: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrafficDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.traffic_copied)

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is TrafficDetailEffect.CopiedToClipboard -> {
                context.copyToClipboard(CLIPBOARD_LABEL, effect.text)
                context.showToast(copiedMessage)
            }

            is TrafficDetailEffect.ShareText -> context.shareText(effect.text, effect.subject)
            is TrafficDetailEffect.NavigateToRouteEditor -> onNavigateToRouteEditor(effect.entryId)
        }
    }

    TrafficDetailContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun TrafficDetailContent(
    state: TrafficDetailState,
    onIntent: (TrafficDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val entry = state.entry

    AppScaffold(
        title = stringResource(R.string.traffic_detail_title),
        modifier = modifier,
        onBack = onNavigateBack,
        subtitle = entry?.let { DateUtil.formatTime(it.receivedAt) },
        actions = {
            if (entry != null) {
                IconButton(onClick = { onIntent(TrafficDetailIntent.ShareTranscript) }) {
                    Icon(
                        imageVector = MockIcons.ShareNetwork,
                        contentDescription = stringResource(R.string.traffic_detail_share),
                        modifier = Modifier.size(Dimens.iconSmall),
                    )
                }
            }
        },
    ) { padding ->
        if (entry == null) {
            EmptyState(
                title = stringResource(R.string.traffic_detail_gone_title),
                description = stringResource(R.string.traffic_detail_gone_body),
                modifier = Modifier.padding(padding),
            )
            return@AppScaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Summary(entry = entry, tab = state.selectedTab, onIntent = onIntent)

            EditorTabs(
                tabs = listOf(
                    EditorTab(stringResource(R.string.traffic_detail_request)),
                    EditorTab(stringResource(R.string.traffic_detail_response)),
                ),
                selectedIndex = state.selectedTab.ordinal,
                onSelect = { index ->
                    onIntent(
                        TrafficDetailIntent.SelectTab(TrafficDetailState.Tab.entries[index]),
                    )
                },
            )

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
                        BodyHeader(state = state, onIntent = onIntent)
                        BodyBlock(
                            body = entry.requestBody,
                            byteCount = entry.requestByteCount,
                            truncated = entry.requestTruncated,
                            prettyPrint = state.isPrettyPrinted,
                        )
                    }

                    TrafficDetailState.Tab.RESPONSE -> {
                        // Which side of the exchange this came from is not obvious from the
                        // headers, and on an unmatched entry it is the answer to "why is this
                        // body not the one I wrote".
                        if (entry.isUnmatched) {
                            Callout(
                                text = stringResource(R.string.traffic_detail_fallback_note),
                            )
                        }
                        HeaderTable(
                            title = stringResource(R.string.traffic_detail_headers),
                            headers = entry.responseHeaders,
                        )
                        BodyHeader(state = state, onIntent = onIntent)
                        BodyBlock(
                            body = entry.responseBody,
                            byteCount = entry.responseBody?.length ?: 0,
                            truncated = entry.responseTruncated,
                            prettyPrint = state.isPrettyPrinted,
                        )
                    }
                }
            }
        }
    }
}

/** "Body" with the two things you can do to it, matching the route editor's body header. */
@Composable
private fun BodyHeader(
    state: TrafficDetailState,
    onIntent: (TrafficDetailIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionTitle(stringResource(R.string.traffic_detail_body))
        Row {
            TextButton(onClick = { onIntent(TrafficDetailIntent.TogglePrettyPrint) }) {
                Text(
                    text = stringResource(
                        if (state.isPrettyPrinted) {
                            R.string.traffic_detail_raw
                        } else {
                            R.string.traffic_detail_pretty
                        },
                    ),
                    maxLines = 1,
                )
            }
            TextButton(onClick = { onIntent(TrafficDetailIntent.CopyBody) }) {
                Text(stringResource(R.string.traffic_detail_copy_body), maxLines = 1)
            }
        }
    }
}

/**
 * What this exchange was, above the tabs.
 *
 * Above rather than inside them, because it is the part you read first and it does not change
 * when you switch sides — only the byte count does, and that follows the tab so the number always
 * describes what is on screen.
 */
@Composable
private fun Summary(
    entry: TrafficEntry,
    tab: TrafficDetailState.Tab,
    onIntent: (TrafficDetailIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.space),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            MethodPill(entry.method)
            StatusBadge(entry.statusCode)
            Text(
                text = when (tab) {
                    TrafficDetailState.Tab.REQUEST -> stringResource(
                        R.string.traffic_detail_bytes_in,
                        entry.durationMillis,
                        entry.requestByteCount,
                    )

                    TrafficDetailState.Tab.RESPONSE -> stringResource(
                        R.string.traffic_detail_bytes_out,
                        entry.durationMillis,
                        entry.responseBody?.length ?: 0,
                    )
                },
                style = MockTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Text(
            text = entry.path,
            style = MockTheme.typography.codeLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        entry.matchedRouteName?.let {
            Text(
                text = stringResource(R.string.traffic_detail_matched, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entry.isUnmatched) {
            Callout(
                text = stringResource(R.string.traffic_detail_unmatched_note),
                tone = Tone.Error,
            )
        }

        // The chaos record is the whole reason an injected 503 is distinguishable from a bug in
        // the rules (SPEC §6.3).
        entry.chaosApplied?.let { chaos ->
            Callout(
                text = stringResource(
                    R.string.traffic_detail_chaos,
                    chaos.delayMillis,
                    chaos.failure?.toString() ?: stringResource(R.string.traffic_detail_chaos_none),
                ),
                tone = Tone.Accent,
            )
        }

        if (entry.unresolvedPlaceholders.isNotEmpty()) {
            Callout(
                text = stringResource(
                    R.string.traffic_detail_unresolved,
                    entry.unresolvedPlaceholders.joinToString(", "),
                ),
                tone = Tone.Error,
            )
        }

        // The prominent action on an unmatched entry: point a client at the phone, let it fail,
        // build the mock from the failure (SPEC §4.5).
        if (entry.isUnmatched) {
            OutlinedButton(
                onClick = { onIntent(TrafficDetailIntent.CreateRuleFromThis) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.touchTarget),
                border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = MockIcons.PlusCircle,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
                Text(
                    text = stringResource(R.string.traffic_detail_create_rule),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Dimens.spaceSmall),
                )
            }
        }

        OutlinedButton(
            onClick = { onIntent(TrafficDetailIntent.CopyAsCurl) },
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.touchTarget),
            border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
        ) {
            Icon(
                imageVector = MockIcons.TerminalWindow,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSmall),
            )
            Text(
                text = stringResource(R.string.traffic_detail_copy_curl),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = Dimens.spaceSmall),
            )
        }
    }
}

private const val CLIPBOARD_LABEL = "Traffic entry"

@Preview(name = "Traffic detail — unmatched")
@Composable
internal fun TrafficDetailContentPreview() {
    AppTheme {
        TrafficDetailContent(
            state = TrafficDetailState(
                entry = TrafficEntry(
                    id = "1",
                    receivedAt = 0L,
                    clientIp = "192.168.1.22",
                    method = "POST",
                    path = "/api/v1/orders",
                    requestHeaders = mapOf("Content-Type" to listOf("application/json")),
                    requestBody = """{"sku":"A-1","qty":2}""",
                    statusCode = 404,
                    responseHeaders = mapOf("Content-Type" to "application/json; charset=utf-8"),
                    responseBody = """{"error":"No mock route matched"}""",
                    durationMillis = 7L,
                    unresolvedPlaceholders = listOf("path.orderId"),
                ),
            ),
            onIntent = {},
        )
    }
}

@Preview(name = "Traffic detail — entry dropped")
@Composable
internal fun TrafficDetailContentMissingPreview() {
    AppTheme {
        TrafficDetailContent(state = TrafficDetailState(entry = null), onIntent = {})
    }
}
