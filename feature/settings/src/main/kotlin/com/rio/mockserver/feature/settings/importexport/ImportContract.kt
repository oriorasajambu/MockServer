package com.rio.mockserver.feature.settings.importexport

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.mvi.UiEffect
import com.rio.mockserver.core.common.mvi.UiIntent
import com.rio.mockserver.core.common.mvi.UiState
import com.rio.mockserver.core.domain.model.ImportConflict
import com.rio.mockserver.core.domain.model.ImportPreview
import com.rio.mockserver.core.domain.model.MockCollection

/**
 * The import flow (SPEC §9.1): pick → parse → preview → resolve conflicts → commit.
 *
 * A preview step rather than a straight import, because "let me see what's in this file" should
 * not be a destructive action — and because a 400-operation OpenAPI spec is almost never wanted
 * in its entirety.
 */
internal data class ImportState(
    val isParsing: Boolean = false,
    val preview: ImportPreview? = null,

    /** Route ids the user has left checked. Everything starts checked. */
    val selectedRouteIds: Set<String> = emptySet(),

    /** Existing collections, for the target picker. */
    val collections: List<MockCollection> = emptyList(),

    /** `null` means "create a new collection from the file's own title". */
    val targetCollectionId: String? = null,

    val newCollectionName: String = "",
    val newCollectionBasePath: String = "/",

    val conflicts: List<ImportConflict> = emptyList(),

    val error: DomainError? = null,
    val isCommitting: Boolean = false,
) : UiState {

    val canCommit: Boolean
        get() = preview != null && selectedRouteIds.isNotEmpty() && !isCommitting

    /** The routes that will actually be written, after unchecking and conflict resolution. */
    val selectedCount: Int get() = selectedRouteIds.size
}

internal sealed interface ImportIntent : UiIntent {
    data object PickFile : ImportIntent
    data class FileRead(val content: String, val fileName: String?) : ImportIntent
    data class ToggleRoute(val routeId: String) : ImportIntent
    data object SelectAll : ImportIntent
    data object SelectNone : ImportIntent
    data class SelectTargetCollection(val collectionId: String?) : ImportIntent
    data class UpdateNewCollectionName(val name: String) : ImportIntent
    data class UpdateNewCollectionBasePath(val basePath: String) : ImportIntent
    data class ResolveConflict(
        val routeId: String,
        val resolution: ImportConflict.Resolution,
    ) : ImportIntent

    data object Commit : ImportIntent
}

internal sealed interface ImportEffect : UiEffect {
    data object OpenFilePicker : ImportEffect
    data class NavigateToCollection(val collectionId: String) : ImportEffect
    data class ShowError(val error: DomainError) : ImportEffect
    data class ShowImported(val count: Int) : ImportEffect
}
