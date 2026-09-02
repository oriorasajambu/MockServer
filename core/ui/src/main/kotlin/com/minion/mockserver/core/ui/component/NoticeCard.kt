package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * Something the user should know, with the one thing they can do about it.
 *
 * The shape every in-app notice in this app takes: a line naming what is true, a sentence saying
 * why it matters, and an action that leads somewhere it can be changed. A notice with no action
 * is a complaint, so [actionLabel] is required; [onDismiss] is not, because some conditions are
 * not the user's to wave away.
 *
 * Deliberately not a dialog. These describe a state the app is already in and can keep running
 * in — the server without its notification, the process the system killed overnight — and
 * interrupting someone to tell them about something that already happened buys nothing.
 */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = body, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
                TextButton(onClick = onAction) { Text(actionLabel) }
                if (dismissLabel != null && onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                }
            }
        }
    }
}

@Preview
@Composable
internal fun NoticeCardPreview() {
    AppTheme {
        NoticeCard(
            title = "The server was stopped in the background",
            body = "Battery optimisation can stop a long session while the screen is off.",
            actionLabel = "Open battery settings",
            onAction = {},
            dismissLabel = "Dismiss",
            onDismiss = {},
        )
    }
}
