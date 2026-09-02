package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.theme.AppTheme
import com.minion.mockserver.core.ui.R

/**
 * The toolbar every screen in this app wears.
 *
 * A component rather than a `TopAppBar` per screen, because a title that is `titleLarge` on one
 * screen and `titleMedium` on the next is the kind of drift nobody notices in review and
 * everybody notices in use. It also fixes the back affordance in one place: a full-screen
 * destination gets an arrow, a tab does not.
 *
 * **The insets are deliberately zeroed.** The host `Scaffold` in the nav graph has already
 * consumed the status bar for its own content padding, so a toolbar that consumed them again
 * would sit a status bar's height too low on every screen.
 *
 * @param onBack `null` on a bottom-bar tab, which has nowhere to go back to
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.toolbar_back),
                    )
                }
            }
        },
        actions = actions,
        windowInsets = WindowInsets(0),
    )
}

/**
 * A screen: [AppToolbar] on top, content below, optionally a floating action button.
 *
 * Every screen goes through this so the chrome cannot diverge — the review that produced it found
 * one screen with a `Scaffold` and six without, which is how a phone app ends up with a title on
 * some screens and not others.
 *
 * [content] receives the scaffold's padding. Apply it, or the toolbar overlaps the first row.
 */
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AppToolbar(title = title, onBack = onBack, actions = actions) },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Preview
@Composable
internal fun AppToolbarPreview() {
    AppTheme {
        AppToolbar(title = "Orders API", onBack = {})
    }
}

@Preview
@Composable
internal fun AppToolbarTabPreview() {
    AppTheme {
        AppToolbar(title = "Traffic")
    }
}
