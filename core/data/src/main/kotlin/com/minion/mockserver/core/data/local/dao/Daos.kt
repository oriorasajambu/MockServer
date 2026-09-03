package com.minion.mockserver.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.minion.mockserver.core.data.local.entity.ChaosProfileEntity
import com.minion.mockserver.core.data.local.entity.CollectionEntity
import com.minion.mockserver.core.data.local.entity.RouteEntity
import kotlinx.coroutines.flow.Flow

/** Collections, ordered the way the Routes tab shows them. */
@Dao
interface CollectionDao {

    @Query("SELECT * FROM ${CollectionEntity.TABLE} ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM ${CollectionEntity.TABLE} WHERE id = :id")
    fun observeById(id: String): Flow<CollectionEntity?>

    @Query("SELECT * FROM ${CollectionEntity.TABLE} WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Query("DELETE FROM ${CollectionEntity.TABLE} WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE ${CollectionEntity.TABLE} SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)

    @Query("UPDATE ${CollectionEntity.TABLE} SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: String, order: Int)

    /**
     * Applies a whole reordering in one transaction.
     *
     * Per-row updates would each emit from [observeAll], so the list would visibly shuffle through
     * intermediate orders — and a snapshot rebuild triggered mid-way would compile a tie-break
     * order that never existed.
     */
    @Transaction
    suspend fun reorder(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, id -> setOrder(id, index) }
    }
}

/** Routes. */
@Dao
interface RouteDao {

    @Query("SELECT * FROM ${RouteEntity.TABLE} ORDER BY `${RouteEntity.ORDER_COLUMN}` ASC, createdAt ASC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query(
        "SELECT * FROM ${RouteEntity.TABLE} WHERE collectionId = :collectionId " +
            "ORDER BY `${RouteEntity.ORDER_COLUMN}` ASC, createdAt ASC",
    )
    fun observeByCollection(collectionId: String): Flow<List<RouteEntity>>

    @Query("SELECT * FROM ${RouteEntity.TABLE} WHERE id = :id")
    fun observeById(id: String): Flow<RouteEntity?>

    @Query("SELECT * FROM ${RouteEntity.TABLE} WHERE id = :id")
    suspend fun getById(id: String): RouteEntity?

    @Upsert
    suspend fun upsert(route: RouteEntity)

    /** One statement, one transaction: an import is all-or-nothing, because a half-imported
     *  collection looks finished. */
    @Upsert
    suspend fun upsertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM ${RouteEntity.TABLE} WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE ${RouteEntity.TABLE} SET `${RouteEntity.ORDER_COLUMN}` = :order WHERE id = :id")
    suspend fun setOrder(id: String, order: Int)

    /**
     * Writes a whole collection's order in one transaction.
     *
     * One statement per row, but one transaction around them: a half-applied reorder is a list
     * with two routes claiming the same index, and the observing flow would emit it.
     */
    @Transaction
    suspend fun reorder(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, id -> setOrder(id, index) }
    }

    @Query("UPDATE ${RouteEntity.TABLE} SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)
}

/** Chaos profiles. */
@Dao
interface ChaosProfileDao {

    @Query("SELECT * FROM ${ChaosProfileEntity.TABLE} ORDER BY name ASC")
    fun observeAll(): Flow<List<ChaosProfileEntity>>

    @Query("SELECT * FROM ${ChaosProfileEntity.TABLE} WHERE id = :id")
    suspend fun getById(id: String): ChaosProfileEntity?

    @Upsert
    suspend fun upsert(profile: ChaosProfileEntity)

    @Query("DELETE FROM ${ChaosProfileEntity.TABLE} WHERE id = :id")
    suspend fun deleteById(id: String)
}
