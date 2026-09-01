package com.rio.mockserver.core.domain.repository

import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.ImportFormat
import com.rio.mockserver.core.domain.model.ImportPreview
import com.rio.mockserver.core.domain.model.MockCollection
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.domain.model.NativeExport
import com.rio.mockserver.core.domain.model.TrafficEntry

/**
 * Parsing import files into domain models (SPEC §9.1).
 *
 * A port, not an implementation: OpenAPI, Postman and HAR are wire formats, and their DTOs and
 * traversals belong in `:core:importexport` behind this interface. The use cases above it deal in
 * [ImportPreview] and never learn what a `$ref` is.
 */
interface ImportGateway {

    /**
     * Guesses the format of [content].
     *
     * By content, with the file name only as a tiebreak: a `.json` extension says nothing about
     * whether the file is an OpenAPI document, a Postman collection or a HAR capture, and all
     * three routinely arrive named `export.json`.
     */
    fun detectFormat(content: String, fileName: String?): ImportFormat?

    /** Parses [content] as [format]. Failures carry a location, never a stack trace. */
    suspend fun parse(content: String, format: ImportFormat): AppResult<ImportPreview>

    /** Parses this app's own export format. */
    suspend fun parseNative(content: String): AppResult<NativeExport>
}

/**
 * Serialising domain models out to files (SPEC §9.2).
 *
 * All four outputs are formats other tools already read — native JSON for this app, Postman for a
 * team, HAR for Charles or devtools, cURL for a terminal — because the alternative is data that
 * can only ever come back here.
 */
interface ExportGateway {

    /** Full fidelity, versioned with `schemaVersion` so a future import can migrate it. */
    fun toNativeJson(
        collections: List<MockCollection>,
        routes: List<MockRoute>,
        chaosProfiles: List<ChaosProfile>,
    ): String

    /**
     * Postman v2.1. Lossy by nature.
     *
     * Matchers and chaos have no Postman equivalent. [postmanLossWarnings] lists exactly what will
     * not survive, so the warning shown before exporting is generated from the data rather than
     * being a static sentence that goes stale.
     */
    fun toPostmanCollection(collection: MockCollection, routes: List<MockRoute>): String

    /** What [toPostmanCollection] will drop from these routes. Empty when nothing is lost. */
    fun postmanLossWarnings(routes: List<MockRoute>): List<String>

    /** HAR 1.2, for Charles, Fiddler or a browser devtools importer (SPEC §7.4). */
    fun toHar(entries: List<TrafficEntry>, baseUrl: String): String

    /** A full cURL reproduction: method, URL with query, headers, `--data` body (SPEC §7.4). */
    fun toCurl(entry: TrafficEntry, baseUrl: String): String

    /** A formatted request/response transcript, for the share sheet. */
    fun toTranscript(entry: TrafficEntry, baseUrl: String): String
}
