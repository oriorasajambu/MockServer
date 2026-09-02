package com.minion.mockserver.core.common.util

/**
 * The app's logging seam.
 *
 * Pure Kotlin with a pluggable [Logger.sink], because `:core:common`, `:core:domain`,
 * `:core:server` and `:core:importexport` have no Android plugin and so cannot call
 * `android.util.Log` — and giving them one so they could would undo the boundary those modules
 * exist to enforce.
 *
 * `:app` installs an Android sink at startup, gated on `BuildConfig.DEBUG`. Until it does, this
 * discards everything, which is the right default for a library module loaded by a test.
 *
 * Never call `android.util.Log` directly anywhere in this app: a log line that survives into a
 * release build of a *developer tool* is likely to be printing somebody's request bodies.
 */
object Logger {

    /** Where log lines go. Replaced once, at application start. */
    @Volatile
    var sink: Sink = Sink.None

    /** Verbose detail. */
    fun d(tag: String, message: String) = sink.log(Level.DEBUG, tag, message, null)

    /** Something unexpected that the app recovered from. */
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        sink.log(Level.WARN, tag, message, throwable)

    /** Something unexpected that the app did not recover from. */
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        sink.log(Level.ERROR, tag, message, throwable)

    /** Severity, in the order the platform's own levels use. */
    enum class Level { DEBUG, WARN, ERROR }

    /** A destination for log lines. */
    fun interface Sink {

        /** Writes one line. */
        fun log(level: Level, tag: String, message: String, throwable: Throwable?)

        companion object {

            /** Discards everything. The default, and what a release build keeps. */
            val None = Sink { _, _, _, _ -> }
        }
    }
}
