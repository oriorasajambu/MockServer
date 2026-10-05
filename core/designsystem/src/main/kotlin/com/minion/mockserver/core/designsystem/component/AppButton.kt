package com.minion.mockserver.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * The app's primary button.
 *
 * **Outlined, not filled.** Nocturne treats the accent as a line and a label, never as a flood —
 * so the primary action is an accent border over the darkest accent step, not a block of colour
 * with text reversed out of it. A filled `Button` on any screen in this app is off-design, and
 * this component exists so that is one decision rather than one per call site.
 *
 * Exists so features call one component instead of Material's own. When the product decides its
 * buttons are taller, or animate on press, that is one edit here rather than a search across
 * every screen.
 *
 * @param icon drawn before the label. Decorative — the label carries the meaning, so it is not
 *   announced separately.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dimens.touchTarget),
        enabled = enabled,
        border = BorderStroke(
            width = Dimens.hairline,
            // The border dims with the label. Left at full accent, a disabled button reads as an
            // enabled one whose text simply failed to draw.
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

/**
 * The secondary action alongside an [AppButton].
 *
 * The same shape with a neutral border and no fill, so a pair of them reads as one choice with a
 * default rather than as two competing buttons.
 */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dimens.touchTarget),
        enabled = enabled,
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            // The label is right beside it.
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSmall),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = if (icon != null) Modifier.padding(start = Dimens.spaceSmall) else Modifier,
    )
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
