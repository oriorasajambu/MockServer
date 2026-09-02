package com.minion.mockserver.core.importexport.importer

import com.minion.mockserver.core.domain.exception.MockServerException
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.ImportFormat
import com.minion.mockserver.core.domain.model.ImportPreview
import com.minion.mockserver.core.domain.model.ImportedRoute
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.PathNormaliser
import com.minion.mockserver.core.domain.util.JsonTree
import com.minion.mockserver.core.importexport.dto.HarEntryDto
import com.minion.mockserver.core.importexport.dto.HarFileDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a HAR 1.2 capture (SPEC §9.1).
 *
 * The most directly useful of the three formats: a HAR entry is a real request paired with the
 * real response it got, so the routes it produces are mocks of something that actually happened
 * rather than of something a spec says should happen.
 *
 * Two reductions are applied, both because a capture is a log and a rule set is not:
 * - Entries are de-duplicated on method + path, keeping the first. A page load produces the same
 *   `GET /api/config` eleven times, and eleven identical routes would shadow each other and fill
 *   the list with rules that can never win.
 * - Only text responses become bodies. An image or a font in the capture becomes a route with an
 *   empty body flagged `needsBody`, because v1 cannot serve binary (SPEC §1.2).
 */
@Singleton
class HarImporter @Inject constructor() {

    /** Whether [text] looks like a HAR file. */
    fun canParse(text: String): Boolean {
        val root = JsonTree.parseOrNull(text) as? kotlinx.serialization.json.JsonObject ?: return false
        val log = root["log"] as? kotlinx.serialization.json.JsonObject ?: return false
        return log.containsKey("entries")
    }

    /** @throws MockServerException.ImportParseError when the document will not deserialise. */
    fun parse(text: String, idFor: (String) -> String): ImportPreview {
        val har = try {
            JsonTree.Permissive.decodeFromString(HarFileDto.serializer(), text)
        } catch (e: Exception) {
            throw MockServerException.ImportParseError(null, e.message.orEmpty(), e)
        }

        val warnings = mutableListOf<String>()
        val seen = LinkedHashSet<String>()
        val routes = mutableListOf<ImportedRoute>()
        var duplicates = 0

        har.log.entries.forEach { entry ->
            val path = PathNormaliser.fromUrl(entry.request.url)
            val key = "${entry.request.method.uppercase()} $path"
            if (!seen.add(key)) {
                duplicates++
                return@forEach
            }

            val body = entry.response.content.text.orEmpty()
            val isBinary = body.isBlank() && entry.response.content.size > 0

            if (isBinary) {
                warnings += "$key has a binary body, which v1 cannot serve"
            }

            routes += ImportedRoute(
                route = MockRoute(
                    id = idFor(key),
                    collectionId = "",
                    name = key,
                    method = HttpMethodFilter.fromWire(entry.request.method),
                    pathPattern = path,
                    response = MockResponse(
                        statusCode = entry.response.status,
                        headers = entry.responseHeaders(),
                        body = if (isBinary) "" else body,
                    ),
                    needsBody = isBinary || body.isBlank(),
                ),
                label = key,
            )
        }

        if (duplicates > 0) {
            warnings += "$duplicates repeated request(s) were collapsed into the first occurrence"
        }

        return ImportPreview(
            format = ImportFormat.HAR,
            suggestedCollectionName = har.log.entries.firstOrNull()?.hostName() ?: DEFAULT_NAME,
            suggestedBasePath = PathNormaliser.ROOT,
            routes = routes,
            warnings = warnings,
        )
    }

    /**
     * The response headers worth keeping.
     *
     * Hop-by-hop and transfer headers are dropped: replaying a captured `Content-Encoding: gzip`
     * against a body this app will send as plain text produces a response no client can read.
     */
    private fun HarEntryDto.responseHeaders(): Map<String, String> = response.headers
        .filter { it.name.isNotBlank() && it.name.lowercase() !in DROPPED_HEADERS }
        .associate { it.name to it.value }

    private fun HarEntryDto.hostName(): String? = request.url
        .substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .takeIf { it.isNotBlank() }

    private companion object {
        const val DEFAULT_NAME = "Captured session"
        val DROPPED_HEADERS = setOf(
            "content-encoding",
            "content-length",
            "transfer-encoding",
            "connection",
            "keep-alive",
            "date",
            "server",
        )
    }
}
