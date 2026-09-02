package com.minion.mockserver.core.importexport.dto

import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.MockRoute
import kotlinx.serialization.Serializable

/**
 * This app's own export format: full fidelity, versioned (SPEC §9.2).
 *
 * The payload is the domain models themselves rather than a mirrored DTO. That is a deliberate
 * trade: a mirror would decouple the file format from the domain, and would also be ten classes
 * nobody remembers to update, so the format would drift silently anyway. Instead the coupling is
 * made loud — [schemaVersion] is the migration hook, and a golden file in this module's tests
 * must keep parsing, so renaming a domain field fails a test rather than quietly breaking every
 * file a user has already exported.
 */
@Serializable
data class NativeExportDto(
    /** Bumped whenever the shape changes, so a future import can migrate rather than refuse. */
    val schemaVersion: Int,

    /** Identifies the writer in a file someone found in their Downloads folder six months later. */
    val exportedBy: String = EXPORTER_NAME,

    /** Epoch millis. */
    val exportedAt: Long = 0L,

    val collections: List<MockCollection> = emptyList(),
    val routes: List<MockRoute> = emptyList(),

    /** Only the profiles the exported routes actually reference. */
    val chaosProfiles: List<ChaosProfile> = emptyList(),
) {
    companion object {
        const val EXPORTER_NAME = "MockServer for Android"
    }
}
