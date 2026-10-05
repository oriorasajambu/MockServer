package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.ui.R

/**
 * What happened the last time this route was fired at itself.
 *
 * Shown only after a test, which is the point: an editor that always displayed a result would be
 * showing a stale one most of the time, and a stale green tick on a rule you have since broken is
 * worse than no tick at all.
 *
 * Tapping it opens the traffic entry the test produced. The bar answers "did it work"; the entry
 * answers "why not", and the two are one tap apart rather than one navigation and a search.
 *
 * Tone comes from the status class, not from whether the request completed. A route that answers
 * 404 was reached successfully and is still wrong, so it reads as a failure here.
 */
@Composable
fun TestResultBar(
    statusCode: Int,
    durationMillis: Long,
    byteCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val succeeded = statusCode / 100 == 2
    val tint = if (succeeded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Icon(
            imageVector = if (succeeded) MockIcons.CheckCircleFill else MockIcons.WarningCircle,
            // The label and the code beside it say it in words and in numbers.
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Text(
            text = stringResource(R.string.editor_test_fired),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = stringResource(
                R.string.editor_test_result,
                statusCode,
                durationMillis,
                byteCount,
            ),
            style = MockTheme.typography.codeSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(name = "Test result", widthDp = 360)
@Composable
internal fun TestResultBarPreview() {
    AppTheme {
        Column {
            TestResultBar(statusCode = 200, durationMillis = 254, byteCount = 61, onClick = {})
            TestResultBar(statusCode = 404, durationMillis = 12, byteCount = 0, onClick = {})
        }
    }
}
