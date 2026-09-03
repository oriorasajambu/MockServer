package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/** One thing the sheet explains: a glyph, a name for it, and what it means. */
@Immutable
data class ExplainerPoint(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * A sheet that explains a feature in full, once.
 *
 * Built for chaos, where the alternative is a screen of percentages that only makes sense if you
 * already know what injecting a failure does — and shaped to serve any help sheet, because the
 * moment there are two of these they should not look different.
 *
 * A sheet rather than a screen: it is read once and dismissed, and pushing a destination for it
 * would put it in the back stack where a stray back gesture lands on it again.
 *
 * @param closing the caveat that belongs after the list rather than inside it — the thing that is
 *   true of all the points at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainerSheet(
    title: String,
    lead: String,
    points: List<ExplainerPoint>,
    closing: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = MockIcons.QuestionFill,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                // The sheet is a fixed height and this content is not: four points and a caveat
                // already fill a small screen, and at a large font scale the dismiss button would
                // otherwise sit below the fold with no way to reach it.
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Dimens.space,
                    end = Dimens.space,
                    bottom = Dimens.spaceLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
        ) {
            DialogHeader(title = title, icon = icon, tone = Tone.Accent)

            Text(
                text = lead,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FadedDivider()

            points.forEach { point ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)) {
                    Icon(
                        imageVector = point.icon,
                        // Each point's title is right beside its glyph.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.iconSmall),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceQuarter),
                    ) {
                        Text(
                            text = point.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = point.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Callout(text = closing, tone = Tone.Accent)

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.touchTarget),
                border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(name = "Explainer sheet", widthDp = 380)
@Composable
internal fun ExplainerSheetPreview() {
    AppTheme {
        ExplainerSheet(
            title = "What chaos profiles do",
            lead = "A profile is a set of odds that break responses on purpose.",
            points = listOf(
                ExplainerPoint(
                    icon = MockIcons.WarningOctagon,
                    title = "An error status",
                    body = "The mock is replaced with 500, 503 or 429.",
                ),
                ExplainerPoint(
                    icon = MockIcons.Plugs,
                    title = "A dead connection",
                    body = "Part of the body is written, then the socket closes.",
                ),
            ),
            closing = "At most one failure fires per request.",
            dismissLabel = "Got it",
            onDismiss = {},
        )
    }
}
