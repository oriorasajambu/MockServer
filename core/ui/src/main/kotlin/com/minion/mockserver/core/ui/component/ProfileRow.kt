package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA
import com.minion.mockserver.core.ui.R

/**
 * One chaos profile: whether it is the active one, what it is called, and what it does.
 *
 * Selecting is the row's primary action, so the whole row is the radio — a 20dp target beside
 * text you have to read anyway is the wrong shape for the thing you do most.
 *
 * The active row takes the accent rail and a tinted ground. Only one profile applies at a time
 * (SPEC §6), and a list where the live one is marked solely by a filled circle makes you hunt
 * for it every time you come back.
 *
 * @param description generated from the profile's own odds — see [chaosDescription]. Passed in
 *   rather than derived here so this stays a row and not a rules engine.
 * @param onEdit `null` for a profile that cannot be edited, which is how None appears.
 */
@Composable
fun ProfileRow(
    name: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = TINT_CONTAINER_ALPHA)
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.railWidth)
                .fillMaxHeight()
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                ),
        )

        Icon(
            imageVector = if (selected) MockIcons.CheckCircleFill else MockIcons.Circle,
            // The row is the radio and announces its own selected state; the glyph repeats it.
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(start = Dimens.space - Dimens.railWidth)
                .size(Dimens.iconSmall),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = Dimens.spaceMedium,
                    top = Dimens.spaceMedium,
                    bottom = Dimens.spaceMedium,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceQuarter),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = Dimens.touchTarget),
            ) {
                Icon(
                    imageVector = MockIcons.PencilSimple,
                    contentDescription = stringResource(R.string.chaos_edit_profile),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
        }
    }
}

@Preview(name = "Profile rows", widthDp = 380)
@Composable
internal fun ProfileRowPreview() {
    AppTheme {
        Column {
            ProfileRow(
                name = "None",
                description = "Every response goes out clean.",
                selected = true,
                onSelect = {},
            )
            ProfileRow(
                name = "Flaky wifi",
                description = "About 1 request in 10 fails. Everything waits 400 ms.",
                selected = false,
                onSelect = {},
                onEdit = {},
            )
        }
    }
}
