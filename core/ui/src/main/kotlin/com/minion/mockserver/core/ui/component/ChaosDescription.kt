package com.minion.mockserver.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.ui.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a chaos profile does, in a sentence.
 *
 * Percentages are precise and useless. "Error injection 10%" tells you nothing about what you
 * will see; "about 1 request in 10 fails" tells you what to expect on the next screen you tap
 * through. Every profile in the list describes itself this way, which is what makes the list
 * readable without opening anything.
 *
 * **Rounding.** See [chaosOdds]. The odds are stated as the simplest fraction that is actually
 * close to the value, rather than by inverting it — 40% is "2 requests in 5", not the "1 in 2"
 * that inverting and rounding produces, which would overstate it by ten points. Anything at or
 * above [CERTAIN_THRESHOLD] drops the odds entirely and says *every* request, because "1 in 1"
 * reads as a rounding artefact rather than as certainty.
 *
 * **Only the loudest failure is named.** At most one failure fires per request (SPEC §6), so
 * listing three probabilities would describe a request that cannot happen. The highest one wins
 * the sentence.
 */
@Composable
fun chaosDescription(profile: ChaosProfile): String {
    val failure = profile.loudestFailure()
    val delay = profile.delay.describe()

    return when {
        failure == null && delay == null -> stringResource(R.string.chaos_desc_clean)
        failure == null -> stringResource(R.string.chaos_desc_sentence, delay!!, stringResource(R.string.chaos_desc_nothing_fails))
        delay == null -> failure
        else -> stringResource(R.string.chaos_desc_sentence, failure, delay)
    }
}

/** The failure mode most likely to fire, described — or `null` when the profile breaks nothing. */
@Composable
private fun ChaosProfile.loudestFailure(): String? {
    val error = errorInjection.probability
    val reset = connectionReset.probability
    val malformed = malformedBody.probability
    val loudest = maxOf(error, reset, malformed)
    if (loudest <= 0.0) return null

    if (loudest >= CERTAIN_THRESHOLD) {
        return when (loudest) {
            error -> stringResource(R.string.chaos_desc_error_all)
            reset -> stringResource(R.string.chaos_desc_reset_all)
            else -> stringResource(R.string.chaos_desc_malformed_all)
        }
    }

    val (n, d) = chaosOdds(loudest)
    val plural = when (loudest) {
        error -> R.plurals.chaos_desc_error
        reset -> R.plurals.chaos_desc_reset
        else -> R.plurals.chaos_desc_malformed
    }
    return pluralStringResource(plural, n, n, d)
}

/**
 * A probability as the simplest honest fraction: 0.4 becomes 2 in 5, 0.1 becomes 1 in 10.
 *
 * Inverting the odds and rounding — the obvious approach — is exact only when the probability
 * happens to be 1/n. At 40% it produces "1 in 2", overstating the failure rate by ten points on
 * a screen whose whole job is telling you what to expect. This walks denominators upward and
 * takes the first fraction within [ODDS_TOLERANCE] of the real value, so the number said out
 * loud is one the slider can actually produce.
 *
 * Falls back to hundredths, which is exact for anything the slider emits and reads as a
 * percentage in words.
 */
fun chaosOdds(probability: Double): Pair<Int, Int> {
    for (denominator in MIN_ODDS..MAX_ODDS) {
        val numerator = (probability * denominator).roundToInt()
        if (numerator < 1 || numerator >= denominator) continue
        if (abs(numerator.toDouble() / denominator - probability) <= ODDS_TOLERANCE) {
            return numerator to denominator
        }
    }
    return (probability * HUNDRED).roundToInt().coerceAtLeast(1) to HUNDRED
}

/** "Everything waits 400 ms", or `null` when the profile adds no delay. */
@Composable
private fun DelaySpec.describe(): String? = when (this) {
    DelaySpec.None -> null
    is DelaySpec.Fixed -> if (millis <= 0L) {
        null
    } else {
        stringResource(R.string.chaos_desc_delay, formatDuration(millis))
    }

    is DelaySpec.Random -> stringResource(
        R.string.chaos_desc_delay_range,
        formatDuration(minMillis),
        formatDuration(maxMillis),
    )
}

/**
 * Milliseconds under a second, whole seconds above it.
 *
 * "2000 ms" is a number to convert; "2 s" is a wait to imagine. The half-second case keeps one
 * decimal rather than rounding to a whole, because 1.5 s and 2 s feel measurably different.
 */
@Composable
private fun formatDuration(millis: Long): String = if (millis < MILLIS_PER_SECOND) {
    stringResource(R.string.chaos_duration_ms, millis)
} else {
    val seconds = millis.toDouble() / MILLIS_PER_SECOND
    val rendered = if (seconds % 1.0 == 0.0) {
        seconds.toInt().toString()
    } else {
        ((seconds * TENTHS).roundToInt() / TENTHS.toDouble()).toString()
    }
    stringResource(R.string.chaos_duration_seconds, rendered)
}

/** At and above this, the sentence says "every request" rather than inventing odds. */
private const val CERTAIN_THRESHOLD = 0.95

/** "1 in 1" would read as a bug, so the denominator never goes below this. */
private const val MIN_ODDS = 2

/** Past this, "3 in 23" is arithmetic rather than a thing anyone pictures. */
private const val MAX_ODDS = 20

/** Two percentage points: closer than the slider's own precision is worth claiming. */
private const val ODDS_TOLERANCE = 0.02

private const val HUNDRED = 100

private const val MILLIS_PER_SECOND = 1000L
private const val TENTHS = 10
