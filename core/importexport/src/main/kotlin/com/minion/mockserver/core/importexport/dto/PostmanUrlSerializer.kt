package com.minion.mockserver.core.importexport.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Accepts both shapes Postman writes a URL in.
 *
 * v2.1 allows `"url": "https://api.example.com/users"` and
 * `"url": { "raw": "...", "host": [...], "path": [...] }`, and real exports contain both — often
 * in the same file, because the string form is what you get from an older Postman and the object
 * form from a newer one. Coercing the string into `{ "raw": ... }` here means the importer only
 * ever sees one shape.
 */
object PostmanUrlSerializer : JsonTransformingSerializer<PostmanUrlDto>(PostmanUrlDto.serializer()) {

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) {
            buildJsonObject { put("raw", element.content) }
        } else {
            element
        }
}
