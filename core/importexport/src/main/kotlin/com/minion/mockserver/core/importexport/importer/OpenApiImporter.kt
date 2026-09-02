package com.minion.mockserver.core.importexport.importer

import com.minion.mockserver.core.domain.exception.MockServerException
import com.minion.mockserver.core.domain.model.AlternateResponse
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.ImportFormat
import com.minion.mockserver.core.domain.model.ImportPreview
import com.minion.mockserver.core.domain.model.ImportedRoute
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.PathNormaliser
import com.minion.mockserver.core.domain.util.JsonTree
import com.minion.mockserver.core.importexport.mapper.YamlToJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads OpenAPI 3.0 and 3.1, JSON or YAML (SPEC §9.1).
 *
 * A hand-rolled traversal over the JSON tree, reading only `info.title`, `servers[0].url`, and the
 * operations under `paths`. `swagger-parser` would give validation and `$ref` resolution this app
 * does not need, at the cost of a large Java dependency with reflection and file-system
 * assumptions that do not belong in an APK.
 *
 * ### Bodies come from examples, never from schemas
 *
 * This is the load-bearing decision. A body synthesised from a schema is full of `"string"` and
 * `0`: it looks real, it parses, and it tests nothing — so the first time a client renders it,
 * the mock has actively misled someone. Instead, a route with no example is created with an empty
 * body and flagged [MockRoute.needsBody], which the route list shows as an amber chip and the
 * collection as an "N routes need bodies" banner. The gap stays visible until a human fills it.
 *
 * Example lookup, in order: the media type's `example`, then the first entry under `examples`,
 * then a `schema.example` if the spec author put one there.
 */
@Singleton
class OpenApiImporter @Inject constructor() {

    /** Whether [document] looks like an OpenAPI 3.x document. */
    fun canParse(document: JsonElement): Boolean {
        val root = document as? JsonObject ?: return false
        val version = root[OPENAPI]?.jsonPrimitiveOrNull()?.content ?: return false
        return version.startsWith("3.")
    }

    /** Parses [text] as JSON or YAML. */
    fun parseDocument(text: String): JsonElement? = JsonTree.parseOrNull(text) ?: YamlToJson.parseOrNull(text)

    /**
     * Builds a preview from an already-parsed document.
     *
     * @throws MockServerException.ImportParseError with a JSON pointer at the offending place —
     *   SPEC §9.1 requires a parse failure to point into the user's file, not at a stack trace.
     */
    fun parse(document: JsonElement, idFor: (String) -> String): ImportPreview {
        val root = document as? JsonObject
            ?: throw MockServerException.ImportParseError(null, "the document root is not an object")

        val title = root[INFO]?.jsonObject?.get(TITLE)?.jsonPrimitiveOrNull()?.content
            ?: DEFAULT_TITLE
        val basePath = root.serverBasePath()
        val warnings = mutableListOf<String>()

        val paths = root[PATHS]?.jsonObject
            ?: throw MockServerException.ImportParseError("/paths", "no paths object")

        val routes = paths.entries.flatMap { (rawPath, pathItem) ->
            val operations = pathItem as? JsonObject ?: return@flatMap emptyList()
            operations.entries
                .filter { (method, _) -> method.uppercase() in SUPPORTED_METHODS }
                .mapNotNull { (method, operation) ->
                    toRoute(
                        rawPath = rawPath,
                        method = method,
                        operation = operation as? JsonObject ?: return@mapNotNull null,
                        idFor = idFor,
                        warnings = warnings,
                    )
                }
        }

        return ImportPreview(
            format = ImportFormat.OPENAPI,
            suggestedCollectionName = title,
            suggestedBasePath = basePath,
            routes = routes,
            warnings = warnings,
        )
    }

    private fun toRoute(
        rawPath: String,
        method: String,
        operation: JsonObject,
        idFor: (String) -> String,
        warnings: MutableList<String>,
    ): ImportedRoute? {
        val pointer = "/paths/${rawPath.escapePointer()}/${method.lowercase()}"
        // OpenAPI's `{id}` is this app's `{id}` — the one place the two grammars already agree.
        val pathPattern = PathNormaliser.normalise(rawPath)

        val responses = operation[RESPONSES]?.jsonObject
        if (responses == null) {
            warnings += "$pointer has no responses and was skipped"
            return null
        }

        val statuses = responses.keys.mapNotNull { it.toIntOrNull() }.sorted()
        // The lowest 2xx becomes the route's response; the rest are offered as one-tap alternates
        // in the editor rather than separate routes, which would all match identically and shadow
        // one another (SPEC §17.3).
        val primaryStatus = statuses.firstOrNull { it in SUCCESS_RANGE } ?: statuses.firstOrNull() ?: DEFAULT_STATUS

        val primary = buildResponse(responses, primaryStatus)
        val alternates = statuses
            .filter { it != primaryStatus }
            .map { status ->
                AlternateResponse(
                    statusCode = status,
                    description = responses[status.toString()]
                        ?.jsonObject?.get(DESCRIPTION)?.jsonPrimitiveOrNull()?.content
                        .orEmpty(),
                    response = buildResponse(responses, status),
                )
            }

        val summary = operation[SUMMARY]?.jsonPrimitiveOrNull()?.content
            ?: operation[OPERATION_ID]?.jsonPrimitiveOrNull()?.content
            ?: "${method.uppercase()} $pathPattern"

        return ImportedRoute(
            route = MockRoute(
                id = idFor("$method $pathPattern"),
                collectionId = "",
                name = summary,
                method = HttpMethodFilter.fromWire(method),
                pathPattern = pathPattern,
                response = primary,
                needsBody = primary.body.isBlank(),
            ),
            label = "${method.uppercase()} $pathPattern",
            alternateResponses = alternates,
        )
    }

    private fun buildResponse(responses: JsonObject, status: Int): MockResponse {
        val response = responses[status.toString()]?.jsonObject
        val content = response?.get(CONTENT)?.jsonObject

        // Prefer a JSON media type; fall back to whatever the spec declared first, since a mock
        // that answers text/csv is still more useful than one that answers nothing.
        val mediaTypeEntry = content?.entries?.firstOrNull { it.key.contains(JSON_HINT) }
            ?: content?.entries?.firstOrNull()

        val body = mediaTypeEntry?.value?.jsonObject?.let(::extractExample).orEmpty()

        return MockResponse(
            statusCode = status,
            headers = mediaTypeEntry?.key?.let { mapOf(MockResponse.CONTENT_TYPE to it) }.orEmpty(),
            body = body,
        )
    }

    /**
     * Pulls the example out of a media-type object.
     *
     * `example` first, then the first entry under `examples` (whose values wrap the payload in a
     * `value` key), then `schema.example`. A schema *without* an example is deliberately not
     * synthesised into anything.
     */
    private fun extractExample(mediaType: JsonObject): String {
        mediaType[EXAMPLE]?.let { return it.renderBody() }

        mediaType[EXAMPLES]?.jsonObject?.values?.firstOrNull()?.let { first ->
            val wrapped = first.jsonObject[VALUE] ?: first
            return wrapped.renderBody()
        }

        mediaType[SCHEMA]?.jsonObject?.get(EXAMPLE)?.let { return it.renderBody() }

        return ""
    }

    /** A string example is used verbatim; anything else is re-serialised as pretty JSON. */
    private fun JsonElement.renderBody(): String {
        val primitive = this as? JsonPrimitive
        return if (primitive != null && primitive.isString) {
            primitive.content
        } else {
            JsonTree.Pretty.encodeToString(JsonElement.serializer(), this)
        }
    }

    /**
     * The path prefix from `servers[0].url`.
     *
     * Only the path component: the host and scheme name a server this app is standing in for, and
     * carrying them into the base path would produce routes nothing can address.
     */
    private fun JsonObject.serverBasePath(): String {
        val url = this[SERVERS]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get(URL)?.jsonPrimitiveOrNull()?.content
            ?: return PathNormaliser.ROOT
        return PathNormaliser.fromUrl(url)
    }

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    /** RFC 6901: `~` becomes `~0` and `/` becomes `~1`, so the pointer addresses one token. */
    private fun String.escapePointer(): String = replace("~", "~0").replace("/", "~1")

    private companion object {
        const val OPENAPI = "openapi"
        const val INFO = "info"
        const val TITLE = "title"
        const val SERVERS = "servers"
        const val URL = "url"
        const val PATHS = "paths"
        const val RESPONSES = "responses"
        const val CONTENT = "content"
        const val EXAMPLE = "example"
        const val EXAMPLES = "examples"
        const val VALUE = "value"
        const val SCHEMA = "schema"
        const val SUMMARY = "summary"
        const val OPERATION_ID = "operationId"
        const val DESCRIPTION = "description"
        const val JSON_HINT = "json"
        const val DEFAULT_TITLE = "Imported API"
        const val DEFAULT_STATUS = 200
        val SUCCESS_RANGE = 200..299
        val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    }
}
