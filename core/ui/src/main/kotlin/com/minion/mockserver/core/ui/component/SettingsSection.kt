package com.minion.mockserver.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * One collapsible block of settings, with its current value on the header.
 *
 * The summary is the point. Settings was one long scroll of every control at once, which meant
 * answering "what port is this on" required finding the field among thirty others. Collapsed
 * sections with their values on the right make the whole screen scannable in one pass, and only
 * the section being changed has to be open.
 *
 * The chevron rotates rather than swapping glyphs, so the same shape tracks the same state and
 * the transition says which way the section is going.
 *
 * @param summary the current value, shown only while collapsed — once the section is open the
 *   controls themselves say it, and repeating it puts two sources of truth on one screen.
 */
@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) CHEVRON_OPEN_DEGREES else 0f,
        label = "chevron",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = Dimens.touchTarget)
                .padding(horizontal = Dimens.space, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            Icon(
                imageVector = icon,
                // The title beside it names the section.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSmall),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (summary != null && !isExpanded) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Icon(
                imageVector = MockIcons.CaretDown,
                // The row's expanded state is announced by the row itself.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Dimens.iconSmall)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // An inset ground, so an open section reads as a panel rather than as the
                    // page continuing into the next heading.
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(Dimens.space),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
                content = content,
            )
        }
    }
}

/** Pointing down when closed, up when open. */
private const val CHEVRON_OPEN_DEGREES = 180f

@Preview(name = "Settings sections", widthDp = 380)
@Composable
internal fun SettingsSectionPreview() {
    AppTheme {
        Column {
            SettingsSection(
                title = "Server",
                icon = MockIcons.HardDrives,
                isExpanded = false,
                onToggle = {},
                summary = "8080",
            ) {}
            SettingsSection(
                title = "Chaos",
                icon = MockIcons.Lightning,
                isExpanded = true,
                onToggle = {},
                summary = "None · seed 1",
            ) {
                Text("Section contents", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
