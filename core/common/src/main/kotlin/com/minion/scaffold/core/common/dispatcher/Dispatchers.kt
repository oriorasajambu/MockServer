package com.minion.scaffold.core.common.dispatcher

import javax.inject.Qualifier

/**
 * Qualifies the dispatcher used for disk and network work.
 *
 * Injecting the dispatcher rather than calling `Dispatchers.IO` directly is what makes the code
 * testable: a test substitutes a `TestDispatcher` and the work runs on the test's scheduler
 * instead of a real thread pool, so `advanceUntilIdle()` actually controls it.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

/** Qualifies the dispatcher used for CPU-bound work: sorting, parsing, deriving. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

/** Qualifies the main thread dispatcher. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher

/**
 * Qualifies the application-lifetime `CoroutineScope`, for work that must outlive the screen
 * that started it — a write that has to complete even if the user navigates away.
 *
 * Not a substitute for `viewModelScope`. Anything whose result the UI is waiting on belongs in
 * `viewModelScope`, so it is cancelled when the ViewModel dies.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope
