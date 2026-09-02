package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.ui.R

/**
 * One collection in the list: what it is called, what it prefixes, and how much is in it.
 *
 * The needs-body count is the part worth keeping visible. An import that found no example
 * response creates the route anyway (SPEC §9.1), and a collection that silently answers `{}`
 * to half its endpoints is worse than one that says so on the row.
 */
@Composable
fun CollectionRow(
    collection: MockCollection,
    routeCount: Int,
    needsBodyCount: Int,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(Dimens.space),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Column(modifier = Modifier.weight(NAME_WEIGHT)) {
            // One line each: a long collection name in a row that also holds Edit and a switch
            // has nowhere to wrap to, and wrapping there stacks it into a narrow column.
            Text(
                text = collection.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = collection.basePath,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.collections_route_count,
                    routeCount,
                    routeCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The visible gap an import leaves rather than a body full of "string" (SPEC §9.1).
            if (needsBodyCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.collections_needs_body,
                        needsBodyCount,
                        needsBodyCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.collections_edit)) }
        MockSwitch(checked = collection.isEnabled, onCheckedChange = onToggle)
    }
}

/** The name column takes what the edit button and the switch leave. */
private const val NAME_WEIGHT = 1f

@Preview
@Composable
internal fun CollectionRowPreview() {
    AppTheme {
        CollectionRow(
            collection = MockCollection(
                id = "1",
                name = "Orders API",
                basePath = "/api/v1",
                createdAt = 0L,
            ),
            routeCount = 12,
            needsBodyCount = 3,
            onOpen = {},
            onToggle = {},
            onEdit = {},
        )
    }
}
