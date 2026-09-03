package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * An inline explanation: an icon, an optional bold lead, and a sentence.
 *
 * The app's one shape for "here is something you should know before you act". It appears on nine
 * of the twenty-one screens — under a chaos slider explaining what a percentage will actually do,
 * inside a delete dialog saying what is not recoverable, beside a field whose effect is not
 * obvious — and having one component for all of them is what stops those explanations drifting
 * into three different-looking boxes.
 *
 * [tone] picks the tint and the default icon together, so a caller cannot pair an error colour
 * with a cheerful glyph.
 *
 * @param lead a short bold clause before [text], for the callouts whose first few words are the
 *   point and the rest is elaboration.
 */
@Composable
fun Callout(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    lead: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tone.container, MaterialTheme.shapes.medium)
            .padding(Dimens.spaceMedium),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Icon(
            imageVector = icon ?: tone.defaultIcon,
            // The sentence beside it carries the meaning; the glyph only sets the register.
            contentDescription = null,
            tint = tone.content,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Column(modifier = Modifier.weight(1f)) {
            if (lead != null) {
                Text(
                    text = lead,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Paired with the tint so the glyph and the colour cannot disagree about how loud this is. */
private val Tone.defaultIcon: ImageVector
    @Composable get() = when (this) {
        Tone.Neutral -> MockIcons.Info
        Tone.Accent -> MockIcons.Lightning
        Tone.Error -> MockIcons.WarningCircle
    }

@Preview(name = "Callouts", widthDp = 360)
@Composable
internal fun CalloutPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(Dimens.space),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            Callout(text = "A label for you only. Clients never see it.")
            Callout(
                text = "At most one failure fires per request.",
                tone = Tone.Accent,
                lead = "Chaos is deliberate.",
            )
            Callout(
                text = "Nothing is exported yet. Save a copy first if you might want it back.",
                tone = Tone.Error,
            )
        }
    }
}
