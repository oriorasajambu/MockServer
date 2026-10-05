package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.ui.R

/**
 * One route inside a collection card: what it answers, with what, and whether it is on.
 *
 * The densest thing in the app — a method, a path, an optional chaos note, a status and a switch
 * on one 44dp line — which is why every part of it is fixed-width except the path. The path takes
 * what is left and middle-ellipsises, because the tail of a route pattern is the part that
 * identifies it and the head is usually a prefix shared with everything above it.
 *
 * An off route is struck through *and* dimmed. Either alone is ambiguous at this size: dimming on
 * its own reads as a rendering artefact in a list, and a strikethrough on its own is easy to miss
 * on a 12sp mono line.
 *
 * @param enabled the *collection's* state, not the route's. A route inside a disabled collection
 *   is not answering either, so its switch stops being actionable — but the value it would take
 *   when the collection comes back on is still worth seeing.
 */
@Composable
fun RoutePreviewRow(
    route: MockRoute,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.routePreviewRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        MethodPill(method = route.method.name)

        Text(
            text = route.pathPattern,
            style = MockTheme.typography.codeMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (route.isEnabled) null else TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(if (route.isEnabled) 1f else DISABLED_ALPHA),
        )

        route.chaosOverride?.delay?.let { delay ->
            ChaosDelay(delay)
        }

        StatusBadge(statusCode = route.response.statusCode)

        MockSwitch(
            checked = route.isEnabled,
            onCheckedChange = onToggle,
            size = MockSwitchSize.Small,
            enabled = enabled,
        )
    }
}

/**
 * The lightning mark and the delay a route adds on its own, independent of the global profile.
 *
 * Only the delay, of everything a chaos override can carry. It is the one failure mode with a
 * number small enough to fit here and concrete enough to be worth the space — a probability of an
 * injected 500 means nothing at a glance, but "250ms" explains a slow response immediately.
 */
@Composable
private fun ChaosDelay(delay: DelaySpec) {
    val label = when (delay) {
        is DelaySpec.Fixed -> stringResource(R.string.route_preview_delay, delay.millis)
        is DelaySpec.Random ->
            stringResource(R.string.route_preview_delay_range, delay.minMillis, delay.maxMillis)

        DelaySpec.None -> return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        Icon(
            imageVector = MockIcons.Lightning,
            // The delay beside it is the content; the mark only says which kind of number it is.
            contentDescription = null,
            tint = MockTheme.colors.chaosInjected,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Text(
            text = label,
            style = MockTheme.typography.codeSmall,
            color = MockTheme.colors.chaosInjected,
            maxLines = 1,
        )
    }
}

@Preview(name = "Route preview rows", widthDp = 360)
@Composable
internal fun RoutePreviewRowPreview() {
    AppTheme {
        Column {
            RoutePreviewRow(
                route = MockRoute(
                    id = "1",
                    collectionId = "c",
                    name = "List users",
                    pathPattern = "/users/{userId}",
                    response = MockResponse(statusCode = 200),
                ),
                onToggle = {},
            )
            RoutePreviewRow(
                route = MockRoute(
                    id = "2",
                    collectionId = "c",
                    name = "Delete session",
                    pathPattern = "/sessions/current",
                    response = MockResponse(statusCode = 204),
                    isEnabled = false,
                ),
                onToggle = {},
            )
        }
    }
}
