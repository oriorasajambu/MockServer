package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * The heading above a group of related controls.
 *
 * A composable rather than a `Text` with a style passed at each call site: "which type scale is a
 * section heading" is one decision, and it should be made once.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

/** A label and a switch, filling the width — the shape every boolean setting takes. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        MockSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A single-line text field that fills the width, with an optional hint under it.
 *
 * Wrapped because the four decisions here — fill the width, stay single-line, put the hint in
 * `supportingText` rather than in the label, show the numeric keyboard for a numeric setting —
 * are the same at every call site, and the fourth is the one that gets forgotten. A port field
 * that opens the alphabetic keyboard is a small daily tax on a phone.
 *
 * The IME action is part of that: without one the keyboard shows a newline key on a field that
 * cannot take a newline, and every field in a form becomes a reach for the dismiss gesture.
 * [ImeAction.Next] moves to the field below, [ImeAction.Done] closes the keyboard.
 *
 * @param hint shown under the field; `null` leaves the supporting slot empty
 * @param numeric whether to ask for the numeric keyboard
 * @param imeAction what the keyboard's action key does — `Next` for any field with one after it
 */
@Composable
fun LabelledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    isError: Boolean = false,
    numeric: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() },
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A label and a read-only value.
 *
 * The value is monospaced and the label is not: these are counters and durations read at a
 * glance while they tick, and a proportional font makes every digit change the width of the row.
 */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Preview
@Composable
internal fun FormRowsPreview() {
    AppTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            SectionTitle("Access")
            LabelledTextField(
                value = "8080",
                onValueChange = {},
                label = "Port",
                hint = "1024–65535.",
                numeric = true,
            )
            ToggleRow(label = "Require an API key", checked = true, onCheckedChange = {})
            StatRow(label = "Requests", value = "1284")
        }
    }
}
