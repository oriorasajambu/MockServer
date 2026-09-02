package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * A titled section of a long form that can be folded away.
 *
 * Collapsible sections in one scroll, rather than a wizard: the whole rule stays reviewable, and
 * a section is folded because the user is done with it rather than because a step decided they
 * were (SPEC §10.2). The expanded set lives in the caller's state, so it survives a rotation.
 *
 * The divider is part of the section, not the caller's job — a form where three sections have a
 * rule under them and the fourth does not is how these drift.
 */
@Composable
fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        TextButton(onClick = onToggle) {
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        if (isExpanded) {
            content()
        }
        FadedDivider()
    }
}

@Preview
@Composable
internal fun CollapsibleSectionPreview() {
    AppTheme {
        Column {
            CollapsibleSection(title = "Request", isExpanded = true, onToggle = {}) {
                Text("GET /users/{id}")
            }
            CollapsibleSection(title = "Matchers", isExpanded = false, onToggle = {}) {
                Text("hidden")
            }
        }
    }
}
