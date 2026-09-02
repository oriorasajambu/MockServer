package com.minion.mockserver.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.minion.mockserver.core.designsystem.icon.MockIcons
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
 * That same distinction drives the rest of the bar, which is why this is one component and not
 * two. [onBack] is what separates a **tab root** from a **pushed screen**, and the design gives
 * them different chrome: a tab wears its title at `headlineSmall` over a faded rule, because it
 * is the top of a section and the rule is where the section starts; a pushed screen wears
 * `titleMedium` with an optional [subtitle] beneath, because it is describing one object and the
 * subtitle is that object's identity.
 *
 * **The insets are deliberately zeroed.** The host `Scaffold` in the nav graph has already
 * consumed the status bar for its own content padding, so a toolbar that consumed them again
 * would sit a status bar's height too low on every screen.
 *
 * @param onBack `null` on a bottom-bar tab, which has nowhere to go back to
 * @param subtitle the second line on a pushed screen — a route path, a collection name. Ignored
 *   on a tab root, which has no object to name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isTabRoot = onBack == null

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = if (isTabRoot) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    // Middle-ellipsised rather than end-ellipsised: a subtitle here is usually a
                    // path, and the tail of a path is the part that identifies it.
                    if (!isTabRoot && subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MockIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.toolbar_back),
                        )
                    }
                }
            },
            actions = actions,
            windowInsets = WindowInsets(0),
        )
        // Only under a tab root. A pushed screen is already bounded by the back arrow and the
        // screen it came from; a rule there would be a second edge saying the same thing.
        if (isTabRoot) {
            FadedDivider()
        }
    }
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
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppToolbar(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Preview
@Composable
internal fun AppToolbarPreview() {
    AppTheme {
        AppToolbar(title = "Orders API", onBack = {}, subtitle = "/api/v1/orders/{id}")
    }
}

@Preview
@Composable
internal fun AppToolbarTabPreview() {
    AppTheme {
        AppToolbar(title = "Traffic")
    }
}
