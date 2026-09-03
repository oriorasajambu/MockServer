package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.designsystem.theme.MockColors
import com.minion.mockserver.core.designsystem.theme.MockTheme
import com.minion.mockserver.core.ui.R

/**
 * The JSON body field — the component the route editor lives or dies on (SPEC §10.2).
 *
 * Phone authoring is the constraint behind every decision here:
 *
 * - **Paste is a first-class action** above the field rather than a keyboard long-press. Bodies
 *   are almost always pasted from a terminal or a colleague's message, and a long-press-and-aim
 *   on a phone is the slowest interaction in the app. What is on the clipboard is previewed on
 *   its own line rather than inside the button: a forty-character label pushed Format off the
 *   right-hand edge of the screen entirely.
 * - **A symbol row** puts `{ } [ ] " : ,` one tap away instead of three, which is where a phone
 *   keyboard buries them.
 * - **Syntax highlighting is a `VisualTransformation`**, not a third-party editor. The whole
 *   requirement is colouring spans of a `String`; pulling in an editor library would bring a
 *   parser, a theme system and an undo stack the app already gets from `TextField`.
 * - **Validation is advisory unless the content type claims JSON.** A route serving CSV is not
 *   wrong for failing a JSON parse.
 *
 * @param error a message with line and column, or `null` when the body parses
 */
@Composable
fun JsonEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    clipboardPreview: String? = null,
    contentType: String? = null,
    onPaste: () -> Unit = {},
    onPrettify: () -> Unit = {},
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.editor_body_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (contentType != null) {
                    Text(
                        text = contentType,
                        style = MockTheme.typography.codeSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onPaste) {
                Text(stringResource(R.string.editor_paste), maxLines = 1)
            }
            TextButton(onClick = onPrettify) {
                Text(stringResource(R.string.editor_format), maxLines = 1)
            }
        }

        // What pasting would actually overwrite the body with. On its own line because it is up
        // to forty characters of someone else's JSON, and no button label survives that.
        clipboardPreview?.let { preview ->
            Text(
                text = stringResourceWithPreview(preview),
                style = MockTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val jsonColours = MockTheme.colors
        val transformation = remember(jsonColours) { JsonHighlighter(jsonColours.toJsonPalette()) }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.editorMinHeight),
            textStyle = MockTheme.typography.codeMedium,
            visualTransformation = transformation,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = Dimens.spaceSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
        ) {
            SYMBOLS.forEach { symbol ->
                AssistChip(
                    onClick = { onValueChange(value.insert(symbol)) },
                    label = { Text(symbol, style = MockTheme.typography.codeMedium) },
                )
            }
        }
    }
}

/** Inserts [text] at the cursor, leaving the caret after it. */
private fun TextFieldValue.insert(text: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val updated = this.text.replaceRange(start, end, text)
    return copy(
        text = updated,
        selection = TextRange(start + text.length),
    )
}

/** The colours a JSON token can take, resolved from the theme once per recomposition of the field. */
private data class JsonPalette(
    val key: Color,
    val value: Color,
    val punctuation: Color,
)

/**
 * Not `@Composable`: it reads plain properties off a [MockColors] that has already been resolved,
 * and it is called from inside `remember`, where a composable call is illegal.
 *
 * Strings, numbers and literals share one tint. Nocturne is a mono palette, so telling them apart
 * would mean inventing two more hues, and the distinction that actually matters when reading a
 * body is key from value — which this keeps.
 */
private fun MockColors.toJsonPalette() = JsonPalette(
    key = jsonKey,
    value = jsonValue,
    punctuation = jsonPunctuation,
)

/**
 * Colours JSON tokens without parsing.
 *
 * A single left-to-right scan that tracks whether it is inside a string and whether that string
 * was followed by a colon. It cannot be wrong in a way that matters — the worst case is a token
 * coloured as a value when it is a key — and it costs nothing on every keystroke, which a real
 * parse over a 200 KB body would not.
 */
private class JsonHighlighter(private val palette: JsonPalette) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text), OffsetMapping.Identity)

    private fun highlight(source: String): AnnotatedString = buildAnnotatedString {
        append(source)
        var index = 0
        while (index < source.length) {
            when {
                source[index] == '"' -> {
                    val end = source.findStringEnd(index)
                    // A string followed by a colon is a key; anything else is a value.
                    val isKey = source.nextNonSpace(end + 1) == ':'
                    addStyle(
                        SpanStyle(color = if (isKey) palette.key else palette.value),
                        index,
                        (end + 1).coerceAtMost(source.length),
                    )
                    index = end + 1
                }

                source[index].isDigit() || (source[index] == '-' && source.getOrNull(index + 1)?.isDigit() == true) -> {
                    var end = index
                    while (end < source.length && (source[end].isDigit() || source[end] in ".eE+-")) end++
                    addStyle(SpanStyle(color = palette.value), index, end)
                    index = end
                }

                source.startsWith("true", index) || source.startsWith("null", index) -> {
                    addStyle(SpanStyle(color = palette.value), index, index + 4)
                    index += 4
                }

                source.startsWith("false", index) -> {
                    addStyle(SpanStyle(color = palette.value), index, index + 5)
                    index += 5
                }

                source[index] in "{}[]:," -> {
                    addStyle(SpanStyle(color = palette.punctuation), index, index + 1)
                    index++
                }

                else -> index++
            }
        }
    }

    /** The index of the closing quote, honouring backslash escapes. */
    private fun String.findStringEnd(openIndex: Int): Int {
        var index = openIndex + 1
        while (index < length) {
            when (this[index]) {
                '\\' -> index++
                '"' -> return index
            }
            index++
        }
        return length - 1
    }

    private fun String.nextNonSpace(from: Int): Char? {
        var index = from
        while (index < length && this[index].isWhitespace()) index++
        return getOrNull(index)
    }
}

@Composable
private fun stringResourceWithPreview(preview: String): String =
    stringResource(R.string.editor_paste_preview, preview)

/** The characters a phone keyboard buries three taps deep. */
private val SYMBOLS = listOf("{", "}", "[", "]", "\"", ":", ",")

@Preview
@Composable
internal fun JsonEditorPreview() {
    AppTheme {
        JsonEditor(
            value = TextFieldValue("""{"id": 42, "name": "Ada", "active": true}"""),
            onValueChange = {},
            clipboardPreview = """{"id": 1…""",
        )
    }
}
