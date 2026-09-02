package com.rio.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.rio.mockserver.core.designsystem.theme.AppTheme
import com.rio.mockserver.core.designsystem.theme.Dimens

/**
 * The "nothing here yet" state, with the action that fixes it.
 *
 * Always with an action where one exists. An empty screen that only says it is empty leaves the
 * user to work out what to do; on a phone, where the relevant button is often behind a menu, that
 * is most of the friction in getting started.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.spaceSmall),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = Dimens.spaceLarge),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Preview
@Composable
internal fun EmptyStatePreview() {
    AppTheme {
        EmptyState(
            title = "No collections yet",
            description = "A collection groups routes under a shared base path, like /api/v1.",
            actionLabel = "New collection",
            onAction = {},
        )
    }
}
