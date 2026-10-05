package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.designsystem.theme.PillShape
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA
import com.minion.mockserver.core.domain.model.HttpMethodFilter

/**
 * The method label at the start of every route and traffic row. Appears on nine screens.
 *
 * Fixed minimum width, so paths in a list all begin at the same x. A badge that sized itself to
 * its text would put `GET /users` and `DELETE /users` at different indents, and the eye reads a
 * list of paths far faster when they are aligned.
 *
 * Tinted by **verb class, not by status**. A POST that returned 500 is still a POST, and a pill
 * that took its colour from the outcome would leave the method unreadable exactly on the row that
 * matters most — so writes take the accent, reads stay neutral, and only DELETE borrows the error
 * tone, because it is the one method whose consequence is destructive.
 */
@Composable
fun MethodPill(
    method: String,
    modifier: Modifier = Modifier,
) {
    // Switched on the domain enum rather than on wire strings: the same seven method names were
    // otherwise spelled out here, in the traffic filter's chips and in the request pipeline, and
    // a typo in any of those is a silent mismatch rather than a compile error. `fromWire` maps
    // anything unrecognised to ANY, which lands in the same neutral branch reads do.
    val tint = when (HttpMethodFilter.fromWire(method)) {
        HttpMethodFilter.POST, HttpMethodFilter.PUT, HttpMethodFilter.PATCH ->
            MockTheme.colors.methodWrite

        HttpMethodFilter.DELETE -> MockTheme.colors.methodDelete

        HttpMethodFilter.GET, HttpMethodFilter.HEAD, HttpMethodFilter.OPTIONS,
        HttpMethodFilter.ANY,
        -> MockTheme.colors.methodGet
    }

    Box(
        modifier = modifier
            .widthIn(min = Dimens.methodBadgeWidth)
            .background(tint.copy(alpha = TINT_CONTAINER_ALPHA), PillShape)
            .padding(horizontal = Dimens.spaceSmall, vertical = Dimens.spaceHalf),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = method.uppercase(),
            style = MockTheme.typography.methodPill,
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The status code, coloured by class.
 *
 * By class rather than by exact code, because that is the distinction anyone scanning a log is
 * making: did it work, was it my fault, was it the server's. Derives its own tint from the code
 * rather than taking one, so a caller cannot colour a 200 as a failure.
 */
@Composable
fun StatusBadge(
    statusCode: Int,
    modifier: Modifier = Modifier,
) {
    val tint: Color = when (statusCode / 100) {
        2 -> MaterialTheme.colorScheme.primary
        4, 5 -> MaterialTheme.colorScheme.error
        // 3xx and anything unrecognised are the off state: neither success nor failure.
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(tint.copy(alpha = TINT_CONTAINER_ALPHA), PillShape)
            .padding(horizontal = Dimens.spaceSmall, vertical = Dimens.spaceHalf),
    ) {
        Text(
            text = statusCode.toString(),
            style = MockTheme.typography.methodPill,
            color = tint,
        )
    }
}

/**
 * The `×N` mark on a traffic row whose consecutive repeats were collapsed into one entry.
 *
 * Quiet by design — neutral, not accent. A poll firing every second is the most common thing this
 * log ever shows, and a badge that announced it would make the ordinary case the loudest thing on
 * screen.
 */
@Composable
fun RepeatBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                PillShape,
            )
            .padding(horizontal = Dimens.spaceHalf),
    ) {
        Text(
            text = "×$count",
            style = MockTheme.typography.codeSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Method pills")
@Composable
internal fun MethodPillPreview() {
    AppTheme {
        Column {
            listOf("GET", "POST", "DELETE", "ANY").forEach { MethodPill(it) }
        }
    }
}

@Preview(name = "Status badges and repeats")
@Composable
internal fun StatusBadgePreview() {
    AppTheme {
        Row {
            listOf(200, 301, 404, 503).forEach { StatusBadge(it) }
            RepeatBadge(count = 4)
        }
    }
}
