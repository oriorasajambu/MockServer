package com.rio.mockserver.core.domain.usecase.chaos

import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.domain.model.ChaosApplied
import com.rio.mockserver.core.domain.model.ChaosFailure
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.DelaySpec
import com.rio.mockserver.core.domain.model.ErrorInjection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Decides what chaos does to one request (SPEC §6.2).
 *
 * ### Draw order
 *
 * Every decision for a request is made in one atomic [ChaosRandom.draw], in this fixed order:
 * delay, then error injection, then connection reset, then malformed body. The order is part of
 * the contract — change it and every seeded test, and every "reproduce with seed 42" workflow,
 * silently produces different answers.
 *
 * A draw is *skipped* when its mode is off, and once a failure mode fires the remaining ones are
 * neither drawn nor applied. Both are deterministic given the same profile, which is what the
 * seed promises: the same seed and the same rules produce the same run.
 *
 * ### One failure, but delay is not one of them
 *
 * SPEC §6.2 lists delay first among four "failure modes" and then says only one fires per
 * request. Taken literally that makes error injection unreachable on any profile that also sets
 * a delay — "10% 500s, and everything takes 200 ms" would silently become "everything takes
 * 200 ms". So delay is applied independently, and the three genuine failure modes stay mutually
 * exclusive in SPEC order. The traffic entry records both, so nothing is hidden.
 */
@Singleton
class DecideChaosUseCase @Inject constructor(
    private val chaosRandom: ChaosRandom,
) {

    /**
     * Resolves the profile for a route and decides what happens.
     *
     * Scope resolution is `route.chaosOverride ?: globalProfile` — a route override replaces the
     * global profile *wholesale* rather than merging field by field (SPEC §6.1). Merging is what
     * produces "why is this one endpoint sleeping three seconds" bug hunts.
     *
     * @param routeDelayOverride [com.rio.mockserver.core.domain.model.MockResponse.delayMillisOverride],
     *   which replaces the profile's delay entirely when set — so the number in the editor is the
     *   number the client waits.
     */
    operator fun invoke(
        profile: ChaosProfile?,
        routeDelayOverride: Long? = null,
    ): ChaosApplied {
        if (profile == null || !profile.isActive) {
            return ChaosApplied(delayMillis = clampDelay(routeDelayOverride ?: 0L))
        }

        return chaosRandom.draw { random ->
            val delay = clampDelay(routeDelayOverride ?: drawDelay(profile.delay, random))
            ChaosApplied(delayMillis = delay, failure = drawFailure(profile, random))
        }
    }

    private fun drawDelay(spec: DelaySpec, random: Random): Long = when (spec) {
        DelaySpec.None -> 0L
        is DelaySpec.Fixed -> spec.millis
        is DelaySpec.Random -> {
            val min = minOf(spec.minMillis, spec.maxMillis).coerceAtLeast(0L)
            val max = maxOf(spec.minMillis, spec.maxMillis).coerceAtLeast(0L)
            // Inclusive of both bounds, so `Random(100, 100)` means exactly 100 rather than
            // throwing on an empty range.
            if (max <= min) min else random.nextLong(min, max + 1)
        }
    }

    private fun drawFailure(profile: ChaosProfile, random: Random): ChaosFailure? {
        val error = profile.errorInjection
        if (error.probability > 0.0 && error.statuses.any { it.weight > 0 }) {
            if (random.nextDouble() < error.probability) {
                return ChaosFailure.ErrorInjected(pickStatus(error, random))
            }
        }

        val reset = profile.connectionReset
        if (reset.probability > 0.0 && random.nextDouble() < reset.probability) {
            return ChaosFailure.ConnectionReset(reset.dropAfterBytes.coerceAtLeast(0))
        }

        val malformed = profile.malformedBody
        if (malformed.probability > 0.0 && random.nextDouble() < malformed.probability) {
            return ChaosFailure.MalformedBody(malformed.truncateAtPercent.coerceIn(0, 100))
        }

        return null
    }

    /**
     * Picks a status from the weighted pool.
     *
     * Non-positive weights are skipped rather than treated as an error: a user zeroing a status
     * in the editor means "not this one", and refusing to serve the request over it would be an
     * absurd escalation.
     */
    private fun pickStatus(injection: ErrorInjection, random: Random): Int {
        val pool = injection.statuses.filter { it.weight > 0 }
        val totalWeight = pool.sumOf { it.weight }
        var ticket = random.nextInt(totalWeight)
        for (entry in pool) {
            ticket -= entry.weight
            if (ticket < 0) return entry.status
        }
        // Unreachable while totalWeight is the sum of the same list, but a fold that returns a
        // status is better than one that can return null on a rounding surprise.
        return pool.last().status
    }

    /** A delay above the ceiling is indistinguishable from a hang, so it is clamped, not honoured. */
    private fun clampDelay(millis: Long): Long =
        millis.coerceIn(0L, AppConstants.Chaos.MAX_DELAY_MILLIS)
}
