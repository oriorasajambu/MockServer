package com.minion.mockserver.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours Material 3 has no role for.
 *
 * Four things in these screens are *domain* colours, not theme colours: what an HTTP method is,
 * how a request turned out, what a piece of JSON is, and what state a route is in. Forcing them
 * into `tertiary` and `secondary` would make the scheme lie about what those roles mean — the
 * next person to read `secondaryContainer` would have no way to know it meant "OPTIONS".
 *
 * Every value here is still a step of a ramp in `Color.kt`. This class decides *which* step; it
 * never introduces a colour the design system does not already carry. That constraint is what
 * keeps a rebrand to one file.
 *
 * Read it through [MockTheme.colors], never through [LocalMockColors] directly.
 */
@Immutable
data class MockColors(
    // Per-method pill tints. Tinted by verb *class* rather than by outcome, because a POST that
    // returns 500 is still a POST — a pill that took its colour from the status would leave the
    // method unreadable exactly when the row matters most. A write must not read as an error.
    val methodGet: Color,
    val methodWrite: Color,
    val methodDelete: Color,

    // How the request turned out. Distinct from `error` on purpose: an injected fault is the
    // server doing what it was told, so chaos takes the accent, not the error tone. Only a
    // genuine no-match is wrong.
    val matched: Color,
    val unmatched: Color,
    val chaosInjected: Color,

    // JSON syntax. Tokenising happens in the presentation layer; these are only the tints it
    // paints with.
    val jsonKey: Color,
    val jsonValue: Color,
    val jsonPunctuation: Color,

    // The 2dp leading rail on route and traffic rows — the fastest thing to scan in a long list,
    // because it is the only element at a fixed x with no text in it.
    val railActive: Color,
    val railWildcard: Color,
    val railOff: Color,
)

/**
 * The one instance. The app is dark only, so there is no light counterpart to keep in step.
 *
 * The step choices, since they are judgement and not arithmetic:
 * - Reads are neutral and writes take the accent, so a list of mostly-GET rows stays calm and the
 *   mutations are what catch the eye. Deletes borrow the error tone because they are the one
 *   method whose *consequence* is destructive.
 * - `matched` is deliberately the quietest value here. The normal case is most of the log, and a
 *   log where every row is tinted is a log with no signal in it.
 * - The rail's three steps are one ramp descending: an active route is the accent at full base,
 *   a wildcard is the same hue two steps back, and an off route drops to the neutral border step
 *   so it reads as absent rather than as another state.
 */
internal val darkMockColors = MockColors(
    methodGet = Neutral300,
    methodWrite = Accent300,
    methodDelete = Red80,

    matched = Neutral400,
    unmatched = Red80,
    chaosInjected = Accent400,

    jsonKey = Accent300,
    jsonValue = Neutral200,
    jsonPunctuation = Neutral600,

    railActive = Accent500,
    railWildcard = Accent700,
    railOff = Neutral800,
)

/**
 * Static rather than dynamic: these values never change for the life of the process, so there is
 * no reason to pay for a read that invalidates recomposition scopes.
 */
internal val LocalMockColors = staticCompositionLocalOf { darkMockColors }

/**
 * The alpha a method or outcome tint is laid over [Ground] at to become a pill's fill.
 *
 * One constant rather than a fourth set of tokens: the fill is always the same colour as the
 * label, which is what keeps twelve pills consistent without twelve more values to maintain.
 */
const val TINT_CONTAINER_ALPHA = 0.16f
