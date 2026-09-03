package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme

/**
 * A confirmation that names what it acted on.
 *
 * "Copied" answers whether the tap registered; it does not answer *which* address, and on the
 * server screen there are three of them a thumb-width apart. The snackbar carries the value
 * itself, in the machine face, so the confirmation is also the receipt.
 *
 * A snackbar rather than a toast. A toast is drawn by the system outside the app's theme — light
 * grey on a dark app — cannot be styled, and on newer Android is clipped to a single short line
 * that would truncate the address this exists to show.
 *
 * @param detail the value acted on, set in mono. Omitted for messages with nothing to name.
 */
@Composable
fun AppSnackbar(
    data: SnackbarData,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = MockIcons.CheckCircleFill,
    detail: String? = data.visuals.message,
) {
    Snackbar(
        modifier = modifier.padding(Dimens.spaceMedium),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            Icon(
                imageVector = icon,
                // The title beside it says the same thing.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSmall),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceQuarter)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MockTheme.typography.codeSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        // The tail of an address is the port, which is the half most likely to
                        // be wrong and the half a head-ellipsis would keep.
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        }
    }
}
