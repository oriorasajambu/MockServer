package com.minion.mockserver.core.domain.fixture

import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockRequest
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.repository.TrafficRepository
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Builders for the models the matching tests need.
 *
 * Every field defaulted, so a test names only what it is actually about. A test that has to spell
 * out a collection, a response and five unrelated fields to say "this route is a GET on /users"
 * stops being readable at about the fourth case, which is where table-driven tests start.
 */
object Fixtures {

    const val COLLECTION_ID = "collection-1"

    fun collection(
        id: String = COLLECTION_ID,
        basePath: String = "/",
        order: Int = 0,
        isEnabled: Boolean = true,
        cors: com.minion.mockserver.core.domain.model.CorsConfig =
            com.minion.mockserver.core.domain.model.CorsConfig.DISABLED,
    ) = MockCollection(
        id = id,
        name = "Collection $id",
        basePath = basePath,
        isEnabled = isEnabled,
        order = order,
        cors = cors,
        createdAt = 1_000L,
    )

    fun route(
        id: String = "route-1",
        collectionId: String = COLLECTION_ID,
        method: HttpMethodFilter = HttpMethodFilter.ANY,
        pathPattern: String = "/",
        matchers: List<RequestMatcher> = emptyList(),
        response: MockResponse = MockResponse(statusCode = 200, body = ""),
        chaosOverride: ChaosProfile? = null,
        isEnabled: Boolean = true,
        createdAt: Long = 1_000L,
    ) = MockRoute(
        id = id,
        collectionId = collectionId,
        name = "$method $pathPattern",
        method = method,
        pathPattern = pathPattern,
        matchers = matchers,
        response = response,
        chaosOverride = chaosOverride,
        isEnabled = isEnabled,
        createdAt = createdAt,
    )

    fun request(
        method: String = "GET",
        path: String = "/",
        query: Map<String, List<String>> = emptyMap(),
        headers: Map<String, List<String>> = emptyMap(),
        body: String? = null,
        bodyState: MockRequest.BodyState =
            if (body == null) MockRequest.BodyState.ABSENT else MockRequest.BodyState.PRESENT,
    ) = MockRequest(
        method = method,
        path = path,
        query = query,
        headers = headers,
        body = body,
        bodyState = bodyState,
        clientIp = "192.168.1.10",
        receivedAt = 5_000L,
    )

    fun snapshot(
        routes: List<MockRoute>,
        collections: List<MockCollection> = listOf(collection()),
        globalChaos: ChaosProfile? = null,
        unmatchedResponse: MockResponse = MockResponse.DEFAULT_UNMATCHED,
        pause: com.minion.mockserver.core.domain.model.PauseSpec =
            com.minion.mockserver.core.domain.model.PauseSpec.OFF,
    ): RoutingSnapshot = RoutingSnapshot.build(
        collections = collections,
        routes = routes,
        globalChaos = globalChaos,
        unmatchedResponse = unmatchedResponse,
        pause = pause,
    )
}

/** A clock that only moves when a test moves it. */
class FakeTimeProvider(
    var nowMillis: Long = 10_000L,
    var monotonicNanos: Long = 0L,
) : TimeProvider {
    override fun nowMillis(): Long = nowMillis
    override fun monotonicNanos(): Long = monotonicNanos
}

/** Sequential ids, so an assertion can name the one it means. */
class FakeIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix-${++counter}"
}

/** Records what was logged, with none of the ring-buffer machinery. */
class RecordingTrafficRepository : TrafficRepository {
    val logged = mutableListOf<TrafficEntry>()

    private val _entries = MutableStateFlow<List<TrafficEntry>>(emptyList())
    override val entries: StateFlow<List<TrafficEntry>> = _entries

    private val _droppedCount = MutableStateFlow(0)
    override val droppedCount: StateFlow<Int> = _droppedCount

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
