package com.minion.scaffold.core.ui.mvi

import androidx.lifecycle.ViewModel
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * The base every feature ViewModel extends: one state stream in, one intent entry point, one
 * effect channel out.
 *
 * Unidirectional by construction — the UI can only send an [UiIntent], and can only observe
 * [state] and [effect]. There is no setter for a screen to reach in and mutate a field.
 *
 * @param S the screen's complete state
 * @param I everything the user can do
 * @param E one-shot events: navigation, toasts
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)

    /**
     * The screen's state.
     *
     * Exposed as a read-only [StateFlow], never the backing [MutableStateFlow]. A leaked mutable
     * reference lets the UI write state directly, which is the one thing this architecture exists
     * to prevent — and it fails silently, because everything still renders.
     */
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = Channel<E>(Channel.BUFFERED)

    /**
     * One-shot events.
     *
     * A [Channel], deliberately, rather than a `SharedFlow`. A `SharedFlow` with no replay drops
     * anything emitted while nothing is collecting — so an effect sent during a configuration
     * change or while the screen is backgrounded simply vanishes, and a navigation never happens.
     * A buffered channel holds the event until someone collects it, and delivers it exactly once.
     *
     * `receiveAsFlow`, not `consumeAsFlow`: the latter cancels the channel on first collection,
     * which breaks the moment a second collector appears.
     */
    val effect: Flow<E> = _effect.receiveAsFlow()

    /** The single entry point for everything the user does. */
    abstract fun onIntent(intent: I)

    /**
     * Applies a change to the current state.
     *
     * Uses [MutableStateFlow.update], which is atomic — a read-then-write via `value` would lose
     * an update when two coroutines reduce concurrently.
     */
    protected fun reduce(block: S.() -> S) {
        _state.update(block)
    }

    /** The current state, for reducers that need to read before deciding. */
    protected val currentState: S get() = _state.value

    /** Sends a one-shot [effect]. Suspends only if the buffer is full. */
    protected suspend fun emitEffect(effect: E) {
        _effect.send(effect)
    }
}
