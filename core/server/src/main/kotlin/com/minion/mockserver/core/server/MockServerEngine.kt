package com.minion.mockserver.core.server

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.domain.exception.MockServerException
import com.minion.mockserver.core.domain.model.BoundAddress
import com.minion.mockserver.core.domain.model.MockResolution
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.ServerConfig
import com.minion.mockserver.core.domain.model.ServerStatus
import com.minion.mockserver.core.domain.usecase.request.HandleMockRequestUseCase
import com.minion.mockserver.core.domain.util.TimeProvider
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.net.BindException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The embedded Ktor CIO server (SPEC §2.2).
 *
 * A `@Singleton`, not something the service or a ViewModel owns. `MockServerService` exists only
 * to keep the process alive, own the notification and drive start/stop; the presentation layer
 * reaches this through `MockServerRepository` and never touches it directly. One process, no
 * binding, no AIDL — a `:server` process would force IPC for every state read and duplicate the
 * DI graph for no benefit.
 *
 * The consequence to respect: if the app's process dies, the engine dies with it. That is why the
 * foreground service exists.
 *
 * ### Hot-apply
 *
 * [snapshot] is an `AtomicReference` holding an immutable [RoutingSnapshot]. Saving a route builds
 * a new one off the main thread and swaps the reference; a request reads it *once* at entry and
 * resolves entirely against that value. So an in-flight request finishes under the rules it
 * started with, the next one sees the new rules, and neither a lock nor a restart is involved
 * (SPEC §5.5). Restarting would drop every keep-alive socket and break clients mid-test.
 */
@Singleton
class MockServerEngine @Inject constructor(
    private val handleMockRequest: HandleMockRequestUseCase,
    private val callAdapter: KtorCallAdapter,
    private val responseWriter: ResponseWriter,
    private val timeProvider: TimeProvider,
) {

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)

    /** The engine's lifecycle. Read-only: nothing outside this class may write it. */
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _requestCount = AtomicLong(0)
    private val _unmatchedCount = AtomicLong(0)

    private val requestCountFlow = MutableStateFlow(0L)

    /** Requests served in the current run. Reset on each start. */
    val requestCount: StateFlow<Long> = requestCountFlow.asStateFlow()

    private val unmatchedCountFlow = MutableStateFlow(0L)

    /** Requests in the current run that matched no route. */
    val unmatchedCount: StateFlow<Long> = unmatchedCountFlow.asStateFlow()

    /** The live rule set. Swapped atomically; never mutated in place. */
    private val snapshot = AtomicReference(RoutingSnapshot.EMPTY)

    /** The configuration of the current run, captured at start (SPEC §5.5). */
    private val activeConfig = AtomicReference(ServerConfig())

    private var server: EmbeddedServer<*, *>? = null

    /** Serialises start and stop so a double-tap cannot leave two engines bound. */
    private val lifecycleMutex = Mutex()

    /**
     * Binds the socket, walking the port fallback range (SPEC §8.4).
     *
     * `port + 1 … port + 10` on a [BindException], then an actionable failure. Landing on a
     * different port is a success, not a fallback failure — the caller reports which one.
     */
    suspend fun start(config: ServerConfig): Result<BoundAddress> = lifecycleMutex.withLock {
        if (server != null) {
            val running = _status.value
            if (running is ServerStatus.Running) return@withLock Result.success(running.address)
        }

        _status.value = ServerStatus.Starting
        configure(config)
        resetCounters()

        for (candidatePort in config.candidatePorts) {
            val attempt = tryBind(config, candidatePort)
            attempt.onSuccess { bound ->
                _status.value = ServerStatus.Running(
                    address = bound,
                    requestedPort = config.port,
                    startedAt = timeProvider.nowMillis(),
                )
                return@withLock Result.success(bound)
            }
            // A bind failure that is not "port in use" will not be fixed by trying port + 1, so
            // it fails immediately rather than burning ten more attempts on the same cause.
            val error = attempt.exceptionOrNull()
            if (error !is BindException) {
                _status.value = ServerStatus.Failed(DomainError.BindFailed)
                return@withLock Result.failure(MockServerException.BindFailed(error))
            }
        }

        val exhausted = MockServerException.PortUnavailable(config.port, config.portFallbackAttempts)
        _status.value = ServerStatus.Failed(exhausted.domainError)
        Result.failure(exhausted)
    }

    /** Stops the socket. Idempotent: stopping an already-stopped engine succeeds. */
    suspend fun stop(): Result<Unit> = lifecycleMutex.withLock {
        val current = server ?: run {
            _status.value = ServerStatus.Stopped
            return@withLock Result.success(Unit)
        }

        _status.value = ServerStatus.Stopping
        return@withLock runCatching {
            current.stop(GRACE_PERIOD_MILLIS, SHUTDOWN_TIMEOUT_MILLIS)
            server = null
            _status.value = ServerStatus.Stopped
        }.onFailure {
            server = null
            _status.value = ServerStatus.Stopped
        }
    }

    /**
     * Swaps in a new rule set (SPEC §5.5).
     *
     * Safe to call while requests are in flight, and safe to call while stopped — the next start
     * serves whatever was last applied.
     */
    fun applySnapshot(newSnapshot: RoutingSnapshot) {
        snapshot.set(newSnapshot)
    }

    /** The rule set currently being served. */
    fun currentSnapshot(): RoutingSnapshot = snapshot.get()

    /**
     * Sets the configuration the request path reads, without binding a socket.
     *
     * [start] calls this. It is also how `testApplication` mounts the pipeline with a chosen body
     * ceiling or API key, since those tests exercise the wiring rather than the bind.
     */
    fun configure(config: ServerConfig) {
        activeConfig.set(config)
    }

    /**
     * Installs the catch-all route.
     *
     * Public so `testApplication` can mount the same pipeline the real engine serves — the wiring
     * a unit test cannot see is exactly what those tests exist to check (SPEC §14).
     */
    fun install(application: Application) {
        application.routing {
            // One handler for everything. Ktor's own routing tree would impose its own matching
            // semantics on top of SPEC §4's, and the two would disagree the first time a
            // trailing slash or a wildcard was involved.
            route("{...}") {
                handle { serve(call) }
            }
        }
    }

    private suspend fun serve(call: ApplicationCall) {
        // Captured once, at entry: this request resolves entirely against this value even if a
        // save swaps the reference mid-flight.
        val active = snapshot.get()
        val config = activeConfig.get()

        responseWriter.respondCatching(call) {
            val request = callAdapter.toMockRequest(call, config.maxRequestBodyBytes)
            val resolution = handleMockRequest(request, active, config) { plan ->
                responseWriter.write(call, plan)
            }
            countRequest(resolution)
        }
    }

    private fun countRequest(resolution: MockResolution) {
        requestCountFlow.value = _requestCount.incrementAndGet()
        if (resolution is MockResolution.Unmatched) {
            unmatchedCountFlow.value = _unmatchedCount.incrementAndGet()
        }
    }

    private fun resetCounters() {
        _requestCount.set(0)
        _unmatchedCount.set(0)
        requestCountFlow.value = 0
        unmatchedCountFlow.value = 0
    }

    private fun tryBind(config: ServerConfig, port: Int): Result<BoundAddress> = runCatching {
        val embedded = embeddedServer(
            factory = CIO,
            port = port,
            host = config.bindAddress,
            module = { install(this) },
        )
        // `wait = false`, so this returns as soon as the socket is bound rather than blocking the
        // caller for the life of the server.
        embedded.start(wait = false)
        server = embedded
        BoundAddress(host = config.bindAddress, port = port)
    }.onFailure { server = null }

    private companion object {
        /** How long in-flight requests get to finish on stop. */
        const val GRACE_PERIOD_MILLIS = 500L

        /** Hard deadline after the grace period. */
        const val SHUTDOWN_TIMEOUT_MILLIS = 2_000L
    }
}
