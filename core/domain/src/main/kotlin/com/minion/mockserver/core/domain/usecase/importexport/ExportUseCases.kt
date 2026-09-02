package com.minion.mockserver.core.domain.usecase.importexport

import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.repository.ChaosRepository
import com.minion.mockserver.core.domain.repository.CollectionRepository
import com.minion.mockserver.core.domain.repository.ExportGateway
import com.minion.mockserver.core.domain.repository.RouteRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The app's own export format: full fidelity, versioned (SPEC §9.2).
 *
 * Everything survives — matchers, chaos overrides, CORS — and `schemaVersion` is what lets a
 * future release migrate a file written by this one instead of refusing it.
 */
class ExportCollectionJsonUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val routeRepository: RouteRepository,
    private val chaosRepository: ChaosRepository,
    private val exportGateway: ExportGateway,
) {

    /** One collection and its routes. */
    suspend operator fun invoke(collectionId: String): AppResult<String> {
        val collection = when (val result = collectionRepository.get(collectionId)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val routes = routeRepository.observeRoutes(collectionId).first()
        return AppResult.Success(
            exportGateway.toNativeJson(
                collections = listOf(collection),
                routes = routes,
                chaosProfiles = referencedProfiles(routes),
            ),
        )
    }

    /** Everything, for a full backup. */
    suspend fun all(): AppResult<String> {
        val collections = collectionRepository.observeCollections().first()
        val routes = routeRepository.observeAllRoutes().first()
        val profiles = chaosRepository.observeProfiles().first()
        return AppResult.Success(exportGateway.toNativeJson(collections, routes, profiles))
    }

    /**
     * Only the profiles the exported routes actually override with.
     *
     * Exporting every saved profile alongside one collection would import a stranger's whole
     * chaos library into someone else's app.
     */
    private fun referencedProfiles(routes: List<MockRoute>): List<ChaosProfile> =
        routes.mapNotNull { it.chaosOverride }.distinctBy { it.id }
}

/**
 * Postman v2.1 export — lossy, and honest about it (SPEC §9.2).
 *
 * [lossWarnings] is generated from the routes being exported rather than being a static sentence,
 * so it names the actual matchers and chaos overrides that will not survive. A static warning
 * goes stale and gets dismissed without reading.
 */
class ExportPostmanCollectionUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val routeRepository: RouteRepository,
    private val exportGateway: ExportGateway,
) {

    suspend operator fun invoke(collectionId: String): AppResult<String> {
        val collection = when (val result = collectionRepository.get(collectionId)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val routes = routeRepository.observeRoutes(collectionId).first()
        return AppResult.Success(exportGateway.toPostmanCollection(collection, routes))
    }

    /** Exactly what this export will drop. Shown before the file is written. */
    suspend fun lossWarnings(collectionId: String): List<String> =
        exportGateway.postmanLossWarnings(routeRepository.observeRoutes(collectionId).first())
}
