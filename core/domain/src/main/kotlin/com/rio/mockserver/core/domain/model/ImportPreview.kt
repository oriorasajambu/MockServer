package com.rio.mockserver.core.domain.model

import kotlinx.serialization.Serializable

/**
 * What an import file turned out to contain, before anything is written (SPEC §9.1).
 *
 * Import is a two-step operation on purpose: parse into this, show it, let the user uncheck what
 * they do not want and resolve conflicts, and only then commit. An importer that writes straight
 * to the database turns "let me see what's in this file" into a destructive action.
 */
data class ImportPreview(
    val format: ImportFormat,

    /** Name suggested for a new collection, from the document's title. */
    val suggestedCollectionName: String,

    /** Base path suggested for a new collection, from the document's server URL. */
    val suggestedBasePath: String,

    /** Every route the file describes, in document order. */
    val routes: List<ImportedRoute>,

    /**
     * Things the file said that this app cannot represent.
     *
     * Shown before committing rather than swallowed, because a silent lossy import is how someone
     * spends an hour wondering why their `oneOf` schema produced one route.
     */
    val warnings: List<String> = emptyList(),
) {

    /** How many routes came in without a response body, for the "N routes need bodies" banner. */
    val needsBodyCount: Int get() = routes.count { it.route.needsBody }
}

/** One route from an import file, with where it came from. */
data class ImportedRoute(
    val route: MockRoute,

    /** `GET /users/{id}` or the Postman request name — what the preview row shows. */
    val label: String,

    /**
     * Other statuses the source defined for the same operation (SPEC §17.3).
     *
     * Not imported as separate routes: identically-shaped routes would shadow each other and the
     * list would be full of rules that can never win. They are offered as one-tap alternates in
     * the editor instead.
     */
    val alternateResponses: List<AlternateResponse> = emptyList(),

    /** Checked by default in the preview; the user can uncheck any of them. */
    val isSelected: Boolean = true,
)

/**
 * A response the source defined that did not become the route's own.
 *
 * `@Serializable` because these outlive the import: they are stored on the route and offered in
 * the editor long after the file that carried them is gone (SPEC §9.1, §17.3).
 */
@Serializable
data class AlternateResponse(
    val statusCode: Int,
    val description: String,
    val response: MockResponse,
)

/** The formats this app reads (SPEC §9.1). */
enum class ImportFormat {
    /** OpenAPI 3.0 / 3.1, JSON or YAML. */
    OPENAPI,

    /** Postman collection v2.1. */
    POSTMAN,

    /** HAR 1.2. */
    HAR,

    /** This app's own export format, which round-trips with full fidelity. */
    NATIVE,
}

/**
 * A route in the import that collides with one already saved (SPEC §9.1).
 *
 * Collision is on method + full path, which is what makes two rules fight for the same traffic.
 * A different name or a different body is not a conflict — it is an edit.
 */
data class ImportConflict(
    val incoming: ImportedRoute,
    val existing: MockRoute,
    val resolution: Resolution = Resolution.SKIP,
) {

    /** What to do about one collision. Chosen per route, not once for the whole file. */
    enum class Resolution {
        /** Leave the saved route alone. The safe default. */
        SKIP,

        /** Overwrite the saved route, keeping its id so nothing else has to be repointed. */
        REPLACE,

        /** Import anyway. Both will exist, and the tie-break chain decides which wins. */
        KEEP_BOTH,
    }
}

/** A parsed native export, ready to be merged into the database (SPEC §9.2). */
data class NativeExport(
    val schemaVersion: Int,
    val collections: List<MockCollection>,
    val routes: List<MockRoute>,
    val chaosProfiles: List<ChaosProfile> = emptyList(),
)
