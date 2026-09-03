package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.ui.R

/**
 * One collection, with enough of its contents to answer the usual question without opening it.
 *
 * The Routes tab used to be a list of names and counts, which meant the answer to "is the route I
 * am debugging on, and what does it return" was always one tap away. The preview rows put method,
 * path, status and switch on the overview, so the tab is useful without navigating.
 *
 * A disabled collection shows no preview at all. Nothing in it is answering, so a list of routes
 * with their own switches would invite toggling one and expecting a response — the master switch
 * is the only control that matters in that state, and hiding the rest says so.
 *
 * @param previewRoutes the first few routes, already truncated by the caller. This composable does
 *   not decide how many; that is a screen-level choice about density.
 */
@Composable
fun CollectionCard(
    collection: MockCollection,
    routeCount: Int,
    needsBodyCount: Int,
    previewRoutes: List<MockRoute>,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleRoute: (String, Boolean) -> Unit,
    onEdit: () -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onOpen)
            .padding(Dimens.spaceMedium),
    ) {
        Header(
            collection = collection,
            routeCount = routeCount,
            needsBodyCount = needsBodyCount,
            onToggle = onToggle,
            onEdit = onEdit,
        )

        if (collection.isEnabled && previewRoutes.isNotEmpty()) {
            FadedDivider(modifier = Modifier.padding(vertical = Dimens.spaceSmall))

            previewRoutes.forEach { route ->
                RoutePreviewRow(
                    route = route,
                    onToggle = { onToggleRoute(route.id, it) },
                )
            }

            FadedDivider(modifier = Modifier.padding(vertical = Dimens.spaceSmall))

            ViewAllFooter(routeCount = routeCount, onViewAll = onViewAll)
        }
    }
}

@Composable
private fun Header(
    collection: MockCollection,
    routeCount: Int,
    needsBodyCount: Int,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // One line. A long collection name in a row that also holds a switch has nowhere to
            // wrap to, and wrapping there stacks it into a narrow column.
            Text(
                text = collection.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.collections_card_caption,
                    collection.basePath,
                    pluralStringResource(
                        R.plurals.collections_route_count,
                        routeCount,
                        routeCount,
                    ),
                ),
                style = MockTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            // The visible gap an import leaves rather than a body full of "string" (SPEC §9.1).
            // Not in the design, which never drew an imported collection — but it is a fact about
            // the data that the overview is the right place to admit.
            if (needsBodyCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.collections_needs_body,
                        needsBodyCount,
                        needsBodyCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // Not in the design, and it has to stay. The detail screen's overflow menu now carries
        // Rename, which is all the design's dialog (1r) changes — it says outright that the path
        // prefix does not move. But a collection also owns its base path and its CORS config
        // (SPEC 8.7), and this dialog is the only place either can be edited after creation. The
        // twenty-one screens never gave those a home; until they do, removing this would make
        // per-collection CORS unreachable.
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = MockIcons.PencilSimple,
                contentDescription = stringResource(R.string.collections_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        }
        MockSwitch(checked = collection.isEnabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ViewAllFooter(routeCount: Int, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewAll)
            .heightIn(min = Dimens.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.collections_view_all,
                routeCount,
                routeCount,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = MockIcons.CaretRight,
            // The label is the link; the caret only says it leads somewhere.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconSmall),
        )
    }
}

@Preview(name = "Collection cards", widthDp = 360)
@Composable
internal fun CollectionCardPreview() {
    val routes = listOf(
        MockRoute(
            id = "1",
            collectionId = "c",
            name = "List users",
            pathPattern = "/users/{userId}",
            response = MockResponse(statusCode = 200),
        ),
        MockRoute(
            id = "2",
            collectionId = "c",
            name = "Create order",
            pathPattern = "/orders",
            response = MockResponse(statusCode = 201),
        ),
    )
    AppTheme {
        Column(
            modifier = Modifier.padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            CollectionCard(
                collection = MockCollection(
                    id = "1",
                    name = "Api",
                    basePath = "/api/v1",
                    createdAt = 0L,
                ),
                routeCount = 6,
                needsBodyCount = 0,
                previewRoutes = routes,
                onOpen = {},
                onToggle = {},
                onToggleRoute = { _, _ -> },
                onEdit = {},
                onViewAll = {},
            )
            CollectionCard(
                collection = MockCollection(
                    id = "2",
                    name = "Legacy v0",
                    basePath = "/v0",
                    createdAt = 0L,
                    isEnabled = false,
                ),
                routeCount = 2,
                needsBodyCount = 0,
                previewRoutes = routes,
                onOpen = {},
                onToggle = {},
                onToggleRoute = { _, _ -> },
                onEdit = {},
                onViewAll = {},
            )
        }
    }
}
