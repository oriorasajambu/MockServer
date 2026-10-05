package com.minion.mockserver.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.minion.mockserver.core.data.local.dao.ChaosProfileDao
import com.minion.mockserver.core.data.local.dao.CollectionDao
import com.minion.mockserver.core.data.local.dao.RouteDao
import com.minion.mockserver.core.data.local.entity.ChaosProfileEntity
import com.minion.mockserver.core.data.local.entity.CollectionEntity
import com.minion.mockserver.core.data.local.entity.RouteEntity

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
        const val VERSION = 3

        /**
         * Adds `routes.alternatesJson` (SPEC §9.1).
         *
         * Written out rather than allowed to fall back to a destructive migration: a v1 database
         * holds hand-built collections, and the whole column is optional data an existing row is
         * entitled to be empty of. `DEFAULT '[]'` is what makes the existing rows valid.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `${RouteEntity.TABLE}` ADD COLUMN `alternatesJson` " +
                        "TEXT NOT NULL DEFAULT '${RouteEntity.EMPTY_JSON_ARRAY}'",
                )
            }
        }

        /**
         * Adds `routes.sortOrder`, so a collection's list can be arranged by hand.
         *
         * Existing rows are seeded from their creation order rather than all defaulting to 0.
         * Every route sharing one order value would leave the list's arrangement down to whatever
         * the secondary sort did, and the first drag would appear to shuffle unrelated rows.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `${RouteEntity.TABLE}` ADD COLUMN " +
                        "`${RouteEntity.ORDER_COLUMN}` INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    """
                    UPDATE `${RouteEntity.TABLE}` SET `${RouteEntity.ORDER_COLUMN}` = (
                        SELECT COUNT(*) FROM `${RouteEntity.TABLE}` AS earlier
                        WHERE earlier.collectionId = `${RouteEntity.TABLE}`.collectionId
                          AND (earlier.createdAt < `${RouteEntity.TABLE}`.createdAt
                               OR (earlier.createdAt = `${RouteEntity.TABLE}`.createdAt
                                   AND earlier.id < `${RouteEntity.TABLE}`.id))
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
