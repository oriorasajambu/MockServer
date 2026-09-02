package com.rio.mockserver.core.domain.model

import com.rio.mockserver.core.common.config.AppConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A named bundle of things that can go wrong (SPEC §6).
 *
 * Scope resolution is `route.chaosOverride ?: globalChaosProfile` — a route override replaces the
 * global profile *wholesale*. Merging field by field is what produces "why is this one endpoint
 * sleeping three seconds" bug hunts, where the answer is a global delay nobody remembered was on.
 */
@Serializable
data class ChaosProfile(
    val id: String,
    val name: String,

    /** Added latency. Orthogonal to the failure modes — see [hasFailureModes]. */
    val delay: DelaySpec = DelaySpec.None,

    /** Probability of answering with an error status instead of the route's response. */
    val errorInjection: ErrorInjection = ErrorInjection.OFF,

    /** Probability of killing the socket mid-body. */
    val connectionReset: ConnectionResetSpec = ConnectionResetSpec.OFF,

    /** Probability of sending a truncated body under a full-length `Content-Length`. */
    val malformedBody: MalformedBodySpec = MalformedBodySpec.OFF,
) {

    /**
     * Whether any of the three mutually-exclusive failure modes can fire.
     *
     * Delay is excluded on purpose: a profile that only adds latency is not "chaos on" in the
     * sense the banner means, and flagging it as such would train people to ignore the banner.
     */
    val hasFailureModes: Boolean
        get() = errorInjection.probability > 0.0 ||
            connectionReset.probability > 0.0 ||
            malformedBody.probability > 0.0

    /** Whether this profile does anything at all. An empty profile is treated as no profile. */
    val isActive: Boolean
        get() = hasFailureModes || delay != DelaySpec.None

    companion object {

        /** Id of the built-in "no chaos" profile, which cannot be deleted. */
        const val NONE_ID = "chaos-none"

        /** The profile a fresh install starts on: everything off. */
        val NONE = ChaosProfile(id = NONE_ID, name = "None")
    }
}

/**
 * How long to wait before answering (SPEC §6.2 step 1).
 *
 * Each case carries an explicit `@SerialName` for the same reason [RequestMatcher]'s do: the
 * default discriminator is the fully-qualified class name, and a package move would then break
 * every file already exported.
 */
@Serializable
sealed interface DelaySpec {

    /** Answer as fast as the device can. */
    @Serializable
    @SerialName("none")
    data object None : DelaySpec

    /** Always wait [millis]. */
    @Serializable
    @SerialName("fixed")
    data class Fixed(val millis: Long) : DelaySpec

    /** Wait a uniformly-drawn number of millis in `[minMillis, maxMillis]`, inclusive. */
    @Serializable
    @SerialName("random")
    data class Random(val minMillis: Long, val maxMillis: Long) : DelaySpec
}

/**
 * Probability of replacing the route's response with an error drawn from a weighted pool.
 *
 * The pool is weighted rather than uniform so "mostly 503, occasionally 500" is expressible —
 * which is what a real degrading backend looks like.
 */
@Serializable
data class ErrorInjection(
    /** 0.0..1.0. Zero means off. */
    val probability: Double = 0.0,

    /** Statuses and their relative weights. An empty pool is treated as off. */
    val statuses: List<WeightedStatus> = DEFAULT_POOL,

    /** Body sent with an injected error. */
    val body: String = AppConstants.Chaos.DEFAULT_ERROR_BODY,

    /** Value of `Retry-After` on an injected `429`. */
    val retryAfterSeconds: Int = AppConstants.Chaos.DEFAULT_RETRY_AFTER_SECONDS,
) {
    companion object {

        /** 500 / 503 / 429 at equal weight, per SPEC §6.2. */
        val DEFAULT_POOL = AppConstants.Chaos.DEFAULT_ERROR_STATUSES.map { WeightedStatus(it, weight = 1) }

        /** Error injection disabled. */
        val OFF = ErrorInjection()
    }
}

/** One entry in an [ErrorInjection] pool. */
@Serializable
data class WeightedStatus(
    val status: Int,

    /** Relative weight. Non-positive weights are skipped rather than treated as an error. */
    val weight: Int = 1,
)

/**
 * Probability of writing part of the body and then killing the socket (SPEC §6.2 step 3).
 *
 * A distinct type from [MalformedBodySpec] even though both are "a probability and a number":
 * one number is bytes and the other is a percentage, and a shared `FailureSpec` would make
 * swapping them a silent behaviour change instead of a compile error.
 */
@Serializable
data class ConnectionResetSpec(
    /** 0.0..1.0. Zero means off. */
    val probability: Double = 0.0,

    /** Bytes written before the channel is cancelled. Zero closes before anything is sent. */
    val dropAfterBytes: Int = 0,
) {
    companion object {
        /** Connection resets disabled. */
        val OFF = ConnectionResetSpec()
    }
}

/**
 * Probability of sending a truncated body under a `Content-Length` that declares the full length
 * (SPEC §6.2 step 4).
 *
 * Strict clients report an incomplete body; lenient ones report a JSON parse error. Both are
 * failures worth being able to reproduce on demand.
 */
@Serializable
data class MalformedBodySpec(
    /** 0.0..1.0. Zero means off. */
    val probability: Double = 0.0,

    /** 0..100. The percentage of the body actually written. */
    val truncateAtPercent: Int = DEFAULT_TRUNCATE_PERCENT,
) {
    companion object {

        /** Half a body is enough to break a parser and short enough to be obviously wrong. */
        const val DEFAULT_TRUNCATE_PERCENT = 50

        /** Malformed bodies disabled. */
        val OFF = MalformedBodySpec()
    }
}
