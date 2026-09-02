package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * The icon-and-title row at the top of every dialog and bottom sheet.
 *
 * A tinted circle carrying an icon, then the title. [tone] selects the tint, which is the whole
 * reason this is one component: rename and delete share a layout and a rhythm, and differ only in
 * the colour of that circle. Two separate headers would eventually differ in their padding too.
 *
 * The title is capped at one line. A dialog title that wrapped to three lines pushes the actions
 * below the fold on a short screen, and a truncated name is recoverable where a hidden Confirm
 * button is not.
 */
@Composable
fun DialogHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.dialogIconSize)
                .background(tone.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                // Decorative: the title beside it already says what this dialog is, and a screen
                // reader announcing "delete icon, Delete route?" is noise.
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Dialog headers", widthDp = 320)
@Composable
internal fun DialogHeaderPreview() {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space)) {
            DialogHeader(title = "Rename collection", icon = Icons.Filled.Edit, tone = Tone.Accent)
            DialogHeader(title = "Delete route?", icon = Icons.Filled.Delete, tone = Tone.Error)
        }
    }
}
