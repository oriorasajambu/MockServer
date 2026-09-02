package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.ui.R

/**
 * One rule in a collection.
 *
 * The three chips are the row's reason for existing. **Shadowed** means this rule can never win
 * whatever the request (SPEC §4.3) — a rule sitting in the list looking correct and never firing
 * is the failure nobody finds unaided. **Needs body** is an import gap, and **chaos** says this
 * route overrides the global profile, which is the difference between "the server is broken" and
 * "I asked for this".
 */
@Composable
fun RouteRow(
    route: MockRoute,
    isShadowed: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(Dimens.space),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        // The pattern gets a line to itself, sharing it only with the badge and the status it
        // answers with. Competing with the name, a duplicate button and a switch on one line left
        // it about ninety dips wide, which turns `/api/v1/users/{userId}` into `/api/…erId}`.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            MethodBadge(route.method.name)
            Text(
                text = route.pathPattern,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(PATH_WEIGHT),
            )
            StatusPill(route.response.statusCode)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            Column(modifier = Modifier.weight(PATH_WEIGHT)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf)) {
                    if (isShadowed) {
                        AssistChip(
                            onClick = onOpen,
                            label = { Text(stringResource(R.string.routes_shadowed)) },
                        )
                    }
                    if (route.needsBody) {
                        AssistChip(
                            onClick = onOpen,
                            label = { Text(stringResource(R.string.routes_needs_body)) },
                        )
                    }
                    if (route.hasChaosOverride) {
                        AssistChip(
                            onClick = onOpen,
                            label = { Text(stringResource(R.string.routes_chaos)) },
                        )
                    }
                }
            }

            IconButton(onClick = onDuplicate) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.routes_duplicate),
                )
            }
            Switch(checked = route.isEnabled, onCheckedChange = onToggle)
        }
    }
}

/** The pattern takes what the badge and the status pill leave. */
private const val PATH_WEIGHT = 1f

@Preview
@Composable
internal fun RouteRowPreview() {
    AppTheme {
        Column {
            RouteRow(
                route = MockRoute(
                    id = "1",
                    collectionId = "c",
                    name = "Get user",
                    pathPattern = "/users/{id}",
                ),
                isShadowed = false,
                onOpen = {},
                onToggle = {},
                onDuplicate = {},
            )
            RouteRow(
                route = MockRoute(
                    id = "2",
                    collectionId = "c",
                    name = "List users",
                    pathPattern = "/users",
                    needsBody = true,
                ),
                isShadowed = true,
                onOpen = {},
                onToggle = {},
                onDuplicate = {},
            )
        }
    }
}
