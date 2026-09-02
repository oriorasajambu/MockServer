package com.rio.mockserver.core.importexport.importer

import com.rio.mockserver.core.domain.exception.MockServerException
import com.rio.mockserver.core.domain.model.HttpMethodFilter
import com.rio.mockserver.core.domain.model.ImportFormat
import com.rio.mockserver.core.domain.model.ImportPreview
import com.rio.mockserver.core.domain.model.ImportedRoute
import com.rio.mockserver.core.domain.model.MockResponse
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.domain.model.PathNormaliser
import com.rio.mockserver.core.domain.util.JsonTree
import com.rio.mockserver.core.importexport.dto.PostmanCollectionDto
import com.rio.mockserver.core.importexport.dto.PostmanItemDto
import com.rio.mockserver.core.importexport.dto.PostmanResponseDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a Postman collection v2.1 (SPEC §9.1).
 *
 * Postman describes *requests*, and this app needs *responses*, so the useful payload is the
 * saved examples under each item's `response` array. An item with no saved example still becomes
 * a route — the path and method are worth having — but it is flagged `needsBody`, exactly as an
 * OpenAPI operation with no example is.
 *
 * Folders are flattened. Postman nests items arbitrarily; a collection here is one flat list of
 * routes under a base path, and preserving the tree would mean inventing a grouping concept that
 * has no effect on matching.
 */
@Singleton
class PostmanImporter @Inject constructor() {

    /** Whether [text] looks like a Postman v2.1 collection. */
    fun canParse(text: String): Boolean {
        val root = JsonTree.parseOrNull(text) ?: return false
        val info = (root as? kotlinx.serialization.json.JsonObject)?.get("info")
            as? kotlinx.serialization.json.JsonObject ?: return false
        val schema = (info["schema"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        return schema.contains("collection/v2")
    }

    /** @throws MockServerException.ImportParseError when the document will not deserialise. */
    fun parse(text: String, idFor: (String) -> String): ImportPreview {
        val collection = try {
            JsonTree.Permissive.decodeFromString(PostmanCollectionDto.serializer(), text)
        } catch (e: Exception) {
            throw MockServerException.ImportParseError(null, e.message.orEmpty(), e)
        }

        val warnings = mutableListOf<String>()
        val flattened = collection.item.flatten()

        val routes = flattened.mapNotNull { item ->
            val request = item.request ?: return@mapNotNull null
            val path = request.url?.toPath() ?: return@mapNotNull null
            val example = item.response.pickExample()

            if (example == null) {
                warnings += "'${item.name}' has no saved example response"
            }

            ImportedRoute(
                route = MockRoute(
                    id = idFor("${request.method} $path"),
                    collectionId = "",
                    name = item.name.ifBlank { "${request.method.uppercase()} $path" },
                    method = HttpMethodFilter.fromWire(request.method),
                    pathPattern = path,
                    response = example.toMockResponse(),
                    needsBody = example?.body.isNullOrBlank(),
                ),
                label = "${request.method.uppercase()} $path",
            )
        }

        return ImportPreview(
            format = ImportFormat.POSTMAN,
            suggestedCollectionName = collection.info.name,
            suggestedBasePath = PathNormaliser.ROOT,
            routes = routes,
            warnings = warnings,
        )
    }

    /** Depth-first, so routes come out in the order the collection shows them. */
    private fun List<PostmanItemDto>.flatten(): List<PostmanItemDto> = flatMap { item ->
        if (item.item.isEmpty()) listOf(item) else item.item.flatten()
    }

    /**
     * Picks the example to mock with.
     *
     * The lowest 2xx, for the same reason OpenAPI's lowest 2xx wins: it is the response the
     * endpoint is *for*. Falling back to the first saved example when none is a success, because
     * a collection of error examples is still better than a collection of empty bodies.
     */
    private fun List<PostmanResponseDto>.pickExample(): PostmanResponseDto? =
        filter { it.code in SUCCESS_RANGE }.minByOrNull { it.code } ?: firstOrNull()

    private fun PostmanResponseDto?.toMockResponse(): MockResponse {
        if (this == null) return MockResponse(statusCode = DEFAULT_STATUS, body = "")
        return MockResponse(
            statusCode = code,
            headers = header
                .filterNot { it.disabled }
                .filter { it.key.isNotBlank() }
                .associate { it.key to it.value },
            body = body.orEmpty(),
        )
    }

    /**
     * The path part of a Postman URL.
     *
     * `path` segments when present, otherwise the path component of `raw`. Postman variables
     * (`:id`) are rewritten to this app's `{id}`; `{{baseUrl}}`-style collection variables are
     * dropped, since they resolve to a host this app is standing in for.
     */
    private fun com.rio.mockserver.core.importexport.dto.PostmanUrlDto.toPath(): String? {
        val segments = when {
            path.isNotEmpty() -> path
            raw.isNotBlank() -> PathNormaliser.segments(PathNormaliser.fromUrl(raw))
            else -> return null
        }
        val rewritten = segments
            .filterNot { it.startsWith(VARIABLE_OPEN) }
            .map { segment -> if (segment.startsWith(':')) "{${segment.removePrefix(":")}}" else segment }
        return if (rewritten.isEmpty()) PathNormaliser.ROOT else rewritten.joinToString("/", prefix = "/")
    }

    private companion object {
        const val DEFAULT_STATUS = 200
        const val VARIABLE_OPEN = "{{"
        val SUCCESS_RANGE = 200..299
    }
}
