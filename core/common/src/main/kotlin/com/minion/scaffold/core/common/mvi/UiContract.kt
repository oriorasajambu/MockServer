package com.minion.scaffold.core.common.mvi

/**
 * Marker for the complete state of one screen.
 *
 * Implemented by a `data class` with safe defaults for every field, so a screen can render before
 * anything has loaded. One state object per screen — not one per section — because partial state
 * is how two fields end up disagreeing about whether the screen is loading.
 */
interface UiState

/**
 * Marker for everything the user (or the system) can do on one screen.
 *
 * Implemented by a `sealed interface`, so the ViewModel's `when (intent)` is exhaustive and
 * adding a case is a compile error until it is handled.
 */
interface UiIntent

/**
 * Marker for one-shot events: navigation, snackbars, toasts.
 *
 * The distinction from [UiState] is whether replaying it would be wrong. A loading flag should
 * survive rotation; a "navigate to detail" must not fire twice. Anything in the second category
 * is an effect, and storing it in state instead is the bug this interface exists to prevent.
 */
interface UiEffect
