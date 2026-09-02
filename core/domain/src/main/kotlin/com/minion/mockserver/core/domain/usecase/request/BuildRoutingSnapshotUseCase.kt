package com.minion.mockserver.core.domain.usecase.request

import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.ServerSettings
import com.minion.mockserver.core.domain.repository.ChaosRepository
import com.minion.mockserver.core.domain.repository.CollectionRepository
import com.minion.mockserver.core.domain.repository.MockServerRepository
import com.minion.mockserver.core.domain.repository.RouteRepository
import com.minion.mockserver.core.domain.repository.ServerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rebuilds the routing snapshot whenever anything it depends on changes (SPEC §5.5).
 *
 * The four sources are combined rather than observed separately so a rebuild always sees a
 * consistent set. Observing them independently would produce a snapshot carrying the new chaos
 * profile and the old pause state whenever two writes land a frame apart — which is exactly the
 * kind of intermittent wrongness nobody can reproduce.
 *
 * The result is a cold [Flow]; whoever collects it decides on which dispatcher the compile
 * happens. `:core:data` collects it on `Dispatchers.Default` and hands each snapshot to
 * [MockServerRepository.applySnapshot], which is the atomic swap.
 */
@Singleton
class BuildRoutingSnapshotUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val routeRepository: RouteRepository,
    private val chaosRepository: ChaosRepository,
    private val settingsRepository: ServerSettingsRepository,
) {

    /** A new snapshot on every change to collections, routes, chaos profiles or settings. */
    operator fun invoke(): Flow<RoutingSnapshot> = combine(
        collectionRepository.observeCollections(),
        routeRepository.observeAllRoutes(),
        chaosRepository.observeProfiles(),
        settingsRepository.observeSettings(),
    ) { collections, routes, profiles, settings ->
        build(collections, routes, profiles, settings)
    }.distinctUntilChanged()

    /** The pure part, so a test can build a snapshot without four fake repositories. */
    fun build(
        collections: List<MockCollection>,
        routes: List<MockRoute>,
        profiles: List<ChaosProfile>,
        settings: ServerSettings,
    ): RoutingSnapshot = RoutingSnapshot.build(
        collections = collections,
        routes = routes,
        globalChaos = settings.globalChaosProfileId?.let { id -> profiles.firstOrNull { it.id == id } },
        unmatchedResponse = settings.unmatchedResponse,
        pause = settings.pause,
    )

    /** Just the invalid patterns, for the banner that tells the user a route will never fire. */
    fun observeInvalidPatterns(): Flow<List<RoutingSnapshot.InvalidPattern>> =
        invoke().map { it.invalidPatterns }.distinctUntilChanged()
}
