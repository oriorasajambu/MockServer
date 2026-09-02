package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.ui.R

/**
 * One exportable thing, and the two places it can go (SPEC §9.2, §7.4).
 *
 * Share and save are both offered everywhere this appears, because they answer different
 * questions: a share sends a collection to a colleague, a file keeps it where it can be found
 * again and re-imported.
 *
 * **`FlowRow`, not `Row`.** A `Row` given more than it can fit does not overflow — it squeezes,
 * and a `TextButton` squeezed to sixty density-independent pixels breaks its label mid-word down
 * a four-line column. Wrapping to the next line is the only behaviour here that stays legible at
 * every font scale and in every language, and this row is one long label away from being too wide
 * in all of them.
 *
 * @param label what is being exported — the format, or the whole database
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportActionRow(
    label: String,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceQuarter),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        TextButton(onClick = onShare) { Text(stringResource(R.string.export_share)) }
        TextButton(onClick = onSave) { Text(stringResource(R.string.export_save)) }
    }
}

@Preview
@Composable
internal fun ExportActionRowPreview() {
    AppTheme {
        Column {
            ExportActionRow(label = "Native JSON", onShare = {}, onSave = {})
            ExportActionRow(label = "Postman v2.1", onShare = {}, onSave = {})
        }
    }
}
