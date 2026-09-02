package com.rio.mockserver.core.domain.usecase.importexport

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.ImportConflict
import com.rio.mockserver.core.domain.model.ImportFormat
import com.rio.mockserver.core.domain.model.ImportPreview
import com.rio.mockserver.core.domain.model.ImportedRoute
import com.rio.mockserver.core.domain.model.MockCollection
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.domain.model.PathNormaliser
import com.rio.mockserver.core.domain.repository.ImportGateway
import com.rio.mockserver.core.domain.usecase.collection.SaveCollectionUseCase
import com.rio.mockserver.core.domain.usecase.route.SaveRoutesUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses an import file into a preview (SPEC §9.1).
 *
 * Detection is by content, with the file name only as a tiebreak: all three formats routinely
 * arrive named `export.json`, and a `.json` extension says nothing about which one it is.
 */
@Singleton
class ParseImportUseCase @Inject constructor(
    private val gateway: ImportGateway,
) {

    suspend operator fun invoke(content: String, fileName: String? = null): AppResult<ImportPreview> {
        val format = gateway.detectFormat(content, fileName)
            ?: return AppResult.Failure(DomainError.UnsupportedImportFormat)
        return gateway.parse(content, format)
    }

    /** Parses as a specific format, for the "it's actually OpenAPI" override in the picker. */
    suspend fun asFormat(content: String, format: ImportFormat): AppResult<ImportPreview> =
        gateway.parse(content, format)
}

/**
 * Finds routes in a preview that collide with what is already saved (SPEC §9.1).
 *
 * Collision is method + fully-resolved path, which is what makes two rules fight for the same
 * traffic. A different name or body is not a conflict — it is an edit, and treating it as a
 * conflict would put a three-way choice in front of the user on every re-import.
 */
@Singleton
class DetectImportConflictsUseCase @Inject constructor() {

    operator fun invoke(
        preview: ImportPreview,
        targetCollection: MockCollection,
        existingRoutes: List<MockRoute>,
    ): List<ImportConflict> {
        if (existingRoutes.isEmpty()) return emptyList()

        val existingByKey = existingRoutes.associateBy { route ->
            key(route.method.name, PathNormaliser.join(targetCollection.basePath, route.pathPattern))
        }

        return preview.routes.mapNotNull { incoming ->
            val incomingKey = key(
                incoming.route.method.name,
                PathNormaliser.join(targetCollection.basePath, incoming.route.pathPattern),
            )
            existingByKey[incomingKey]?.let { existing ->
                ImportConflict(incoming = incoming, existing = existing)
            }
        }
    }

    private fun key(method: String, path: String) = "$method $path"
}

/**
 * Writes a resolved import (SPEC §9.1).
 *
 * The collection is saved first so the routes have a valid foreign key, then every route goes in
 * as one transaction — a half-imported collection is worse than a failed import, because it looks
 * finished.
 */
@Singleton
class CommitImportUseCase @Inject constructor(
    private val saveCollection: SaveCollectionUseCase,
    private val saveRoutes: SaveRoutesUseCase,
) {

    /** What the preview screen resolved to, ready to write. */
    data class Plan(
        val collection: MockCollection,
        val routes: List<ImportedRoute>,
        val conflicts: List<ImportConflict>,
    )

    suspend operator fun invoke(plan: Plan): AppResult<Int> {
        val savedCollection = when (val result = saveCollection(plan.collection)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }

        val skippedIds = plan.conflicts
            .filter { it.resolution == ImportConflict.Resolution.SKIP }
            .map { it.incoming.route.id }
            .toSet()

        // A REPLACE keeps the existing route's id, so the insert overwrites it in place and
        // nothing that referenced it — a traffic entry, a chaos override — is orphaned.
        val replacementIds = plan.conflicts
            .filter { it.resolution == ImportConflict.Resolution.REPLACE }
            .associate { it.incoming.route.id to it.existing.id }

        val toSave = plan.routes
            .filter { it.isSelected && it.route.id !in skippedIds }
            .map { imported ->
                imported.route.copy(
                    id = replacementIds[imported.route.id] ?: imported.route.id,
                    collectionId = savedCollection.id,
                    // The statuses the source defined but this route did not take, carried onto
                    // the route so the editor can offer them long after the file is gone.
                    alternateResponses = imported.alternateResponses,
                )
            }

        return when (val result = saveRoutes(toSave)) {
            is AppResult.Success -> AppResult.Success(toSave.size)
            is AppResult.Failure -> result
        }
    }
}
