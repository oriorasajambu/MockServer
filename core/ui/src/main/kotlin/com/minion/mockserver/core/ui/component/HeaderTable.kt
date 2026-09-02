package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens

/**
 * A name/value table for HTTP headers.
 *
 * Values are monospaced and names are not: a header value is a token someone will compare
 * character by character against what their client sent, and a proportional font makes
 * `application/json;charset=utf-8` and `application/json; charset=utf-8` look identical.
 *
 * Renders nothing at all when [headers] is empty, so a caller can hand it a request's headers
 * without first checking whether there were any.
 */
@Composable
fun HeaderTable(
    title: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    if (headers.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        headers.forEach { (name, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(WEIGHT_NAME),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(WEIGHT_VALUE),
                )
            }
        }
    }
}

/** The name column is narrower: header names are short, values are the part that wraps. */
private const val WEIGHT_NAME = 1f
private const val WEIGHT_VALUE = 2f

@Preview
@Composable
internal fun HeaderTablePreview() {
    AppTheme {
        HeaderTable(
            title = "Headers",
            headers = mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "X-Mock-Key" to "a1b2c3d4e5f6a7b8",
            ),
        )
    }
}
