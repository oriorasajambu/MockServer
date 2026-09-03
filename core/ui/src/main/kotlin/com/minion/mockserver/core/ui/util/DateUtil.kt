package com.minion.mockserver.core.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Timestamp formatting for the traffic log.
 *
 * No date: a log covering one debugging session never spans one. Two precisions, because the two
 * places this appears are asking different questions — a row is scanned, an entry is compared.
 */
object DateUtil {

    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    private val SHORT_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    /**
     * `14:03:22.481`, for one entry's detail.
     *
     * Milliseconds are the resolution at which two requests are actually distinguishable, which
     * is what you are asking once you have opened one.
     */
    fun formatTime(epochMillis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))

    /**
     * `14:03:22`, for a log row.
     *
     * The four characters milliseconds cost come straight out of the path beside them, which on a
     * row carrying a repeat badge is the difference between `/api/v1/users/42` and `/api/v…rs/42`.
     * Nobody distinguishes two requests by eye while scrolling; they open one.
     */
    fun formatTimeShort(epochMillis: Long): String =
        SHORT_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))

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
