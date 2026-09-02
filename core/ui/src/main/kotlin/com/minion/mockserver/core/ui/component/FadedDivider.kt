package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * A rule that fades to transparent at both ends.
 *
 * Nocturne's signature edge: freestanding rules dissolve over [Dimens.dividerFadeLength] rather
 * than stopping against nothing. Drawn as a four-stop horizontal gradient rather than as three
 * stacked boxes, so it is one draw call in a list that may hold hundreds of them.
 *
 * Use this under a screen title or between sections. **Not** between two list rows: a row rule
 * that faded at the ends would read as a rendering fault, and those stay flat — pass
 * [FlatDivider] for those.
 */
@Composable
fun FadedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.hairline),
    ) {
        // A zero-width canvas would divide by zero on the first frame of a lazy item, and a rule
        // narrower than two fade lengths has no solid middle to reach — in both cases the honest
        // answer is a fade that meets in the centre.
        if (size.width <= 0f) return@Canvas
        val fade = (Dimens.dividerFadeLength.toPx()).coerceAtMost(size.width / 2f)
        val stop = fade / size.width

        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                stop to color,
                (1f - stop) to color,
                1f to Color.Transparent,
            ),
        )
    }
}

/**
 * The flat counterpart: a full-bleed hairline with no fade, for rules *between* rows.
 *
 * Exists so a call site picks the rule it means by name rather than by remembering which of the
 * two Material's `HorizontalDivider` happens to match.
 */
@Composable
fun FlatDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.hairline),
    ) {
        drawRect(color = color)
    }
}

@Preview(name = "Dividers", widthDp = 320)
@Composable
internal fun FadedDividerPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(vertical = Dimens.spaceLarge)) {
            FadedDivider()
            FlatDivider(modifier = Modifier.padding(top = Dimens.spaceLarge))
        }
    }
}
