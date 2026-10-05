package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.designsystem.theme.Dimens
import com.minion.mockserver.core.ui.R

/**
 * The traffic log's filter field.
 *
 * The clear button appears only once there is something to clear. A permanent one is a target
 * that does nothing most of the time, sitting where the eye checks for state — and on a field
 * that filters a live list, "is anything filtering this" is the question it should answer.
 *
 * Single-line with a search IME action: the query filters as you type, so the keyboard's action
 * key has nothing to submit and should close instead.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = MockIcons.MagnifyingGlass,
                // The placeholder says what the field is for.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = MockIcons.X,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSmall),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Preview(name = "Search bar", widthDp = 360)
@Composable
internal fun SearchBarPreview() {
    AppTheme {
        SearchBar(query = "orders", onQueryChange = {}, hint = "Search paths, headers, bodies")
    }
}
