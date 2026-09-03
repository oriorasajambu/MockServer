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

    /**
     * The rows as drawn: consecutive identical requests folded into one.
     *
     * Folded here rather than in the repository, which answers the handoff's open question 04.
     * A poll firing every second is most of what this log ever holds, and six hundred identical
     * rows bury the one request that went wrong. The count is of entries *in the log* — if the
     * ring buffer has evicted some of a run, the badge shrinks with it, which is the honest
     * reading of a capped log rather than a total the buffer can no longer support.
     */
    val groups: List<TrafficGroup> = emptyList(),

    /** Unfiltered totals, for the tab-bar count and the filter chips. */
    val totalCount: Int = 0,
    val unmatchedCount: Int = 0,
    val chaosCount: Int = 0,
) : UiState {

    /** True when nothing has arrived yet, as opposed to everything being filtered out. */
    val isEmpty: Boolean get() = entries.isEmpty() && filter.isEmpty

    /** True when there is traffic but the filter hides all of it. */
    val isFilteredEmpty: Boolean get() = entries.isEmpty() && !filter.isEmpty
}

/** One row of the log, and how many identical requests it stands for. */
internal data class TrafficGroup(
    val entry: TrafficEntry,
    val repeatCount: Int,
)

internal sealed interface TrafficIntent : UiIntent {
    data class UpdateSearch(val query: String) : TrafficIntent
    data class ToggleMethodFilter(val method: String) : TrafficIntent
    data class ToggleStatusClassFilter(val statusClass: Int) : TrafficIntent
    data class SetUnmatchedFilter(val unmatchedOnly: Boolean?) : TrafficIntent
    data class SetChaosFilter(val chaosOnly: Boolean?) : TrafficIntent

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
