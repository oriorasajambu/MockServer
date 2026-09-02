package com.minion.mockserver.core.domain.usecase.traffic

import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.repository.ExportGateway
import com.minion.mockserver.core.domain.repository.TrafficRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * The live log, filtered (SPEC §7.3).
 *
 * Filtering in memory rather than through an index: at 500 entries a linear scan across four
 * string fields is imperceptible, and an index would be a second source of truth to keep in sync
 * with a buffer that is constantly dropping its own tail.
 */
@Singleton
class ObserveTrafficUseCase @Inject constructor(
    private val repository: TrafficRepository,
) {

    /** What the traffic screen's chips and search box add up to. */
    data class Filter(
        /** Empty means every method. */
        val methods: Set<String> = emptySet(),

        /** 2, 3, 4, 5 — the leading digit of the status. Empty means every class. */
        val statusClasses: Set<Int> = emptySet(),

        /** `true` shows only unmatched, `false` only matched, `null` both. */
        val unmatchedOnly: Boolean? = null,

        /** Restricts to entries answered by a route in this collection. */
        val collectionRouteIds: Set<String>? = null,

        /** Full-text across path, headers and both bodies. Case-insensitive. */
        val query: String = "",
    ) {
        /** Whether this filter would remove anything. Skips the scan entirely when it would not. */
        val isEmpty: Boolean
            get() = methods.isEmpty() && statusClasses.isEmpty() && unmatchedOnly == null &&
                collectionRouteIds == null && query.isBlank()
    }

    /** Every entry, newest first. */
    operator fun invoke(): StateFlow<List<TrafficEntry>> = repository.entries

    /** Entries passing [filter], newest first. */
    fun filtered(filter: Flow<Filter>): Flow<List<TrafficEntry>> = filter.let { filters ->
        kotlinx.coroutines.flow.combine(repository.entries, filters) { entries, current ->
            if (current.isEmpty) entries else entries.filter { it.matches(current) }
        }
    }

    /** Entries dropped because the log could not keep up (SPEC §7.1). */
    fun droppedCount(): StateFlow<Int> = repository.droppedCount

    /** How many of the current entries matched no route, for the server screen's counter. */
    fun unmatchedCount(): Flow<Int> = repository.entries.map { list -> list.count { it.isUnmatched } }

    private fun TrafficEntry.matches(filter: Filter): Boolean {
        if (filter.methods.isNotEmpty() && method !in filter.methods) return false
        if (filter.statusClasses.isNotEmpty() && statusClass !in filter.statusClasses) return false
        if (filter.unmatchedOnly != null && isUnmatched != filter.unmatchedOnly) return false
        if (filter.collectionRouteIds != null && matchedRouteId !in filter.collectionRouteIds) return false
        if (filter.query.isNotBlank() && !containsText(filter.query)) return false
        return true
    }

    private fun TrafficEntry.containsText(query: String): Boolean =
        path.contains(query, ignoreCase = true) ||
            method.contains(query, ignoreCase = true) ||
            matchedRouteName?.contains(query, ignoreCase = true) == true ||
            requestBody?.contains(query, ignoreCase = true) == true ||
            responseBody?.contains(query, ignoreCase = true) == true ||
            requestHeaders.any { (name, values) ->
                name.contains(query, ignoreCase = true) ||
                    values.any { it.contains(query, ignoreCase = true) }
            } ||
            responseHeaders.any { (name, value) ->
                name.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)
            }
}

/** Empties the log and resets the dropped counter. */
class ClearTrafficUseCase @Inject constructor(
    private val repository: TrafficRepository,
) {
    suspend operator fun invoke() = repository.clear()
}

/**
 * Serialises the currently-filtered entries to HAR 1.2 (SPEC §7.4).
 *
 * The *filtered* set, not everything: exporting a session usually means exporting the ten
 * requests you were looking at, and a 500-entry file to find them in is not that.
 */
class ExportTrafficAsHarUseCase @Inject constructor(
    private val exportGateway: ExportGateway,
) {
    operator fun invoke(entries: List<TrafficEntry>, baseUrl: String): String =
        exportGateway.toHar(entries, baseUrl)
}

/** A cURL reproduction of one entry, against the address currently on screen (SPEC §7.4). */
class FormatEntryAsCurlUseCase @Inject constructor(
    private val exportGateway: ExportGateway,
) {
    operator fun invoke(entry: TrafficEntry, baseUrl: String): String =
        exportGateway.toCurl(entry, baseUrl)
}

/** A shareable request/response transcript for one entry. */
class FormatEntryAsTextUseCase @Inject constructor(
    private val exportGateway: ExportGateway,
) {
    operator fun invoke(entry: TrafficEntry, baseUrl: String): String =
        exportGateway.toTranscript(entry, baseUrl)
}
