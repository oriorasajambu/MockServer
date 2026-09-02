package com.rio.mockserver.core.server

import com.rio.mockserver.core.common.error.toDomainError
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.BoundAddress
import com.rio.mockserver.core.domain.model.RoutingSnapshot
import com.rio.mockserver.core.domain.model.ServerConfig
import com.rio.mockserver.core.domain.model.ServerStatus
import com.rio.mockserver.core.domain.repository.MockServerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Satisfies [MockServerRepository] by delegating to the engine.
 *
 * Thin on purpose. Its whole job is to be the thing ViewModels depend on so that nothing in the
 * presentation layer ever holds a [MockServerEngine] — and so the engine's `Result<T>`, which is
 * how a bind failure travels out of a `runCatching`, becomes the `AppResult<T>` the rest of the
 * app speaks.
 */
@Singleton
class MockServerRepositoryImpl @Inject constructor(
    private val engine: MockServerEngine,
) : MockServerRepository {

    override val status: StateFlow<ServerStatus> get() = engine.status

    override val requestCount: StateFlow<Long> get() = engine.requestCount

    override val unmatchedCount: StateFlow<Long> get() = engine.unmatchedCount

    override suspend fun start(config: ServerConfig): AppResult<BoundAddress> =
        engine.start(config).toAppResult()

    override suspend fun stop(): AppResult<Unit> = engine.stop().toAppResult()

    override suspend fun applySnapshot(snapshot: RoutingSnapshot) {
        engine.applySnapshot(snapshot)
    }

    /**
     * Converts the engine's `Result` into the app's `AppResult`.
     *
     * `MockServerException` implements `DomainErrorAware`, so `toDomainError()` picks up
     * `PortUnavailable` and `BindFailed` without this needing a `when` over exception types.
     */
    private fun <T> Result<T>.toAppResult(): AppResult<T> = fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(it.toDomainError()) },
    )
}
