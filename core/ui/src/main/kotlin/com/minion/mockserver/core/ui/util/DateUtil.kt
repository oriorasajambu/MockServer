package com.minion.mockserver.core.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Timestamp formatting for the traffic log.
 *
 * `HH:mm:ss.SSS` and nothing else. A log covering one debugging session never spans a date, and
 * milliseconds are the resolution at which two requests are actually distinguishable — which is
 * the question anyone reads this column to answer.
 */
object DateUtil {

    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    /** `14:03:22.481`. */
    fun formatTime(epochMillis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))

    /**
     * `1h 04m`, `4m 12s`, `38s`.
     *
     * Uptime is glanced at, not read: the largest two units are all anyone takes from it, and
     * showing seconds alongside hours makes the number change under the eye for no purpose.
     */
    fun formatUptime(millis: Long): String {
        val totalSeconds = millis / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return when {
            hours > 0 -> "${hours}h ${minutes.pad()}m"
            minutes > 0 -> "${minutes}m ${seconds.pad()}s"
            else -> "${seconds}s"
        }
    }

    private fun Long.pad(): String = toString().padStart(2, '0')

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
