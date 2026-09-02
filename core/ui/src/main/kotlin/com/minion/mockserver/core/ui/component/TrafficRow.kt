package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.ui.R
import com.minion.mockserver.core.ui.util.DateUtil

/**
 * One request in the live log (SPEC §7.3).
 *
 * The path is **middle**-ellipsised, not end-ellipsised, because the distinguishing part of a
 * mock URL is almost always the tail: `/api/v1/users/42` and `/api/v1/users/43` differ in the last
 * two characters, and a row that cuts there shows two identical lines.
 *
 * The outcome word is the row's whole diagnostic value. Without it an injected 503 and a genuine
 * mismatch look the same, which is the failure mode SPEC §6.3 exists to prevent.
 */
@Composable
fun TrafficRow(
    entry: TrafficEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space, vertical = Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        MethodPill(entry.method)

        Column(modifier = Modifier.weight(PATH_WEIGHT)) {
            Text(
                text = entry.path,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                Text(
                    text = DateUtil.formatTime(entry.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.traffic_duration, entry.durationMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.outcomeLabel()?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        StatusBadge(entry.statusCode)
    }
}

/** The one-word reason a row is not an ordinary match, or `null` when it is. */
@Composable
private fun TrafficEntry.outcomeLabel(): String? = when (outcome) {
    TrafficEntry.Outcome.MATCHED -> chaosApplied?.let { stringResource(R.string.traffic_chaos) }
    TrafficEntry.Outcome.UNMATCHED -> stringResource(R.string.traffic_unmatched)
    TrafficEntry.Outcome.REJECTED -> stringResource(R.string.traffic_rejected)
    TrafficEntry.Outcome.PAUSED -> stringResource(R.string.traffic_paused)
    TrafficEntry.Outcome.PREFLIGHT -> stringResource(R.string.traffic_preflight)
    TrafficEntry.Outcome.TOO_LARGE -> stringResource(R.string.traffic_too_large)
    TrafficEntry.Outcome.ERRORED -> stringResource(R.string.traffic_errored)
}

/** The path column takes whatever the badge and the pill leave. */
private const val PATH_WEIGHT = 1f

@Preview
@Composable
internal fun TrafficRowPreview() {
    AppTheme {
        Column {
            TrafficRow(
                entry = TrafficEntry(
                    id = "1",
                    receivedAt = 0L,
                    clientIp = "192.168.1.22",
                    method = "GET",
                    path = "/api/v1/users/42",
                    statusCode = 200,
                    durationMillis = 6L,
                ),
                onClick = {},
            )
            TrafficRow(
                entry = TrafficEntry(
                    id = "2",
                    receivedAt = 0L,
                    clientIp = "192.168.1.22",
                    method = "POST",
                    path = "/api/v1/orders",
                    statusCode = 404,
                    durationMillis = 3L,
                    outcome = TrafficEntry.Outcome.UNMATCHED,
                ),
                onClick = {},
            )
        }
    }
}
