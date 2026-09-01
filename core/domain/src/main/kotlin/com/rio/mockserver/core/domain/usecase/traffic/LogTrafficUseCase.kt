package com.rio.mockserver.core.domain.usecase.traffic

import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.domain.model.TrafficEntry
import com.rio.mockserver.core.domain.repository.TrafficRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caps an entry's bodies and hands it to the log (SPEC §7.1).
 *
 * Not `suspend`, deliberately. The request path must never wait on logging: this caps, offers to
 * a buffered channel and returns. If the drain cannot keep up, the *log* loses entries — the
 * server does not lose throughput — and the dropped count is surfaced in the UI so the loss is
 * visible rather than silent.
 */
@Singleton
class LogTrafficUseCase @Inject constructor(
    private val trafficRepository: TrafficRepository,
) {

    /** Caps and records [entry]. Returns immediately. */
    operator fun invoke(entry: TrafficEntry) {
        trafficRepository.offer(entry.capped())
    }

    private fun TrafficEntry.capped(): TrafficEntry {
        val request = truncate(requestBody)
        val response = truncate(responseBody)
        // Untouched when neither body needed capping, so the common case allocates nothing.
        if (request == null && response == null) return this
        return copy(
            requestBody = request?.text ?: requestBody,
            requestTruncated = requestTruncated || request != null,
            responseBody = response?.text ?: responseBody,
            responseTruncated = responseTruncated || response != null,
        )
    }

    private class Truncated(val text: String)

    /**
     * Cuts [body] to the per-direction cap, or returns `null` when it already fits.
     *
     * The cheap length check first: UTF-8 uses at most three bytes per UTF-16 code unit, so a
     * string shorter than a third of the cap cannot possibly exceed it, and the overwhelming
     * majority of bodies never get encoded to be measured.
     */
    private fun truncate(body: String?): Truncated? {
        if (body == null) return null
        val cap = AppConstants.Traffic.MAX_LOGGED_BODY_BYTES
        if (body.length <= cap / MAX_UTF8_BYTES_PER_CHAR) return null

        val bytes = body.toByteArray(Charsets.UTF_8)
        if (bytes.size <= cap) return null
        return Truncated(String(bytes, 0, cap, Charsets.UTF_8))
    }

    private companion object {
        const val MAX_UTF8_BYTES_PER_CHAR = 3
    }
}
