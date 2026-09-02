package com.minion.mockserver.core.domain.usecase.chaos

import com.minion.mockserver.core.domain.model.ServerSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * The seeded RNG the chaos engine draws from (SPEC §6.3).
 *
 * Seeded, and the seed is visible and regenerable in settings, for two reasons that matter more
 * than they sound: "reproduce that flaky failure" becomes a thing you can actually do, and the
 * chaos unit tests get to assert exact outcomes instead of statistical ones.
 *
 * [draw] takes a block rather than exposing per-value methods so that every decision for one
 * request consumes a contiguous run of the sequence. Interleaved draws from two concurrent
 * requests would still be non-reproducible in aggregate — nothing can fix that — but each
 * individual request's decision stays a single atomic step, which is what makes replaying one
 * request at a time deterministic.
 */
@Singleton
class ChaosRandom @Inject constructor() {

    private val lock = Any()

    private var currentSeed: Long = ServerSettings.DEFAULT_CHAOS_SEED

    private var random: Random = Random(currentSeed)

    /** The seed the sequence is currently running from. Shown in settings. */
    val seed: Long get() = synchronized(lock) { currentSeed }

    /** Restarts the sequence from [newSeed]. Idempotent for the same value. */
    fun reseed(newSeed: Long) {
        synchronized(lock) {
            currentSeed = newSeed
            random = Random(newSeed)
        }
    }

    /** Runs [block] against the sequence, holding the lock for its whole run. */
    fun <T> draw(block: (Random) -> T): T = synchronized(lock) { block(random) }
}
