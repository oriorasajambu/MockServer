package com.minion.mockserver.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The three configuration tables (SPEC §11.1).
 *
 * Traffic is deliberately absent: the log is an in-memory ring buffer, because sustained writes
 * on the request path would put disk I/O between a client and its response, and the data has a
 * lifespan of one debugging session.
 *
 * Nested values — matchers, responses, CORS, chaos specs — are stored as JSON in `String` columns
 * rather than normalised into child tables. They are always read and written as part of a whole
 * route, never queried field-wise, so normalising would buy joins and migration pain with no
 * query benefit.
 *
 * The serialisation happens in `data/mapper/`, not in a Room `TypeConverter`. A converter would
 * put `kotlinx.serialization` inside Room's generated code, where a malformed row surfaces as an
 * opaque Room exception naming neither the row nor the field. In the mapper it can be reported.
 */
@Entity(tableName = CollectionEntity.TABLE)
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val basePath: String,
    val isEnabled: Boolean,

    /** `order` is a reserved word in SQL, so the column carries a different name. */
    val sortOrder: Int,
    val corsJson: String,
    val createdAt: Long,
) {
    companion object {
        const val TABLE = "collections"
    }
}

@Entity(
    tableName = RouteEntity.TABLE,
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            // Deleting a collection deletes its routes. A route whose collection is gone has no
            // base path, so it could never be matched — an orphan row would be invisible data.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("collectionId"),
        Index("isEnabled"),
    ],
)
data class RouteEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val name: String,
    val method: String,
    val pathPattern: String,
    val matchersJson: String,
    val responseJson: String,
    val chaosOverrideJson: String?,
    val isEnabled: Boolean,
    val createdAt: Long,
    val needsBody: Boolean,

    /** The other statuses the source defined, as JSON. `[]` for a hand-written route. */
    @ColumnInfo(defaultValue = EMPTY_JSON_ARRAY)
    val alternatesJson: String,
) {
    companion object {
        const val TABLE = "routes"

        /** Matches the migration's `DEFAULT`, so a v1 row reads back as "no alternates". */
        const val EMPTY_JSON_ARRAY = "[]"
    }
}

@Entity(tableName = ChaosProfileEntity.TABLE)
data class ChaosProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val delayJson: String,
    val errorInjectionJson: String,
    val connectionResetJson: String,
    val malformedBodyJson: String,
) {
    companion object {
        const val TABLE = "chaos_profiles"
    }
}
