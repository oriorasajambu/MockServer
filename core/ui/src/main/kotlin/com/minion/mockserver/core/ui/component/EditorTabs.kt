package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/** One tab: what it is called, and whether anything under it has been touched. */
@Immutable
data class EditorTab(val label: String, val hasChanges: Boolean = false)

/**
 * The route editor's section switcher.
 *
 * The editor used to be one scroll of four collapsible sections, which meant the response body —
 * the thing being edited most of the time — sat below however much request configuration happened
 * to be expanded. Three tabs put each part one tap away at a fixed position.
 *
 * The dot is why this is not a plain `TabRow`. Splitting a form across tabs hides changes: edit
 * the delay, switch to Response, and there is nothing to say the Chaos tab is no longer what it
 * was. The dot is that reminder, and it is what makes Save safe to press without visiting every
 * tab first.
 *
 * @param dimmed drawn faint but still tappable, for a route being created — the other tabs work,
 *   they just have nothing worth reading until the path exists.
 */
@Composable
fun EditorTabs(
    tabs: List<EditorTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                color = MaterialTheme.colorScheme.primary,
            )
        },
        divider = { FadedDivider() },
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Tab(
                selected = selected,
                onClick = { onSelect(index) },
                modifier = Modifier.alpha(if (dimmed && !selected) DISABLED_ALPHA else 1f),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = Dimens.spaceMedium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    if (tab.hasChanges) {
                        // No glow: an unsaved edit is a fact to notice, not an alarm.
                        StateDot(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Preview(name = "Editor tabs", widthDp = 380)
@Composable
internal fun EditorTabsPreview() {
    AppTheme {
        EditorTabs(
            tabs = listOf(
                EditorTab("Request"),
                EditorTab("Response"),
                EditorTab("Chaos", hasChanges = true),
            ),
            selectedIndex = 1,
            onSelect = {},
        )
    }
}
