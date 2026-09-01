package com.minion.scaffold.core.designsystem.component

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.minion.scaffold.core.designsystem.theme.AppTheme

/**
 * The app's primary button.
 *
 * Exists so that features call one component instead of Material's [Button] directly. When the
 * product decides its buttons are taller, or gain a gradient, or animate on press, that is one
 * edit here rather than a search across every screen.
 *
 * The first of the design system's atoms, and the pattern the rest follow: a thin wrapper, a
 * `Modifier` parameter, no business logic, and a `@Preview` so it appears in the Showkase catalog.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text = text)
    }
}

/** The secondary action alongside an [AppButton]. */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text = text)
    }
}

@ShowkaseComposable(name = "Button", group = "Buttons")
@Preview(showBackground = true)
@Composable
internal fun AppButtonPreview() {
    AppTheme {
        AppButton(text = "Continue", onClick = {})
    }
}

@ShowkaseComposable(name = "Outlined Button", group = "Buttons")
@Preview(showBackground = true)
@Composable
internal fun AppOutlinedButtonPreview() {
    AppTheme {
        AppOutlinedButton(text = "Cancel", onClick = {})
    }
}
