package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.ui.R

/**
 * A modal that states a consequence and offers two ways out.
 *
 * One component for both the destructive and the benign case, with [tone] as the only difference
 * — it drives the header tint, the icon and the confirm button's colour together. Two separate
 * dialogs would eventually differ in their padding and their button order too, and a delete
 * confirm that does not look like the rest of the app is one people click through without reading.
 *
 * The actions are stretched to equal width rather than right-aligned. At the bottom of a phone
 * screen that puts Cancel under the left thumb and Confirm under the right, instead of stacking
 * both destinations within a few millimetres of each other.
 *
 * @param calloutText an extra line for what the body cannot recover from — "nothing is exported
 *   yet". Rendered as a [Callout] in the same tone, or omitted entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    icon: ImageVector = MockIcons.WarningCircle,
    confirmIcon: ImageVector? = null,
    calloutText: String? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(Dimens.space),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
            ) {
                DialogHeader(title = title, icon = icon, tone = tone)

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (calloutText != null) {
                    Callout(text = calloutText, tone = tone)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
                ) {
                    DialogAction(
                        label = stringResource(R.string.dialog_cancel),
                        tint = MaterialTheme.colorScheme.onSurface,
                        border = MaterialTheme.colorScheme.outline,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    DialogAction(
                        label = confirmLabel,
                        icon = confirmIcon,
                        tint = tone.content,
                        border = tone.content,
                        container = tone.container,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    label: String,
    tint: Color,
    border: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = Color.Transparent,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dimens.touchTarget),
        border = BorderStroke(Dimens.hairline, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = tint,
        ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                // The label is right beside it.
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = if (icon != null) Modifier.padding(start = Dimens.spaceSmall) else Modifier,
        )
    }
}

@Preview(name = "Confirm dialog")
@Composable
internal fun ConfirmDialogPreview() {
    AppTheme {
        ConfirmDialog(
            title = "Delete Api?",
            body = "Its 6 routes go with it. Requests to /api/v1 will fall through to the " +
                "unmatched response.",
            confirmLabel = "Delete",
            confirmIcon = MockIcons.Trash,
            tone = Tone.Error,
            icon = MockIcons.Trash,
            calloutText = "Nothing is exported yet. Save a copy first if you might want it back.",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
