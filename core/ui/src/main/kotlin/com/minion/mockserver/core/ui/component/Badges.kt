package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.HttpMethodFilter

/**
 * The coloured method label at the start of every route and traffic row.
 *
 * Fixed width, so paths in a list all begin at the same x. A badge that sized itself to its text
 * would put `GET /users` and `DELETE /users` at different indents, and the eye reads a list of
 * paths far faster when they are aligned.
 *
 * Colour comes from the theme, mapped by *meaning*: reads are neutral, writes are the primary
 * accent, deletes are the error colour. Hard-coding the familiar green/orange/red of an API
 * client would break dark mode and dynamic colour, and would fail contrast on some wallpapers.
 */
@Composable
fun MethodBadge(
    method: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Switched on the domain enum rather than on wire strings: the same seven method names were
    // otherwise spelled out here, in the traffic filter's chips and in the request pipeline, and
    // a typo in any of those is a silent mismatch rather than a compile error. `fromWire` maps
    // anything unrecognised to ANY, which lands in the same neutral branch the `else` used to.
    val filter = HttpMethodFilter.fromWire(method)
    val container = when (filter) {
        HttpMethodFilter.GET, HttpMethodFilter.HEAD -> scheme.surfaceVariant
        HttpMethodFilter.POST, HttpMethodFilter.PUT, HttpMethodFilter.PATCH ->
            scheme.primaryContainer

        HttpMethodFilter.DELETE -> scheme.errorContainer
        HttpMethodFilter.OPTIONS, HttpMethodFilter.ANY -> scheme.secondaryContainer
    }
    val content = when (filter) {
        HttpMethodFilter.GET, HttpMethodFilter.HEAD -> scheme.onSurfaceVariant
        HttpMethodFilter.POST, HttpMethodFilter.PUT, HttpMethodFilter.PATCH ->
            scheme.onPrimaryContainer

        HttpMethodFilter.DELETE -> scheme.onErrorContainer
        HttpMethodFilter.OPTIONS, HttpMethodFilter.ANY -> scheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .widthIn(min = Dimens.methodBadgeWidth)
            .background(container, RoundedCornerShape(Dimens.cornerSmall))
            .padding(horizontal = Dimens.spaceSmall, vertical = Dimens.spaceHalf),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = method.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The status code, coloured by class.
 *
 * By class rather than by exact code, because that is the distinction anyone scanning a log is
 * making: did it work, was it my fault, was it the server's.
 */
@Composable
fun StatusPill(
    statusCode: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container: Color = when (statusCode / 100) {
        2 -> scheme.primaryContainer
        3 -> scheme.secondaryContainer
        4 -> scheme.tertiaryContainer
        5 -> scheme.errorContainer
        else -> scheme.surfaceVariant
    }
    val content: Color = when (statusCode / 100) {
        2 -> scheme.onPrimaryContainer
        3 -> scheme.onSecondaryContainer
        4 -> scheme.onTertiaryContainer
        5 -> scheme.onErrorContainer
        else -> scheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(Dimens.cornerSmall))
            .padding(horizontal = Dimens.spaceSmall, vertical = Dimens.spaceHalf),
    ) {
        Text(
            text = statusCode.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

@Preview(name = "Method badges")
@Composable
internal fun MethodBadgePreview() {
    AppTheme {
        androidx.compose.foundation.layout.Column {
            listOf("GET", "POST", "DELETE", "ANY").forEach { MethodBadge(it) }
        }
    }
}

@Preview(name = "Status pills")
@Composable
internal fun StatusPillPreview() {
    AppTheme {
        androidx.compose.foundation.layout.Row {
            listOf(200, 301, 404, 503).forEach { StatusPill(it) }
        }
    }
}
