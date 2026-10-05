package com.minion.mockserver.core.domain.usecase.route

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.fixture.Fixtures
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reordering is arithmetic on a list, which is exactly the kind of thing that looks right and is
 * off by one — so the edges are pinned here rather than discovered by dragging a row on a phone.
 */
class ReorderRoutesUseCaseTest {

    private val repository = RecordingRouteRepository()
    private val reorder = ReorderRoutesUseCase(repository)

    @Test
    fun `moving up swaps with the row above`() {
        val moved = reorder.moved(listOf("a", "b", "c"), routeId = "b", up = true)
        assertEquals(listOf("b", "a", "c"), moved)
    }

    @Test
    fun `moving down swaps with the row below`() {
        val moved = reorder.moved(listOf("a", "b", "c"), routeId = "b", up = false)
        assertEquals(listOf("a", "c", "b"), moved)
    }

    @Test
    fun `the first row cannot move up and does not wrap`() {
        val order = listOf("a", "b", "c")
        assertEquals(order, reorder.moved(order, routeId = "a", up = true))
    }

    @Test
    fun `the last row cannot move down and does not wrap`() {
        val order = listOf("a", "b", "c")
        assertEquals(order, reorder.moved(order, routeId = "c", up = false))
    }

    @Test
    fun `an unknown id leaves the order untouched`() {
        val order = listOf("a", "b", "c")
        assertEquals(order, reorder.moved(order, routeId = "zzz", up = true))
    }

    @Test
    fun `persisting assigns indices from zero`() = runTest {
        repository.items.value = listOf(
            Fixtures.route(id = "a", collectionId = "c1").copy(order = 7),
            Fixtures.route(id = "b", collectionId = "c1").copy(order = 7),
            Fixtures.route(id = "c", collectionId = "c1").copy(order = 99),
        )

        reorder(listOf("c", "a", "b"))

        assertEquals(listOf(0, 1, 2), listOf("c", "a", "b").map { id ->
            repository.items.value.first { it.id == id }.order
        })
    }

    @Test
    fun `an empty order writes nothing`() = runTest {
        val result = reorder(emptyList())

        assertTrue(result is AppResult.Success)
        assertEquals(0, repository.reorderCalls)
    }
}

private class RecordingRouteRepository : RouteRepository {
    val items = MutableStateFlow<List<MockRoute>>(emptyList())
    var reorderCalls = 0

    override fun observeAllRoutes(): Flow<List<MockRoute>> = items
    override fun observeRoutes(collectionId: String): Flow<List<MockRoute>> =
        items.map { list -> list.filter { it.collectionId == collectionId } }

    override fun observeRoute(id: String): Flow<MockRoute?> =
        items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: String): AppResult<MockRoute> =
        items.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(DomainError.NotFound)

    override suspend fun save(route: MockRoute): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun saveAll(routes: List<MockRoute>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setEnabled(id: String, isEnabled: Boolean): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun reorder(idsInOrder: List<String>): AppResult<Unit> {
        reorderCalls++
        items.value = items.value.map { route ->
            val index = idsInOrder.indexOf(route.id)
            if (index >= 0) route.copy(order = index) else route
        }
        return AppResult.Success(Unit)
    }
}
