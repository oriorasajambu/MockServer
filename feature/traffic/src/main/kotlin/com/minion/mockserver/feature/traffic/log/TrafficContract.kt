package com.minion.mockserver.feature.traffic.log

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.mvi.UiEffect
import com.minion.mockserver.core.common.mvi.UiIntent
import com.minion.mockserver.core.common.mvi.UiState
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.usecase.traffic.ObserveTrafficUseCase

/**
 * The Traffic tab's contract (SPEC §7.3).
 *
 * Auto-follow is the behaviour worth reading carefully. New entries stream in at the top, and
 * touching the list to scroll *pauses* following rather than fighting the user's finger — the
 * alternative is a list that yanks itself back to the top every time a request arrives, which
 * makes reading anything older than three seconds impossible. [newEntryCount] then drives the
 * "N new — jump to latest" pill.
 */
internal data class TrafficState(
    val entries: List<TrafficEntry> = emptyList(),
    val filter: ObserveTrafficUseCase.Filter = ObserveTrafficUseCase.Filter(),

    /** Bound to the search field. Debounced before it reaches [filter]. */
    val searchQuery: String = "",

    /** Whether the list snaps to the newest entry. Paused by scrolling away from the top. */
    val isFollowing: Boolean = true,

    /** Entries that arrived while following was paused. */
    val newEntryCount: Int = 0,

    /** Entries the log could not keep up with, surfaced so the loss is visible (SPEC §7.1). */
    val droppedCount: Int = 0,

    /** Whether the filter sheet is open. */
    val isFilterSheetOpen: Boolean = false,

    /** The address cURL and HAR exports are written against — whatever the server screen shows. */
    val baseUrl: String = "",

    /** Every collection, for the collection selector (SPEC §7.3). */
    val collections: List<MockCollection> = emptyList(),

    /**
     * The collection the log is restricted to, or `null` for all of them.
     *
     * Held as an id beside the filter rather than being derived from it: the filter carries the
     * route ids the selection expands to, and route ids cannot be turned back into the
     * collection that produced them once a route has been deleted.
     */
    val selectedCollectionId: String? = null,
) : UiState {

    /** True when nothing has arrived yet, as opposed to everything being filtered out. */
    val isEmpty: Boolean get() = entries.isEmpty() && filter.isEmpty

    /** True when there is traffic but the filter hides all of it. */
    val isFilteredEmpty: Boolean get() = entries.isEmpty() && !filter.isEmpty
}

internal sealed interface TrafficIntent : UiIntent {
    data class UpdateSearch(val query: String) : TrafficIntent
    data class ToggleMethodFilter(val method: String) : TrafficIntent
    data class ToggleStatusClassFilter(val statusClass: Int) : TrafficIntent
    data class SetUnmatchedFilter(val unmatchedOnly: Boolean?) : TrafficIntent

    /** `null` clears the restriction and shows every collection's traffic again. */
    data class SetCollectionFilter(val collectionId: String?) : TrafficIntent
    data object ClearFilters : TrafficIntent
    data object ToggleFilterSheet : TrafficIntent
    data object PauseFollow : TrafficIntent
    data object ResumeFollow : TrafficIntent
    data object ClearLog : TrafficIntent
    /** Hands the filtered session to the share sheet, for sending it somewhere. */
    data object ExportHar : TrafficIntent

    /** Writes the filtered session to a file, for opening it in a HAR viewer (SPEC §7.4). */
    data object SaveHarToFile : TrafficIntent
    data class OpenEntry(val entryId: String) : TrafficIntent
    data class CreateRuleFrom(val entryId: String) : TrafficIntent
    data class CopyAsCurl(val entryId: String) : TrafficIntent
}

internal sealed interface TrafficEffect : UiEffect {
    data class CopiedToClipboard(val text: String) : TrafficEffect
    data class ShareText(val text: String, val subject: String) : TrafficEffect

    /**
     * Write the HAR to a file the user picks (SPEC §7.4).
     *
     * The share sheet is not enough on its own here: a session export exists to be opened in
     * Charles, Fiddler or a browser's devtools importer, and none of those take a message body.
     */
    data class SaveToFile(val fileName: String, val content: String) : TrafficEffect
    data class NavigateToDetail(val entryId: String) : TrafficEffect
    data class NavigateToRouteEditor(val entryId: String) : TrafficEffect
    data class ShowError(val error: DomainError) : TrafficEffect
}
