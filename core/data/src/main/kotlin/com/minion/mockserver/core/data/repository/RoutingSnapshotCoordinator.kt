package com.minion.mockserver.core.data.repository

import com.minion.mockserver.core.common.dispatcher.ApplicationScope
import com.minion.mockserver.core.common.dispatcher.DefaultDispatcher
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.repository.MockServerRepository
import com.minion.mockserver.core.domain.repository.ServerSettingsRepository
import com.minion.mockserver.core.domain.repository.TrafficRepository
import com.minion.mockserver.core.domain.usecase.chaos.ChaosRandom
import com.minion.mockserver.core.domain.usecase.request.BuildRoutingSnapshotUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the running engine in step with what is saved (SPEC §5.5).
 *
 * Started once from `Application.onCreate` and never stopped. Three things flow one way here, and
 * all three exist so that editing something in the UI takes effect on the *next request* without
 * anybody restarting the socket:
 *
 * - Collections, routes, chaos profiles and settings compile into a [RoutingSnapshot] on
 *   `Dispatchers.Default` and are swapped atomically into the engine.
 * - The persisted chaos seed is pushed into the live RNG, so "reproduce with seed 42" works
 *   against a server that is already running.
 * - The log capacity is applied to the ring buffer.
 *
 * The rebuild happens off the main thread because it parses every path pattern in the database.
 * At a hundred routes that is microseconds; at a thousand, on a mid-range phone, it is a frame.
 */
@Singleton
class RoutingSnapshotCoordinator @Inject constructor(
    private val buildRoutingSnapshot: BuildRoutingSnapshotUseCase,
    private val serverRepository: MockServerRepository,
    private val settingsRepository: ServerSettingsRepository,
    private val trafficRepository: TrafficRepository,
    private val chaosRandom: ChaosRandom,
    @ApplicationScope private val scope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    /** Idempotent: a second call is ignored, so a restarted activity cannot double-subscribe. */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            buildRoutingSnapshot()
                .flowOn(defaultDispatcher)
                .collect { snapshot -> serverRepository.applySnapshot(snapshot) }
        }

        scope.launch {
            settingsRepository.observeSettings()
                .map { it.chaosSeed }
                .distinctUntilChanged()
                .collect { seed -> chaosRandom.reseed(seed) }
        }

        scope.launch {
            settingsRepository.observeSettings()
                .map { it.logCapacity }
                .distinctUntilChanged()
                .collect { capacity -> trafficRepository.setCapacity(capacity) }
        }
    }

    @Volatile
    private var started = false
}
