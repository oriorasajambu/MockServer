package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.ui.R

/**
 * A 0..1 probability, shown as a percentage.
 *
 * A slider rather than a number field: a probability is a feel, not a figure, and "roughly a
 * third of requests" is the way anyone actually thinks about injected failure. The percentage in
 * the label is what keeps it reproducible afterwards.
 *
 * @param value 0.0..1.0
 * @param help what firing at this probability will actually do. A function of [value], not fixed
 *   copy: a line that says the same thing at 0% and at 40% is one nobody reads twice.
 */
@Composable
fun PercentSlider(
    label: String,
    help: String,
    value: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.probability_percent, label, (value * PERCENT).toInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = 0f..1f,
        )
        Text(
            text = help,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val PERCENT = 100

@Preview
@Composable
internal fun PercentSliderPreview() {
    AppTheme {
        PercentSlider(
            label = "Inject an error status",
            help = "Replaces the response with 500, 503 or 429.",
            value = 0.35,
            onChange = {},
        )
    }
}
