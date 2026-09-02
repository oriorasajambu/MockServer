package com.rio.mockserver.feature.routes.detail

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.mvi.UiEffect
import com.rio.mockserver.core.common.mvi.UiIntent
import com.rio.mockserver.core.common.mvi.UiState
import com.rio.mockserver.core.domain.model.MockRoute

/** The routes inside one collection. */
internal data class CollectionDetailState(
    val collectionName: String = "",
    val basePath: String = "",
    val routes: List<MockRoute> = emptyList(),

    /**
     * Route id → the id of the route that always beats it (SPEC §4.3).
     *
     * The **Shadowed** chip. A rule that is right there in the list, looks correct, and can never
     * fire is the failure mode nobody notices unaided.
     */
    val shadowedBy: Map<String, String> = emptyMap(),

    val isLoading: Boolean = true,
) : UiState {

    val needsBodyCount: Int get() = routes.count { it.needsBody }

    val isEmpty: Boolean get() = !isLoading && routes.isEmpty()
}

internal sealed interface CollectionDetailIntent : UiIntent {
    data object NewRoute : CollectionDetailIntent
    data class OpenRoute(val id: String) : CollectionDetailIntent
    data class ToggleRoute(val id: String, val isEnabled: Boolean) : CollectionDetailIntent
    data class DuplicateRoute(val id: String) : CollectionDetailIntent
    data class DeleteRoute(val id: String) : CollectionDetailIntent
    data object ExportJson : CollectionDetailIntent
    data object ExportPostman : CollectionDetailIntent
}

internal sealed interface CollectionDetailEffect : UiEffect {
    data class NavigateToEditor(val routeId: String?) : CollectionDetailEffect
    data class ShareText(val text: String, val subject: String) : CollectionDetailEffect

    /** Postman is lossy; the warnings name what will actually be dropped (SPEC §9.2). */
    data class ConfirmLossyExport(val warnings: List<String>) : CollectionDetailEffect
    data class ShowError(val error: DomainError) : CollectionDetailEffect
}
