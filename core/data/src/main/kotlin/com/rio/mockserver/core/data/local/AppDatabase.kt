package com.rio.mockserver.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rio.mockserver.core.data.local.dao.ChaosProfileDao
import com.rio.mockserver.core.data.local.dao.CollectionDao
import com.rio.mockserver.core.data.local.dao.RouteDao
import com.rio.mockserver.core.data.local.entity.ChaosProfileEntity
import com.rio.mockserver.core.data.local.entity.CollectionEntity
import com.rio.mockserver.core.data.local.entity.RouteEntity

/**
 * `mock_server.db` — configuration only, never traffic (SPEC §11.1).
 *
 * No `@TypeConverters`: every column is a primitive or a JSON `String` the mappers produce, so
 * Room has nothing it cannot store on its own.
 *
 * `exportSchema = true`, and `fallbackToDestructiveMigration` is never used. Migrations are
 * written explicitly from v1 onward, because losing a hand-built mock collection is a real cost
 * to a real person and "the schema changed" is not a reason they would accept.
 */
@Database(
    entities = [
        CollectionEntity::class,
        RouteEntity::class,
        ChaosProfileEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun collectionDao(): CollectionDao

    abstract fun routeDao(): RouteDao

    abstract fun chaosProfileDao(): ChaosProfileDao

    companion object {
        const val NAME = "mock_server.db"
        const val VERSION = 1
    }
}
