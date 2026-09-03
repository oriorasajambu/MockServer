package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.designsystem.theme.PillShape

/**
 * The "nothing here yet" state, with the action that fixes it.
 *
 * **Flush left, not centred.** Every other screen in this app hangs off a left margin, and a
 * centred block in the middle of one of them reads as a different app's error page. It also makes
 * the text harder to scan: a centred paragraph gives the eye a new left edge on every line.
 *
 * Always with an action where one exists. An empty screen that only says it is empty leaves the
 * user to work out what to do; on a phone, where the relevant button is often behind a menu, that
 * is most of the friction in getting started.
 *
 * @param chips what the screen will accept, when that is the question the empty state raises —
 *   "which formats can I import" is answered here rather than after a rejected file.
 * @param caption the small print under the action: limits, sizes, the thing that would otherwise
 *   surface as an error.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    chips: List<String> = emptyList(),
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    caption: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.space),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                // The heading below it says the same thing in words.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(Dimens.iconLarge)
                    .padding(bottom = Dimens.spaceMedium),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.spaceSmall),
        )

        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
                modifier = Modifier.padding(top = Dimens.spaceMedium),
            ) {
                chips.forEach { chip ->
                    Text(
                        text = chip,
                        style = MockTheme.typography.codeSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .border(
                                BorderStroke(
                                    Dimens.hairline,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                                PillShape,
                            )
                            .padding(
                                horizontal = Dimens.spaceMedium,
                                vertical = Dimens.spaceSmall,
                            ),
                    )
                }
            }
        }

        if (actionLabel != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier
                    .padding(top = Dimens.spaceLarge)
                    .height(Dimens.touchTarget),
                border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSmall),
                    )
                }
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = if (actionIcon != null) {
                        Modifier.padding(start = Dimens.spaceSmall)
                    } else {
                        Modifier
                    },
                )
            }
        }

        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.spaceSmall),
            )
        }
    }
}

@Preview(name = "Empty state", widthDp = 380)
@Composable
internal fun EmptyStatePreview() {
    AppTheme {
        EmptyState(
            title = "Bring routes in from a spec",
            description = "Nothing is written until you have seen what is in the file.",
            icon = MockIcons.FileArrowDown,
            chips = listOf("OpenAPI 3.0", "OpenAPI 3.1", "Postman v2.1", "HAR"),
            actionLabel = "Choose a file",
            actionIcon = MockIcons.FolderOpen,
            onAction = {},
            caption = "JSON or YAML, up to 8 MB.",
        )
    }
}
