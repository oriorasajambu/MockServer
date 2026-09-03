package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.ui.R
import com.minion.mockserver.core.ui.util.DateUtil

/**
 * One request in the live log (SPEC §7.3).
 *
 * The densest read in the app, and the one that has to be scannable at speed: a rail, a wash, the
 * method, the path, and underneath it the single line that says what actually happened.
 *
 * The rail, the wash and that line all come from one decision — [outcomeOf] — rather than from
 * three separate conditionals. Three would eventually disagree, and a row tinted like a failure
 * whose text says it matched is worse than either signal alone.
 *
 * The path is **middle**-ellipsised. The distinguishing part of a mock URL is almost always the
 * tail: `/api/v1/users/42` and `/api/v1/users/43` differ in the last two characters, and a row
 * that cuts there shows two identical lines.
 *
 * @param repeatCount how many consecutive identical requests this row stands for. 1 hides the
 *   badge. See the log screen for why the collapsing happens there and not here.
 */
@Composable
fun TrafficRow(
    entry: TrafficEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    repeatCount: Int = 1,
) {
    val outcome = entry.outcomeOf()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(outcome.tint.copy(alpha = outcome.washAlpha))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.railWidth)
                .fillMaxHeight()
                .background(outcome.tint),
        )

        MethodPill(
            method = entry.method,
            modifier = Modifier.padding(start = Dimens.space - Dimens.railWidth),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Dimens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceQuarter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
            ) {
                Text(
                    text = entry.path,
                    style = MockTheme.typography.codeMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (repeatCount > 1) {
                    RepeatBadge(count = repeatCount)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
            ) {
                if (outcome.icon != null) {
                    Icon(
                        imageVector = outcome.icon,
                        // The line beside it says the same thing in words.
                        contentDescription = null,
                        tint = outcome.tint,
                        modifier = Modifier.size(Dimens.iconSmall),
                    )
                }
                Text(
                    text = outcome.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = outcome.labelColour,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }

        Column(
            modifier = Modifier.padding(end = Dimens.space),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceQuarter),
        ) {
            StatusBadge(entry.statusCode)
            Text(
                text = stringResource(
                    R.string.traffic_timing,
                    entry.durationMillis,
                    DateUtil.formatTimeShort(entry.receivedAt),
                ),
                style = MockTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Everything the row's appearance depends on, decided once.
 *
 * Chaos is checked before the outcome enum, because an injected failure is recorded as a match —
 * it *did* hit a route, the server just broke the answer on purpose. Reading it as an ordinary
 * match would lose the one distinction SPEC §6.3 exists to preserve: "the server is broken"
 * versus "I asked for this".
 */
private data class RowOutcome(
    val tint: Color,
    val washAlpha: Float,
    val label: String,
    val labelColour: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

@Composable
private fun TrafficEntry.outcomeOf(): RowOutcome {
    val chaos = chaosApplied
    if (chaos != null) {
        return RowOutcome(
            tint = MockTheme.colors.chaosInjected,
            washAlpha = WASH_ALPHA,
            label = stringResource(R.string.traffic_chaos_injected, statusCode),
            labelColour = MockTheme.colors.chaosInjected,
            icon = MockIcons.Lightning,
        )
    }

    return when (outcome) {
        TrafficEntry.Outcome.MATCHED -> RowOutcome(
            tint = MockTheme.colors.railActive,
            // The ordinary case gets no wash at all. Most of the log is this, and a list where
            // every row is tinted has no signal in it.
            washAlpha = 0f,
            label = matchedRouteName ?: stringResource(R.string.traffic_matched),
            labelColour = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TrafficEntry.Outcome.UNMATCHED -> RowOutcome(
            tint = MockTheme.colors.unmatched,
            washAlpha = WASH_ALPHA,
            label = stringResource(R.string.traffic_no_route),
            labelColour = MockTheme.colors.unmatched,
        )

        // The remaining outcomes are the request never reaching a route: refused, rejected,
        // preflighted, oversized, or thrown. All of them are "not your rule's fault", so they
        // take the neutral rail and say which one they were.
        else -> RowOutcome(
            tint = MockTheme.colors.railOff,
            washAlpha = 0f,
            label = outcomeLabel(),
            labelColour = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The one-word reason a row never reached a route. */
@Composable
private fun TrafficEntry.outcomeLabel(): String = when (outcome) {
    TrafficEntry.Outcome.REJECTED -> stringResource(R.string.traffic_rejected)
    TrafficEntry.Outcome.PAUSED -> stringResource(R.string.traffic_paused)
    TrafficEntry.Outcome.PREFLIGHT -> stringResource(R.string.traffic_preflight)
    TrafficEntry.Outcome.TOO_LARGE -> stringResource(R.string.traffic_too_large)
    TrafficEntry.Outcome.ERRORED -> stringResource(R.string.traffic_errored)
    else -> stringResource(R.string.traffic_matched)
}

/** Faint enough to read as a tint on the row rather than as a block of colour in the list. */
private const val WASH_ALPHA = TINT_CONTAINER_ALPHA / 2f

@Preview(name = "Traffic rows", widthDp = 380)
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
                    durationMillis = 36L,
                    matchedRouteName = "GET /users/{userId}",
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
                repeatCount = 4,
            )
        }
    }
}
