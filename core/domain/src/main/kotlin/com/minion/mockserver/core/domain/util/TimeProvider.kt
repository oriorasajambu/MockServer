package com.minion.mockserver.core.domain.util

/**
 * The clock, injected rather than called.
 *
 * `System.currentTimeMillis()` in a use case makes every assertion about timestamps and durations
 * either flaky or impossible. Both readings are here because they answer different questions:
 * [nowMillis] is what a traffic entry is stamped with, and [monotonicNanos] is what its duration
 * is measured with — a wall clock that steps backwards over an NTP correction would otherwise
 * produce negative response times.
 */
interface TimeProvider {

    /** Epoch milliseconds. For timestamps a human reads. */
    fun nowMillis(): Long

    /** A monotonic reading in nanoseconds. Meaningful only as a difference. */
    fun monotonicNanos(): Long

    companion object {

        /** The real clock. Bound by Hilt in `:core:data`; used directly only in tests' fixtures. */
        val System: TimeProvider = object : TimeProvider {
            override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
            override fun monotonicNanos(): Long = java.lang.System.nanoTime()
        }
    }
}
