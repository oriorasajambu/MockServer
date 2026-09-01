package com.minion.scaffold.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Collects a one-shot effect flow tied to the **lifecycle**, not to composition.
 *
 * `LaunchedEffect(Unit) { flow.collect { } }` looks equivalent and is not. It is scoped to
 * composition, and a backgrounded screen stays composed — so effects keep arriving while the app
 * is not visible. A `navigate()` fires in the background and the user returns to the wrong
 * screen; a snackbar is shown to nobody and consumed. `repeatOnLifecycle(STARTED)` stops
 * collecting when the screen stops.
 *
 * Nothing is lost by stopping: [MviViewModel.effect] is backed by a buffered `Channel`, so events
 * emitted while stopped are held and delivered on resume.
 *
 * `Dispatchers.Main.immediate` avoids a frame of delay between the effect being sent and
 * [onEvent] running, which is visible on navigation.
 *
 * Import note: [LocalLifecycleOwner] comes from `androidx.lifecycle.compose`. The one in
 * `androidx.compose.ui.platform` is deprecated as of lifecycle 2.8.0.
 *
 * @param flow the effect stream, usually `viewModel.effect`
 * @param key an extra restart key, when the handler closes over something that can change
 * @param onEvent what to do with each event
 */
@Composable
fun <T> ObserveAsEvents(flow: Flow<T>, key: Any? = null, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner, key) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                flow.collect(onEvent)
            }
        }
    }
}
