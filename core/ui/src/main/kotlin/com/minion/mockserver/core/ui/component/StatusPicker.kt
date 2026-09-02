package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.ui.R

/**
 * An HTTP status code: the dozen anybody actually mocks, one tap away, and a field for the rest.
 *
 * Chips *and* a field, not one or the other. The pinned list covers almost every route somebody
 * writes on a phone, and typing three digits into a numeric field is the fallback for the times
 * it does not — a scrolling list of all 60-odd codes would be slower than both.
 */
@Composable
fun StatusPicker(
    statusCode: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            PINNED_STATUSES.forEach { code ->
                FilterChip(
                    selected = statusCode == code,
                    onClick = { onSelect(code) },
                    label = { Text(code.toString()) },
                )
            }
        }
        LabelledTextField(
            value = statusCode.toString(),
            onValueChange = { it.toIntOrNull()?.let(onSelect) },
            label = stringResource(R.string.status_picker_label),
            numeric = true,
            imeAction = ImeAction.Done,
        )
    }
}

/**
 * The codes worth a chip, in the order a developer thinks of them: the successes, then the
 * client errors that a client's error handling is written against, then the server errors chaos
 * injects.
 */
private val PINNED_STATUSES = listOf(200, 201, 204, 400, 401, 403, 404, 409, 422, 429, 500, 503)

@Preview
@Composable
internal fun StatusPickerPreview() {
    AppTheme {
        StatusPicker(statusCode = 404, onSelect = {})
    }
}
