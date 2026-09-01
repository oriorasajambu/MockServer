package com.rio.mockserver.core.domain.usecase.chaos

import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.domain.model.ChaosFailure
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.ConnectionResetSpec
import com.rio.mockserver.core.domain.model.DelaySpec
import com.rio.mockserver.core.domain.model.ErrorInjection
import com.rio.mockserver.core.domain.model.MalformedBodySpec
import com.rio.mockserver.core.domain.model.WeightedStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC §6 — chaos, asserted exactly rather than statistically.
 *
 * Every case here pins the probability at 0.0 or 1.0, or fixes the seed. A test that ran a
 * thousand requests and checked the rate would be slow, flaky, and would still not tell you which
 * mode fired — which is the thing that actually goes wrong.
 */
class DecideChaosUseCaseTest {

    private val random = ChaosRandom()
    private val decide = DecideChaosUseCase(random)

    @Test
    fun `a null profile does nothing`() {
        val applied = decide(profile = null)
        assertEquals(0L, applied.delayMillis)
        assertNull(applied.failure)
    }

    @Test
    fun `probability 0 never fires and probability 1 always does`() {
        assertNull(decide(profile(errorProbability = 0.0)).failure)
        repeat(20) {
            assertEquals(
                ChaosFailure.ErrorInjected(500),
                decide(profile(errorProbability = 1.0, statuses = listOf(WeightedStatus(500)))).failure,
            )
        }
    }

    @Test
    fun `only one failure mode fires, in SPEC order`() {
        val everything = ChaosProfile(
            id = "p",
            name = "all",
            errorInjection = ErrorInjection(probability = 1.0, statuses = listOf(WeightedStatus(503))),
            connectionReset = ConnectionResetSpec(probability = 1.0, dropAfterBytes = 8),
            malformedBody = MalformedBodySpec(probability = 1.0, truncateAtPercent = 10),
        )

        // Error injection is first in the list, so it wins and the rest are skipped.
        assertEquals(ChaosFailure.ErrorInjected(503), decide(everything).failure)
    }

    @Test
    fun `connection reset fires when error injection does not`() {
        val profile = ChaosProfile(
            id = "p",
            name = "reset",
            errorInjection = ErrorInjection(probability = 0.0),
            connectionReset = ConnectionResetSpec(probability = 1.0, dropAfterBytes = 128),
            malformedBody = MalformedBodySpec(probability = 1.0),
        )
        assertEquals(ChaosFailure.ConnectionReset(128), decide(profile).failure)
    }

    @Test
    fun `malformed body fires last`() {
        val profile = ChaosProfile(
            id = "p",
            name = "malformed",
            malformedBody = MalformedBodySpec(probability = 1.0, truncateAtPercent = 60),
        )
        assertEquals(ChaosFailure.MalformedBody(60), decide(profile).failure)
    }

    @Test
    fun `delay is applied alongside a failure, not instead of it`() {
        // The documented deviation from a literal reading of SPEC §6.2: a profile that sets a
        // delay must still be able to inject errors.
        val profile = ChaosProfile(
            id = "p",
            name = "slow and broken",
            delay = DelaySpec.Fixed(250),
            errorInjection = ErrorInjection(probability = 1.0, statuses = listOf(WeightedStatus(500))),
        )

        val applied = decide(profile)

        assertEquals(250L, applied.delayMillis)
        assertEquals(ChaosFailure.ErrorInjected(500), applied.failure)
    }

    @Test
    fun `a random delay stays within its bounds, inclusive`() {
        val profile = profile(delay = DelaySpec.Random(100, 200))
        repeat(200) {
            val millis = decide(profile).delayMillis
            assertTrue("$millis outside 100..200", millis in 100L..200L)
        }
    }

    @Test
    fun `a random delay with equal bounds is exact`() {
        assertEquals(100L, decide(profile(delay = DelaySpec.Random(100, 100))).delayMillis)
    }

    @Test
    fun `a delay above the ceiling is clamped`() {
        val profile = profile(delay = DelaySpec.Fixed(AppConstants.Chaos.MAX_DELAY_MILLIS + 5_000))
        assertEquals(AppConstants.Chaos.MAX_DELAY_MILLIS, decide(profile).delayMillis)
    }

    @Test
    fun `a route delay override replaces the profile delay entirely`() {
        // The number in the editor is the number the client waits — not that plus a global one.
        val profile = profile(delay = DelaySpec.Fixed(5_000))
        assertEquals(120L, decide(profile, routeDelayOverride = 120L).delayMillis)
    }

    @Test
    fun `a route delay override applies with no profile at all`() {
        assertEquals(120L, decide(profile = null, routeDelayOverride = 120L).delayMillis)
    }

    @Test
    fun `the same seed produces the same sequence`() {
        val profile = profile(
            delay = DelaySpec.Random(0, 1_000),
            errorProbability = 0.5,
            statuses = listOf(WeightedStatus(500), WeightedStatus(503), WeightedStatus(429)),
        )

        val first = runSequence(seed = 42L, profile = profile, count = 50)
        val second = runSequence(seed = 42L, profile = profile, count = 50)
        val different = runSequence(seed = 43L, profile = profile, count = 50)

        assertEquals("same seed must replay exactly", first, second)
        assertTrue("a different seed should diverge", first != different)
    }

    @Test
    fun `weights bias the pool and a zero weight is never drawn`() {
        val profile = profile(
            errorProbability = 1.0,
            statuses = listOf(WeightedStatus(500, weight = 0), WeightedStatus(503, weight = 3)),
        )
        random.reseed(7L)

        repeat(50) {
            assertEquals(ChaosFailure.ErrorInjected(503), decide(profile).failure)
        }
    }

    @Test
    fun `an all-zero-weight pool disables error injection rather than crashing`() {
        val profile = profile(
            errorProbability = 1.0,
            statuses = listOf(WeightedStatus(500, weight = 0)),
        )
        assertNull(decide(profile).failure)
    }

    @Test
    fun `an empty profile is treated as no chaos`() {
        assertNull(decide(ChaosProfile.NONE).failure)
        assertEquals(0L, decide(ChaosProfile.NONE).delayMillis)
    }

    private fun runSequence(seed: Long, profile: ChaosProfile, count: Int): List<String> {
        random.reseed(seed)
        return (1..count).map { decide(profile).toString() }
    }

    private fun profile(
        delay: DelaySpec = DelaySpec.None,
        errorProbability: Double = 0.0,
        statuses: List<WeightedStatus> = ErrorInjection.DEFAULT_POOL,
    ) = ChaosProfile(
        id = "p",
        name = "test",
        delay = delay,
        errorInjection = ErrorInjection(probability = errorProbability, statuses = statuses),
    )
}
