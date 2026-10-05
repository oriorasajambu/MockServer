package com.minion.mockserver.core.domain.usecase.collection

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.fixture.Fixtures
import com.minion.mockserver.core.domain.fixture.FakeIdGenerator
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.repository.CollectionRepository
import com.minion.mockserver.core.domain.repository.RouteRepository
import com.minion.mockserver.core.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Duplicating a collection has to produce something independent of the original, which is the one
 * property a copy can silently get wrong: shared ids look correct until the first delete.
 */
class DuplicateCollectionUseCaseTest {

    private val collections = FakeCollectionRepository()
    private val routes = FakeRouteRepository()
    private val duplicate = DuplicateCollectionUseCase(
        collectionRepository = collections,
        routeRepository = routes,
        idGenerator = FakeIdGenerator(prefix = "new"),
        timeProvider = object : TimeProvider {
            override fun nowMillis(): Long = COPIED_AT
            override fun monotonicNanos(): Long = 0L
        },
    )

    @Test
    fun `the copy and its routes get fresh ids`() = runTest {
        collections.items.value = listOf(original)
        routes.items.value = listOf(routeA, routeB)

        val copy = (duplicate("c1") as AppResult.Success).data

        assertNotEquals("c1", copy.id)
        val copied = routes.items.value.filter { it.collectionId == copy.id }
        assertEquals(2, copied.size)
        assertTrue(copied.none { it.id == "r1" || it.id == "r2" })
    }

    @Test
    fun `the copy is disabled so it does not compete with the original`() = runTest {
        collections.items.value = listOf(original.copy(isEnabled = true))
        routes.items.value = listOf(routeA)

        val copy = (duplicate("c1") as AppResult.Success).data

        assertFalse(copy.isEnabled)
    }

    @Test
    fun `the base path is carried over unchanged`() = runTest {
        collections.items.value = listOf(original)
        routes.items.value = emptyList()

        val copy = (duplicate("c1") as AppResult.Success).data

        assertEquals(original.basePath, copy.basePath)
    }

    @Test
    fun `the first copy is named copy and the second is numbered`() = runTest {
        collections.items.value = listOf(original)
        routes.items.value = emptyList()

        val first = (duplicate("c1") as AppResult.Success).data
        assertEquals("Api copy", first.name)

        val second = (duplicate("c1") as AppResult.Success).data
        assertEquals("Api copy 2", second.name)
    }

    @Test
    fun `a missing collection fails without writing anything`() = runTest {
        collections.items.value = emptyList()

        val result = duplicate("nope")

        assertTrue(result is AppResult.Failure)
        assertEquals(0, collections.items.value.size)
    }

    @Test
    fun `a failure saving the routes leaves no empty duplicate behind`() = runTest {
        collections.items.value = listOf(original)
        routes.items.value = listOf(routeA)
        routes.failSaveAll = true

        val result = duplicate("c1")

        assertTrue(result is AppResult.Failure)
        assertEquals(listOf("c1"), collections.items.value.map { it.id })
    }

    private val original = Fixtures.collection(id = "c1", basePath = "/api/v1").copy(name = "Api")
    private val routeA = Fixtures.route(id = "r1", collectionId = "c1", pathPattern = "/users")
    private val routeB = Fixtures.route(id = "r2", collectionId = "c1", pathPattern = "/orders")

    private companion object {
        const val COPIED_AT = 1_700_000_000_000L
    }
}

private class FakeCollectionRepository : CollectionRepository {
    val items = MutableStateFlow<List<MockCollection>>(emptyList())

    override fun observeCollections(): Flow<List<MockCollection>> = items
    override fun observeCollection(id: String): Flow<MockCollection?> =
        items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: String): AppResult<MockCollection> =
        items.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(DomainError.NotFound)

    override suspend fun save(collection: MockCollection): AppResult<Unit> {
        items.value = items.value.filterNot { it.id == collection.id } + collection
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun setEnabled(id: String, isEnabled: Boolean): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun reorder(idsInOrder: List<String>): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeRouteRepository : RouteRepository {
    val items = MutableStateFlow<List<MockRoute>>(emptyList())
    var failSaveAll = false

    override fun observeAllRoutes(): Flow<List<MockRoute>> = items
    override fun observeRoutes(collectionId: String): Flow<List<MockRoute>> =
        items.map { list -> list.filter { it.collectionId == collectionId } }

    override fun observeRoute(id: String): Flow<MockRoute?> =
        items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: String): AppResult<MockRoute> =
        items.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(DomainError.NotFound)

    override suspend fun save(route: MockRoute): AppResult<Unit> {
        items.value = items.value.filterNot { it.id == route.id } + route
        return AppResult.Success(Unit)
    }

    override suspend fun saveAll(routes: List<MockRoute>): AppResult<Unit> {
        if (failSaveAll) return AppResult.Failure(DomainError.Persistence)
        items.value = items.value + routes
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun setEnabled(id: String, isEnabled: Boolean): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun reorder(idsInOrder: List<String>): AppResult<Unit> {
        items.value = items.value.map { route ->
            val index = idsInOrder.indexOf(route.id)
            if (index >= 0) route.copy(order = index) else route
        }
        return AppResult.Success(Unit)
    }
}
