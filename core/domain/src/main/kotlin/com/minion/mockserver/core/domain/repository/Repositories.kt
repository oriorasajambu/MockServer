package com.minion.mockserver.core.domain.repository

import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.BoundAddress
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.NetworkAddress
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.ServerConfig
import com.minion.mockserver.core.domain.model.ServerSettings
import com.minion.mockserver.core.domain.model.ServerStatus
import com.minion.mockserver.core.domain.model.TrafficEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Collections of routes, backed by Room (SPEC §11.1).
 *
 * Declared here and implemented in `:core:data`, so the dependency points inward: the domain
 * never learns where its data comes from.
 */
interface CollectionRepository {

    /** Every collection, ordered by [MockCollection.order] then name. Re-emits on every change. */
    fun observeCollections(): Flow<List<MockCollection>>

    /** One collection, or `null` once it is deleted. */
    fun observeCollection(id: String): Flow<MockCollection?>

    suspend fun get(id: String): AppResult<MockCollection>

    /** Inserts or replaces. The caller supplies the id, so this is idempotent on re-import. */
    suspend fun save(collection: MockCollection): AppResult<Unit>

    /** Deletes the collection and, by foreign-key cascade, every route in it. */
    suspend fun delete(id: String): AppResult<Unit>

    suspend fun setEnabled(id: String, isEnabled: Boolean): AppResult<Unit>

    /** Persists a reordering as one transaction, so no intermediate order is ever observable. */
    suspend fun reorder(idsInOrder: List<String>): AppResult<Unit>
}

/** Routes, backed by Room (SPEC §11.1). */
interface RouteRepository {

    /** Every route, across every collection. What the snapshot builder subscribes to. */
    fun observeAllRoutes(): Flow<List<MockRoute>>

    /** The routes in one collection, in creation order. */
    fun observeRoutes(collectionId: String): Flow<List<MockRoute>>

    fun observeRoute(id: String): Flow<MockRoute?>

    suspend fun get(id: String): AppResult<MockRoute>

    suspend fun save(route: MockRoute): AppResult<Unit>

    /** Saves many as one transaction — what an import commit uses (SPEC §9.1). */
    suspend fun saveAll(routes: List<MockRoute>): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>

    suspend fun setEnabled(id: String, isEnabled: Boolean): AppResult<Unit>
}

/** Named chaos profiles (SPEC §6). */
interface ChaosRepository {

    fun observeProfiles(): Flow<List<ChaosProfile>>

    suspend fun get(id: String): AppResult<ChaosProfile>

    suspend fun save(profile: ChaosProfile): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>
}

/** Server settings, backed by DataStore (SPEC §11.2). */
interface ServerSettingsRepository {

    fun observeSettings(): Flow<ServerSettings>

    suspend fun get(): ServerSettings

    /**
     * Applies [transform] to the stored settings atomically.
     *
     * A read-modify-write through two calls would lose an update whenever two screens change
     * different fields at once — which is exactly what happens when the notification's Pause
     * action fires while the settings screen is open.
     */
    suspend fun update(transform: (ServerSettings) -> ServerSettings): AppResult<Unit>
}

/**
 * The live traffic log: an in-memory ring buffer, never Room (SPEC §7.1).
 *
 * [offer] is deliberately not `suspend`. The request path must never block on logging: it hands
 * the entry to a buffered channel and returns, and if the drain cannot keep up the *log* loses
 * entries rather than the *server* losing throughput. [droppedCount] makes that loss visible
 * instead of silent.
 */
interface TrafficRepository {

    /** Newest first. Capped at [ServerSettings.logCapacity]. */
    val entries: StateFlow<List<TrafficEntry>>

    /** Entries dropped because the hand-off channel overflowed. Reset by [clear]. */
    val droppedCount: StateFlow<Int>

    /** Non-blocking. Called from the request path. */
    fun offer(entry: TrafficEntry)

    /** Empties the buffer and resets [droppedCount]. */
    suspend fun clear()

    /** Resizes the ring buffer, trimming the oldest entries if it shrinks. */
    suspend fun setCapacity(capacity: Int)
}

/**
 * The embedded server itself (SPEC §2.2).
 *
 * The engine is a `@Singleton` in `:core:server`; this interface is the only thing the
 * presentation layer ever sees of it. No service binding, no AIDL, no cross-process IPC — the
 * foreground service exists to keep the process alive, not to own the engine.
 */
interface MockServerRepository {

    /** The engine's lifecycle. Hot, and survives every ViewModel that observes it. */
    val status: StateFlow<ServerStatus>

    /** Requests served since the current run started. Reset on each start. */
    val requestCount: StateFlow<Long>

    /** Requests that matched no route, since the current run started. */
    val unmatchedCount: StateFlow<Long>

    /**
     * Binds the socket, applying port auto-fallback (SPEC §8.4).
     *
     * The returned [BoundAddress] may name a different port from [config] — that is a success,
     * not a failure, and the UI says which port it landed on.
     */
    suspend fun start(config: ServerConfig): AppResult<BoundAddress>

    suspend fun stop(): AppResult<Unit>

    /**
     * Swaps in a new rule set without restarting (SPEC §5.5).
     *
     * Safe to call while requests are in flight: each one already captured the previous snapshot
     * at entry and finishes against it.
     */
    suspend fun applySnapshot(snapshot: RoutingSnapshot)
}

/**
 * The device's own reachable addresses (SPEC §8.5).
 *
 * A `Flow` rather than a one-shot read because the IP changes the instant Wi-Fi flips or a
 * hotspot comes up, and a stale address on the server screen is worse than no address — someone
 * will type it into a laptop and spend ten minutes wondering why nothing connects.
 */
interface NetworkAddressRepository {

    /** Ranked best-first, re-emitting on every connectivity change. Empty means localhost only. */
    fun observeAddresses(): Flow<List<NetworkAddress>>
}
