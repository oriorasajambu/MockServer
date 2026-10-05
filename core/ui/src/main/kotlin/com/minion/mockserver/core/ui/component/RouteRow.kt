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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.designsystem.theme.PillShape
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.ui.R

/**
 * One rule in a collection.
 *
 * A leading rail, the pills and the path, then a caption for everything that needs saying about
 * this rule in particular. The rail is the fastest thing to scan down a long collection precisely
 * because it carries no text and sits at a fixed x: three states, three steps of one ramp, read
 * without reading.
 *
 * The captions are the row's reason for existing. **Shadowed** means this rule can never win
 * whatever the request (SPEC §4.3) — a rule sitting in the list looking correct and never firing
 * is the failure nobody finds unaided. **Needs body** is an import gap, and a chaos override says
 * this route ignores the global profile, which is the difference between "the server is broken"
 * and "I asked for this".
 */
@Composable
fun RouteRow(
    route: MockRoute,
    isShadowed: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    // Reorder handles replace the duplicate button and the switch rather than joining them. Four
    // controls beside a path leaves it about ninety dips wide, which is where it starts shredding.
    val isReordering = onMoveUp != null || onMoveDown != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(route = route)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    // The rail lives *inside* the gutter, so the text after it lands on the same
                    // 16dp as everything else on the screen. Padding it by a round step instead
                    // left every route two dips left of the header above it.
                    start = Dimens.space - Dimens.railWidth,
                    end = Dimens.spaceSmall,
                    top = Dimens.spaceMedium,
                    bottom = Dimens.spaceMedium,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
            ) {
                MethodPill(route.method.name)
                StatusBadge(route.response.statusCode)
                route.chaosOverride?.delay?.takeIf { it != DelaySpec.None }?.let { delay ->
                    Text(
                        text = delay.label(),
                        style = MockTheme.typography.codeSmall,
                        color = MockTheme.colors.chaosInjected,
                        maxLines = 1,
                    )
                }
                if (!route.isEnabled) {
                    Text(
                        text = stringResource(R.string.routes_off),
                        style = MockTheme.typography.codeSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // The pattern gets a line to itself. Sharing one with the pills, a duplicate button
            // and a switch left it about ninety dips wide, which turns `/api/v1/users/{userId}`
            // into `/api/…erId}`.
            Text(
                text = route.pathPattern,
                style = MockTheme.typography.codeMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (route.isEnabled) null else TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )

            Caption(route = route, isShadowed = isShadowed)
        }

        if (isReordering) {
            IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                Icon(
                    imageVector = MockIcons.CaretUp,
                    contentDescription = stringResource(R.string.routes_move_up),
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
            IconButton(
                onClick = { onMoveDown?.invoke() },
                enabled = onMoveDown != null,
                modifier = Modifier.padding(end = Dimens.spaceSmall),
            ) {
                Icon(
                    imageVector = MockIcons.CaretDown,
                    contentDescription = stringResource(R.string.routes_move_down),
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
        } else {
            IconButton(onClick = onDuplicate) {
                Icon(
                    imageVector = MockIcons.Copy,
                    contentDescription = stringResource(R.string.routes_duplicate),
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
            MockSwitch(
                checked = route.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(end = Dimens.spaceSmall),
            )
        }
    }
}

/**
 * The 2dp leading mark: on, wildcard, or off.
 *
 * Wildcard gets its own step because a pattern that can swallow requests meant for its neighbours
 * behaves differently from an ordinary rule, and that is worth seeing before you go looking for
 * why a specific path never matched.
 */
@Composable
private fun Rail(route: MockRoute) {
    val colour: Color = when {
        !route.isEnabled -> MockTheme.colors.railOff
        route.pathPattern.contains('*') -> MockTheme.colors.railWildcard
        else -> MockTheme.colors.railActive
    }
    Box(
        modifier = Modifier
            .width(Dimens.railWidth)
            .fillMaxHeight()
            .background(colour),
    )
}

/** The route's name, plus any flag that changes how it behaves. */
@Composable
private fun Caption(route: MockRoute, isShadowed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        Text(
            text = route.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isShadowed) {
            RouteTag(stringResource(R.string.routes_shadowed), MaterialTheme.colorScheme.error)
        }
        if (route.needsBody) {
            RouteTag(stringResource(R.string.routes_needs_body), MaterialTheme.colorScheme.primary)
        }
        if (route.hasChaosOverride) {
            RouteTag(stringResource(R.string.routes_chaos), MockTheme.colors.chaosInjected)
        }
    }
}

/** A small tinted label. Not an `AssistChip`: these are read, never tapped. */
@Composable
private fun RouteTag(label: String, tint: Color) {
    Text(
        text = label,
        style = MockTheme.typography.codeSmall,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .background(tint.copy(alpha = TINT_CONTAINER_ALPHA), PillShape)
            .padding(horizontal = Dimens.spaceSmall, vertical = Dimens.spaceQuarter),
    )
}

/** "250ms", or "100–400ms" for a random spread. */
@Composable
private fun DelaySpec.label(): String = when (this) {
    is DelaySpec.Fixed -> stringResource(R.string.route_preview_delay, millis)
    is DelaySpec.Random ->
        stringResource(R.string.route_preview_delay_range, minMillis, maxMillis)

    DelaySpec.None -> ""
}

@Preview(name = "Route rows", widthDp = 380)
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
                    response = MockResponse(statusCode = 200),
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
                    pathPattern = "/users/**",
                    needsBody = true,
                    response = MockResponse(statusCode = 200),
                ),
                isShadowed = true,
                onOpen = {},
                onToggle = {},
                onDuplicate = {},
            )
            RouteRow(
                route = MockRoute(
                    id = "3",
                    collectionId = "c",
                    name = "Delete session",
                    pathPattern = "/sessions/current",
                    isEnabled = false,
                    response = MockResponse(statusCode = 204),
                ),
                isShadowed = false,
                onOpen = {},
                onToggle = {},
                onDuplicate = {},
            )
        }
    }
}
