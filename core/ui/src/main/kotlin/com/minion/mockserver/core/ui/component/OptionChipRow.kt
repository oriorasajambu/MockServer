package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.designsystem.theme.PillShape
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA

/**
 * A row of mutually exclusive chips, with an optional escape hatch at the end.
 *
 * Serves the two places the editor asks a closed question with a long tail: which method, and
 * which status code. The common answers are one tap; [otherLabel] opens the way to the rest
 * rather than pretending the list is exhaustive.
 *
 * A `FlowRow`, not a `Row`. Six status chips do not fit across a phone, and a `Row` short of
 * width squeezes its children rather than overflowing — which turns `PATCH` into three stacked
 * letters instead of moving it to the next line.
 *
 * Labels are monospace because every one of them is a value the client will see on the wire.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> OptionChipRow(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    otherLabel: String? = null,
    onOther: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        options.forEach { option ->
            OptionChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
        if (otherLabel != null && onOther != null) {
            OptionChip(
                label = otherLabel,
                // "Other" is never the selected state: picking it leads somewhere that then makes
                // one of the real chips selected, or sets a value none of them show.
                selected = selected != null && options.none { it == selected },
                onClick = onOther,
            )
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        style = MockTheme.typography.codeSmall,
        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                if (selected) accent.copy(alpha = TINT_CONTAINER_ALPHA) else Color.Transparent,
                PillShape,
            )
            .border(
                BorderStroke(
                    Dimens.hairline,
                    if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                ),
                PillShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall),
    )
}

@Preview(name = "Option chips", widthDp = 360)
@Composable
internal fun OptionChipRowPreview() {
    AppTheme {
        OptionChipRow(
            options = listOf(200, 201, 204, 400, 404),
            selected = 200,
            onSelect = {},
            label = { it.toString() },
            otherLabel = "Other",
            onOther = {},
            modifier = Modifier.padding(Dimens.space),
        )
    }
}
