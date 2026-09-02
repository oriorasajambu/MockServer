package com.rio.mockserver.feature.traffic

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.mvi.UiEffect
import com.rio.mockserver.core.common.mvi.UiIntent
import com.rio.mockserver.core.common.mvi.UiState
import com.rio.mockserver.core.domain.model.TrafficEntry
import com.rio.mockserver.core.domain.usecase.traffic.ObserveTrafficUseCase

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
    data object ClearFilters : TrafficIntent
    data object ToggleFilterSheet : TrafficIntent
    data object PauseFollow : TrafficIntent
    data object ResumeFollow : TrafficIntent
    data object ClearLog : TrafficIntent
    data object ExportHar : TrafficIntent
    data class OpenEntry(val entryId: String) : TrafficIntent
    data class CreateRuleFrom(val entryId: String) : TrafficIntent
    data class CopyAsCurl(val entryId: String) : TrafficIntent
}

internal sealed interface TrafficEffect : UiEffect {
    data class CopiedToClipboard(val text: String) : TrafficEffect
    data class ShareText(val text: String, val subject: String) : TrafficEffect
    data class NavigateToDetail(val entryId: String) : TrafficEffect
    data class NavigateToRouteEditor(val entryId: String) : TrafficEffect
    data class ShowError(val error: DomainError) : TrafficEffect
}
