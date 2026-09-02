package com.minion.mockserver.feature.routes.collections

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.mvi.UiEffect
import com.minion.mockserver.core.common.mvi.UiIntent
import com.minion.mockserver.core.common.mvi.UiState
import com.minion.mockserver.core.domain.model.MockCollection

/** The Routes tab's top level: the collection list. */
internal data class CollectionsState(
    val collections: List<MockCollection> = emptyList(),

    /** Route count per collection id, for the subtitle. */
    val routeCounts: Map<String, Int> = emptyMap(),

    /** Routes imported without a body, per collection — the "N need bodies" banner (SPEC §9.1). */
    val needsBodyCounts: Map<String, Int> = emptyMap(),

    val isLoading: Boolean = true,

    /** The new/edit sheet, or `null` when it is closed. */
    val editing: Draft? = null,
) : UiState {

    /** The collection being created or edited, before it is validated and saved. */
    data class Draft(
        val id: String = "",
        val name: String = "",
        val basePath: String = "/",
        val corsEnabled: Boolean = false,
        val allowedOrigins: String = "*",
        val allowCredentials: Boolean = false,
        val error: DomainError? = null,
    ) {
        /** True when the collection is new, which changes the sheet's title and its save verb. */
        val isNew: Boolean get() = id.isBlank()
    }

    val isEmpty: Boolean get() = !isLoading && collections.isEmpty()
}

internal sealed interface CollectionsIntent : UiIntent {
    data object NewCollection : CollectionsIntent
    data class EditCollection(val id: String) : CollectionsIntent
    data class OpenCollection(val id: String) : CollectionsIntent
    data class ToggleCollection(val id: String, val isEnabled: Boolean) : CollectionsIntent
    data class DeleteCollection(val id: String) : CollectionsIntent
    data class UpdateDraftName(val name: String) : CollectionsIntent
    data class UpdateDraftBasePath(val basePath: String) : CollectionsIntent
    data class UpdateDraftCors(val enabled: Boolean) : CollectionsIntent
    data class UpdateDraftOrigins(val origins: String) : CollectionsIntent
    data class UpdateDraftCredentials(val allow: Boolean) : CollectionsIntent
    data object SaveDraft : CollectionsIntent
    data object DismissDraft : CollectionsIntent
}

internal sealed interface CollectionsEffect : UiEffect {
    data class NavigateToCollection(val id: String) : CollectionsEffect
    data class ShowError(val error: DomainError) : CollectionsEffect
}
