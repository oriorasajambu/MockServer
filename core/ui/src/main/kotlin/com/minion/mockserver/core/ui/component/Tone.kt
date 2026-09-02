package com.minion.mockserver.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.minion.mockserver.core.designsystem.theme.TINT_CONTAINER_ALPHA

/**
 * How loudly a callout, a dialog header or a result bar should read.
 *
 * Shared across those components on purpose. A rename dialog and a delete dialog are the same
 * layout with a different tone, and expressing that as one parameter is what stops them drifting
 * into two half-similar designs — the destructive one is not a different component, it is this
 * one set to [Error].
 */
enum class Tone {
    /** The default. Information that needs saying but not flagging. */
    Neutral,

    /** Something the app did deliberately: a chaos note, a successful test, a live value. */
    Accent,

    /** Something wrong or irreversible: a no-match, a 5xx, a delete. */
    Error,
}

/** The tone's foreground — icon and label. */
val Tone.content: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        Tone.Accent -> MaterialTheme.colorScheme.primary
        Tone.Error -> MaterialTheme.colorScheme.error
    }

/**
 * The tone's fill, at the one tint alpha the whole app uses.
 *
 * Derived from [content] rather than stored separately so a tone can never end up with a label
 * and a background that disagree.
 */
val Tone.container: Color
    @Composable @ReadOnlyComposable get() = content.copy(alpha = TINT_CONTAINER_ALPHA)
