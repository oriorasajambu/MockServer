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
    data class Export(
        val format: ExportFormat,
        val destination: ExportDestination,
    ) : CollectionDetailIntent
}

/** The two wire formats a collection can leave in (SPEC §9.2). */
internal enum class ExportFormat {
    /** Full fidelity: matchers, chaos overrides and CORS all survive. */
    NATIVE_JSON,

    /** Postman v2.1. Lossy, and the user is told what will be dropped before it is written. */
    POSTMAN,
}

/**
 * Where an export goes.
 *
 * Both, rather than one: a share sends the collection to a colleague, a file keeps it somewhere
 * it can be found again and re-imported. Offering only the share sheet makes "back up my mocks"
 * a matter of mailing yourself a message.
 */
internal enum class ExportDestination { SHARE, FILE }

internal sealed interface CollectionDetailEffect : UiEffect {
    data class NavigateToEditor(val routeId: String?) : CollectionDetailEffect
    data class ShareText(val text: String, val subject: String) : CollectionDetailEffect

    /** Write the export to a file the user picks, through the storage-access framework. */
    data class SaveToFile(val fileName: String, val content: String) : CollectionDetailEffect

    /** Postman is lossy; the warnings name what will actually be dropped (SPEC §9.2). */
    data class ConfirmLossyExport(val warnings: List<String>) : CollectionDetailEffect
    data class ShowError(val error: DomainError) : CollectionDetailEffect
}
