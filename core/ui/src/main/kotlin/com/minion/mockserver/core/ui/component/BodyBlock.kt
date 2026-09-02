package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.util.JsonTree
import com.minion.mockserver.core.ui.R

/**
 * A request or response body, in the three states a body can actually be in.
 *
 * - **Binary** — reported by size and never rendered (SPEC §5.4). Decoding
 *   `image/png` as text produces a screenful of mojibake that looks like corruption rather than
 *   like a picture, which is worse than saying nothing.
 * - **Absent** — said plainly, so an empty body is distinguishable from a body that failed to
 *   load.
 * - **Text** — monospaced, optionally pretty-printed.
 *
 * [truncated] is called out separately because a truncated body is still a body: it renders, and
 * the warning is what stops someone concluding the server sent malformed JSON when the log simply
 * stopped keeping it.
 *
 * @param byteCount size of the original body, used only to describe a binary one
 */
@Composable
fun BodyBlock(
    body: String?,
    byteCount: Int,
    truncated: Boolean,
    prettyPrint: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        Text(stringResource(R.string.body_title), style = MaterialTheme.typography.titleSmall)
        when {
            body == null && byteCount > 0 -> Text(
                text = stringResource(R.string.body_binary, byteCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            body.isNullOrBlank() -> Text(
                text = stringResource(R.string.body_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Text(
                text = if (prettyPrint) JsonTree.prettify(body) else body,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        if (truncated) {
            Text(
                text = stringResource(R.string.body_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview
@Composable
internal fun BodyBlockPreview() {
    AppTheme {
        BodyBlock(
            body = """{"id":42,"name":"Ada"}""",
            byteCount = 22,
            truncated = true,
            prettyPrint = true,
        )
    }
}
