package com.minion.mockserver.core.server

import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.CorsConfig
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.PauseSpec
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.repository.TrafficRepository
import com.minion.mockserver.core.domain.usecase.chaos.ChaosRandom
import com.minion.mockserver.core.domain.usecase.chaos.DecideChaosUseCase
import com.minion.mockserver.core.domain.usecase.request.BodySubsetMatcher
import com.minion.mockserver.core.domain.usecase.request.HandleMockRequestUseCase
import com.minion.mockserver.core.domain.usecase.request.MatchScorer
import com.minion.mockserver.core.domain.usecase.request.ResolvePlaceholdersUseCase
import com.minion.mockserver.core.domain.usecase.request.ResolveRouteUseCase
import com.minion.mockserver.core.domain.usecase.traffic.LogTrafficUseCase
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Assembles a real engine from real collaborators.
 *
 * No mocks, deliberately. These tests exist to check the wiring the unit tests cannot see, and a
 * mocked collaborator is precisely the wiring being checked.
 */
object EngineFixture {

    fun engine(traffic: TrafficRepository = RecordingTraffic()): MockServerEngine {
        val time = object : TimeProvider {
            override fun nowMillis(): Long = 1_000L
            override fun monotonicNanos(): Long = 0L
        }
        return MockServerEngine(
            handleMockRequest = HandleMockRequestUseCase(
                resolveRoute = ResolveRouteUseCase(MatchScorer(BodySubsetMatcher())),
                decideChaos = DecideChaosUseCase(ChaosRandom()),
                resolvePlaceholders = ResolvePlaceholdersUseCase(),
                logTraffic = LogTrafficUseCase(traffic),
                timeProvider = time,
                idGenerator = IdGenerator { "entry" },
            ),
            callAdapter = KtorCallAdapter(time),
            responseWriter = ResponseWriter(),
            timeProvider = time,
        )
    }

    fun collection(
        id: String = "c1",
        basePath: String = "/",
        cors: CorsConfig = CorsConfig.DISABLED,
    ) = MockCollection(id = id, name = id, basePath = basePath, cors = cors, createdAt = 1L)

    fun route(
        id: String = "r1",
        collectionId: String = "c1",
        method: HttpMethodFilter = HttpMethodFilter.ANY,
        pathPattern: String = "/",
        matchers: List<RequestMatcher> = emptyList(),
        response: MockResponse = MockResponse(statusCode = 200, body = "{}"),
        chaosOverride: ChaosProfile? = null,
    ) = MockRoute(
        id = id,
        collectionId = collectionId,
        name = id,
        method = method,
        pathPattern = pathPattern,
        matchers = matchers,
        response = response,
        chaosOverride = chaosOverride,
        createdAt = 1L,
    )

    fun snapshot(
        routes: List<MockRoute>,
        collections: List<MockCollection> = listOf(collection()),
        globalChaos: ChaosProfile? = null,
        pause: PauseSpec = PauseSpec.OFF,
    ): RoutingSnapshot = RoutingSnapshot.build(
        collections = collections,
        routes = routes,
        globalChaos = globalChaos,
        pause = pause,
    )
}

/** Keeps every entry the pipeline logged, so a test can assert on what the inspector would show. */
class RecordingTraffic : TrafficRepository {
    val logged = mutableListOf<TrafficEntry>()

    private val _entries = MutableStateFlow<List<TrafficEntry>>(emptyList())
    override val entries: StateFlow<List<TrafficEntry>> = _entries

    private val _dropped = MutableStateFlow(0)
    override val droppedCount: StateFlow<Int> = _dropped

    override fun offer(entry: TrafficEntry) {
        logged += entry
        _entries.value = listOf(entry) + _entries.value
    }

    override suspend fun clear() {
        logged.clear()
        _entries.value = emptyList()
    }

    override suspend fun setCapacity(capacity: Int) = Unit
}
