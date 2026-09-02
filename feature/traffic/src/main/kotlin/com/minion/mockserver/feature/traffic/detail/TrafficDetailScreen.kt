package com.minion.mockserver.feature.traffic.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.ui.component.AppScaffold
import com.minion.mockserver.core.ui.component.BodyBlock
import com.minion.mockserver.core.ui.component.EmptyState
import com.minion.mockserver.core.ui.component.FadedDivider
import com.minion.mockserver.core.ui.component.HeaderTable
import com.minion.mockserver.core.ui.component.MethodPill
import com.minion.mockserver.core.ui.component.StatusBadge
import com.minion.mockserver.core.ui.mvi.ObserveAsEvents
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
    AppScaffold(
        title = stringResource(R.string.traffic_detail_title),
        modifier = modifier,
        onBack = onNavigateBack,
    ) { padding ->
        val entry = state.entry
        if (entry == null) {
            EmptyState(
                title = stringResource(R.string.traffic_detail_gone_title),
                description = stringResource(R.string.traffic_detail_gone_body),
                modifier = Modifier.padding(padding),
            )
            return@AppScaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(entry = entry, onIntent = onIntent)

            PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
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
            MethodPill(entry.method)
            StatusBadge(entry.statusCode)
            Text(
                text = stringResource(UiR.string.traffic_duration, entry.durationMillis),
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

        FadedDivider()
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
