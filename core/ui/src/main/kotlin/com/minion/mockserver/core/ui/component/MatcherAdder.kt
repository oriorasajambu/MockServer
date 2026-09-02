package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.StringOp
import com.minion.mockserver.core.ui.R

/**
 * Builds one header or query matcher and hands it over (SPEC §4.1, §4.3).
 *
 * The draft — which kind, the name, the value — is local state, not hoisted. A half-typed matcher
 * is not part of the route being edited: it becomes one on Add, and until then losing it to a
 * rotation costs two words, while routing every keystroke through the ViewModel would put
 * unfinished input into the object that gets saved.
 *
 * `EQUALS` is the only operator offered here. The others exist on [RequestMatcher] and are worth
 * a fuller editor; equality is what almost every matcher actually is.
 */
@Composable
fun MatcherAdder(
    onAdd: (RequestMatcher) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var isHeader by remember { mutableStateOf(true) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            FilterChip(
                selected = isHeader,
                onClick = { isHeader = true },
                label = { Text(stringResource(R.string.editor_matcher_header)) },
            )
            FilterChip(
                selected = !isHeader,
                onClick = { isHeader = false },
                label = { Text(stringResource(R.string.editor_matcher_query)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.editor_matcher_name)) },
                singleLine = true,
                modifier = Modifier.weight(FIELD_WEIGHT),
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.editor_matcher_value)) },
                singleLine = true,
                modifier = Modifier.weight(FIELD_WEIGHT),
            )
        }
        TextButton(
            onClick = {
                if (name.isBlank()) return@TextButton
                onAdd(
                    if (isHeader) {
                        RequestMatcher.Header(name, StringOp.EQUALS, value)
                    } else {
                        RequestMatcher.Query(name, StringOp.EQUALS, value)
                    },
                )
                name = ""
                value = ""
            },
        ) {
            Text(stringResource(R.string.editor_add_matcher))
        }
    }
}

/** Warnings are advisory and never block Save — that distinction is the whole point of them. */

/** Name and value share the row evenly. */
private const val FIELD_WEIGHT = 1f

@Preview
@Composable
internal fun MatcherAdderPreview() {
    AppTheme {
        MatcherAdder(onAdd = {})
    }
}
