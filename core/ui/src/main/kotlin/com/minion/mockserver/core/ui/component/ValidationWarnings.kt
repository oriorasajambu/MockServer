package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.usecase.route.RouteValidation
import com.minion.mockserver.core.ui.R

/**
 * What is questionable about a route, without stopping anyone saving it.
 *
 * Warnings, not errors, and the distinction is the point: a placeholder sitting unquoted inside a
 * JSON string will probably produce invalid JSON, and it is still the author's call — a mock
 * server that refuses to save a rule because it disapproves of it is a mock server people work
 * around (SPEC §5.1). Errors block Save; these do not.
 */
@Composable
fun ValidationWarnings(
    warnings: List<RouteValidation.Warning>,
    modifier: Modifier = Modifier,
) {
    if (warnings.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceHalf),
    ) {
        warnings.forEach { warning ->
            Text(
                text = warning.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** The sentence each warning becomes. */
@Composable
private fun RouteValidation.Warning.describe(): String = when (this) {
    is RouteValidation.Warning.UnquotedPlaceholder ->
        stringResource(R.string.editor_warn_unquoted, placeholder)

    is RouteValidation.Warning.ReservedHeader ->
        stringResource(R.string.editor_warn_reserved_header, name)

    is RouteValidation.Warning.LongDelay -> stringResource(R.string.editor_warn_long_delay, millis)
    is RouteValidation.Warning.UnknownPathPlaceholder ->
        stringResource(R.string.editor_warn_unknown_placeholder, name)
}

@Preview
@Composable
internal fun ValidationWarningsPreview() {
    AppTheme {
        ValidationWarnings(
            warnings = listOf(
                RouteValidation.Warning.UnquotedPlaceholder("{{path.id}}"),
                RouteValidation.Warning.LongDelay(45_000L),
            ),
        )
    }
}
