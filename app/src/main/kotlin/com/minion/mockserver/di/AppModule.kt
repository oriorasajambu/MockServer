package com.minion.mockserver.di

/**
 * There is nothing left for this module to bind, and that is the intended end state.
 *
 * The scaffold's version provided the base URL and the dispatchers. This app makes no outbound
 * calls, so there is no base URL; and the dispatchers, the application scope, the clock and the
 * id generator are all bound in `:core:data`'s `DispatcherModule`, beside the implementations
 * they wrap.
 *
 * A binding belongs here only if it depends on *which app this is* — an application `BuildConfig`
 * value, say. A core module reading `:app`'s `BuildConfig` is how it quietly stops being reusable,
 * which is the whole reason this file exists as a place to put such things.
 *
 * The file is kept rather than deleted so the next person adding an app-specific binding has an
 * obvious home for it, and this note explaining what does and does not belong.
 */
