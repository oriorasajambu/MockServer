package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme

/**
 * A label on the left, a machine value on the right, a rule beneath.
 *
 * The session counters on the Server tab. The value is monospace because it is a number that
 * changes in place while you watch it — proportional digits shift the column on every tick, and
 * a counter that jitters is one you stop being able to read at a glance.
 *
 * @param emphasised draws the value in the accent. Reserved for a value that is *asking* for
 *   attention rather than merely reporting — an unmatched count above zero, not a request count.
 * @param onClick makes the row a link and adds the chevron. This is how Unmatched becomes a way
 *   into the traffic log: the count is the question, the log is the answer, and the row is the
 *   shortest path between them.
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget)
                .padding(vertical = Dimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MockTheme.typography.codeMedium,
                color = if (emphasised) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    // The row is the click target and the label already names it; announcing
                    // "arrow forward" after "Unmatched, 3" adds nothing.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
        }
        FlatDivider()
    }
}

@Preview(name = "Metric rows", widthDp = 320)
@Composable
internal fun MetricRowPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(Dimens.space)) {
            MetricRow(label = "Uptime", value = "6s")
            MetricRow(label = "Requests", value = "5")
            MetricRow(label = "Unmatched", value = "3", emphasised = true, onClick = {})
        }
    }
}
