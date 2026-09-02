package com.minion.mockserver.core.importexport.importer

import com.minion.mockserver.core.common.error.safeCall
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.exception.MockServerException
import com.minion.mockserver.core.domain.model.ImportFormat
import com.minion.mockserver.core.domain.model.ImportPreview
import com.minion.mockserver.core.domain.model.NativeExport
import com.minion.mockserver.core.domain.repository.ImportGateway
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.JsonTree
import com.minion.mockserver.core.importexport.dto.NativeExportDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the format and dispatches to the right importer (SPEC §9.1).
 *
 * Detection is by *content*. All three formats routinely arrive named `export.json`, so an
 * extension says nothing; the file name is consulted only to break a tie in favour of YAML, which
 * has no distinguishing top-level key the way the JSON formats do.
 *
 * Order matters. Native is checked first because it is the only format whose round trip is
 * lossless, and a native file that fell through to another importer would come back missing its
 * matchers and chaos overrides.
 */
@Singleton
class ImportGatewayImpl @Inject constructor(
    private val openApiImporter: OpenApiImporter,
    private val postmanImporter: PostmanImporter,
    private val harImporter: HarImporter,
    private val idGenerator: IdGenerator,
) : ImportGateway {

    override fun detectFormat(content: String, fileName: String?): ImportFormat? {
        if (content.isBlank()) return null
        if (looksNative(content)) return ImportFormat.NATIVE
        if (postmanImporter.canParse(content)) return ImportFormat.POSTMAN
        if (harImporter.canParse(content)) return ImportFormat.HAR

        val document = openApiImporter.parseDocument(content)
        if (document != null && openApiImporter.canParse(document)) return ImportFormat.OPENAPI

        return null
    }

    override suspend fun parse(content: String, format: ImportFormat): AppResult<ImportPreview> =
        safeCall {
            when (format) {
                ImportFormat.OPENAPI -> {
                    val document = openApiImporter.parseDocument(content)
                        ?: throw MockServerException.ImportParseError(null, "not valid JSON or YAML")
                    openApiImporter.parse(document) { idGenerator.newId() }
                }

                ImportFormat.POSTMAN -> postmanImporter.parse(content) { idGenerator.newId() }

                ImportFormat.HAR -> harImporter.parse(content) { idGenerator.newId() }

                // A native file round-trips through parseNative, which preserves ids, matchers and
                // chaos overrides. Reducing it to a preview would lose exactly what makes it the
                // full-fidelity format.
                ImportFormat.NATIVE -> throw MockServerException.UnsupportedImportFormat(
                    "native exports are restored through parseNative, not previewed",
                )
            }
        }

    override suspend fun parseNative(content: String): AppResult<NativeExport> = safeCall {
        val dto = try {
            JsonTree.Permissive.decodeFromString(NativeExportDto.serializer(), content)
        } catch (e: Exception) {
            throw MockServerException.ImportParseError(null, e.message.orEmpty(), e)
        }
        NativeExport(
            schemaVersion = dto.schemaVersion,
            collections = dto.collections,
            routes = dto.routes,
            chaosProfiles = dto.chaosProfiles,
        )
    }

    private fun looksNative(content: String): Boolean {
        val root = JsonTree.parseOrNull(content) as? kotlinx.serialization.json.JsonObject ?: return false
        return root.containsKey(SCHEMA_VERSION) && root.containsKey(COLLECTIONS)
    }

    private companion object {
        const val SCHEMA_VERSION = "schemaVersion"
        const val COLLECTIONS = "collections"
    }
}
